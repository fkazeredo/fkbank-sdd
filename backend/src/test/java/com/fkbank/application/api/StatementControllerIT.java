package com.fkbank.application.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fkbank.domain.account.CurrentAccount;
import com.fkbank.domain.customer.CustomerId;
import com.fkbank.domain.ledger.AccountId;
import com.fkbank.domain.ledger.Ledger;
import com.fkbank.domain.ledger.Money;
import com.fkbank.testsupport.ControllableBureau;
import com.fkbank.testsupport.OnboardingFixture;
import com.fkbank.testsupport.PkceTokenFlow;
import com.fkbank.testsupport.PostgresContainer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@link StatementController}, end to end, against the real configuration.
 *
 * <p>Deliberately not a mocked security slice, matching {@code MeEndpointSecurityIT}: boots the
 * whole application on a real port against real PostgreSQL and drives a genuine PKCE exchange for
 * the bearer token, so a mistake in the filter chain or the caller resolution shows up here rather
 * than being hidden by a stubbed principal.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@Import(ControllableBureau.Configuration.class)
@DisplayName("GET /api/account/statement")
class StatementControllerIT {

  @LocalServerPort private int port;

  @Autowired private OnboardingFixture onboarding;
  @Autowired private Ledger ledger;

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    PostgresContainer.registerDataSource(registry);
  }

  @Test
  @DisplayName("answers 401 without a token")
  void unauthenticatedIsRejected() throws Exception {
    HttpResponse<String> response = get("/api/account/statement", null);

    assertThat(response.statusCode()).isEqualTo(401);
  }

  @Test
  @DisplayName("shows a deposit as a line with its running balance, newest first")
  void showsADepositLine() throws Exception {
    OnboardingFixture.SignedUpCustomer customer = onboarding.approvedCustomer();
    String token = tokenFor(customer);
    fund(customer.customerId(), "42.50");

    HttpResponse<String> response = get("/api/account/statement", token);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
        .contains("\"amount\":\"42.50\"")
        .contains("\"currency\":\"BRL\"")
        .contains("\"direction\":\"IN\"")
        .contains("\"runningBalance\":\"42.50\"");
  }

  @Test
  @DisplayName("answers 422 for a page size outside the allowed range")
  void refusesAnOutOfRangePageSize() throws Exception {
    OnboardingFixture.SignedUpCustomer customer = onboarding.approvedCustomer();
    String token = tokenFor(customer);

    HttpResponse<String> response = get("/api/account/statement?size=0", token);

    assertThat(response.statusCode()).isEqualTo(422);
  }

  @Test
  @DisplayName(
      "answers 422 with a domain-language message for size/from/to, never a framework type name (QA-0003-01)")
  void refusesUnparsableFiltersWithoutLeakingFrameworkTypes() throws Exception {
    OnboardingFixture.SignedUpCustomer customer = onboarding.approvedCustomer();
    String token = tokenFor(customer);

    HttpResponse<String> badSize = get("/api/account/statement?size=notanumber", token);
    assertThat(badSize.statusCode()).isEqualTo(422);
    assertThat(badSize.body())
        .contains("size must be a whole number, was notanumber")
        .doesNotContain("java.lang")
        .doesNotContain("MethodArgumentTypeMismatch")
        .doesNotContain("RequestParam");

    HttpResponse<String> badFrom = get("/api/account/statement?from=2026-07-01&to=2026-07-02", token);
    assertThat(badFrom.statusCode()).isEqualTo(422);
    assertThat(badFrom.body())
        .contains("from must be a full ISO-8601 instant, was 2026-07-01")
        .doesNotContain("java.time")
        .doesNotContain("MethodArgumentTypeMismatch")
        .doesNotContain("RequestParam");
  }

  @Test
  @DisplayName("fetches a receipt by its posting id, showing rail, status and amount")
  void fetchesAReceipt() throws Exception {
    OnboardingFixture.SignedUpCustomer customer = onboarding.approvedCustomer();
    String token = tokenFor(customer);
    UUID postingId = fund(customer.customerId(), "15.00");

    HttpResponse<String> response = get("/api/account/statement/receipts/" + postingId, token);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
        .contains("\"amount\":\"15.00\"")
        .contains("\"direction\":\"IN\"")
        .contains("\"rail\":\"BOLETO\"")
        .contains("\"status\":\"COMPLETED\"");
  }

  @Test
  @DisplayName("answers 404 for a receipt the caller was not a party to")
  void refusesSomeoneElsesReceipt() throws Exception {
    OnboardingFixture.SignedUpCustomer owner = onboarding.approvedCustomer();
    UUID postingId = fund(owner.customerId(), "5.00");

    OnboardingFixture.SignedUpCustomer stranger = onboarding.approvedCustomer();
    String strangerToken = tokenFor(stranger);

    HttpResponse<String> response =
        get("/api/account/statement/receipts/" + postingId, strangerToken);

    assertThat(response.statusCode()).isEqualTo(404);
  }

  @Test
  @DisplayName("answers 404 for a receipt id that is not even a well-formed UUID, same as an unknown one")
  void refusesAMalformedReceiptId() throws Exception {
    OnboardingFixture.SignedUpCustomer customer = onboarding.approvedCustomer();
    String token = tokenFor(customer);

    HttpResponse<String> response = get("/api/account/statement/receipts/not-a-uuid", token);

    assertThat(response.statusCode()).isEqualTo(404);
  }

  private String tokenFor(OnboardingFixture.SignedUpCustomer customer) throws Exception {
    return new PkceTokenFlow(port).obtainAccessToken(customer.username(), customer.password());
  }

  /** Funds the customer's account from the already-seeded boleto settlement account. */
  private UUID fund(CustomerId customerId, String amount) {
    AccountId settlement = ledger.accountIdOf("internal:settlement:boleto");
    AccountId customerAccount = ledger.accountIdOf(CurrentAccount.ledgerAccountCodeFor(customerId));
    return ledger.record(settlement, customerAccount, Money.of(amount)).id().value();
  }

  private HttpResponse<String> get(String path, String bearerToken) throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(15))
            .GET();
    if (bearerToken != null) {
      builder.header("Authorization", "Bearer " + bearerToken);
    }
    return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }
}
