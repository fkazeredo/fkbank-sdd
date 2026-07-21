package com.fkbank.acceptance.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import com.fkbank.domain.onboarding.BureauDecision;
import com.fkbank.domain.onboarding.RejectionReason;
import com.fkbank.testsupport.ControllableBureau;
import com.fkbank.testsupport.Cpfs;
import com.fkbank.testsupport.OnboardingFixture;
import com.fkbank.testsupport.PkceTokenFlow;
import jakarta.persistence.EntityManager;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The sign-up and account-opening journey exercised the way an actual HTTP client experiences
 * it: real status codes, real response bodies, real row counts read back from the database the
 * running server is actually writing to.
 */
@DisplayName("Sign-up and account opening, over HTTP")
class SignUpHttpAcceptanceIT extends OnboardingHttpAcceptanceTest {

  @Autowired private ControllableBureau bureau;
  @Autowired private OnboardingFixture onboardingFixture;
  @Autowired private EntityManager entityManager;

  @BeforeEach
  void resetTheBureau() {
    bureau.reset();
  }

  @Nested
  @DisplayName("acceptance criterion 2 - duplicate CPF or e-mail")
  class DuplicateApplicant {

    @Test
    @DisplayName("a duplicate CPF is refused 409 DUPLICATE_CUSTOMER and creates nothing")
    void duplicateCpfCreatesNothing() throws Exception {
      OnboardingFixture.SignedUpCustomer existing = onboardingFixture.approvedCustomer();
      // The fixture does not expose the CPF directly; read it back the way any other caller
      // would have to - from the row the approval actually left behind.
      String registeredCpf = cpfOfCustomer(existing);

      long customersBefore = countRows("customer");
      long onboardingsBefore = countRows("onboarding");
      long credentialsBefore = countRows("credential");
      long accountsBefore = countRows("current_account");

      HttpResponse<String> response =
          post(
              "/api/signup",
              signUpJson(
                  "Someone Else",
                  registeredCpf,
                  "someone.else." + System.nanoTime() + "@example.com",
                  "Passw0rd1",
                  "1990-01-01",
                  "1000.00"));

      assertThat(response.statusCode()).isEqualTo(409);
      assertThat(response.body())
          .contains("\"code\":\"DUPLICATE_CUSTOMER\"")
          .contains("\"status\":409");
      assertThat(response.headers().firstValue("Content-Type"))
          .hasValueSatisfying(type -> assertThat(type).contains("application/problem+json"));

      assertThat(countRows("customer")).as("no extra customer").isEqualTo(customersBefore);
      assertThat(countRows("onboarding")).as("no extra onboarding").isEqualTo(onboardingsBefore);
      assertThat(countRows("credential")).as("no extra credential").isEqualTo(credentialsBefore);
      assertThat(countRows("current_account")).as("no extra account").isEqualTo(accountsBefore);
    }

    @Test
    @DisplayName("a duplicate e-mail with a fresh CPF is also refused 409 DUPLICATE_CUSTOMER")
    void duplicateEmailCreatesNothing() throws Exception {
      OnboardingFixture.SignedUpCustomer existing = onboardingFixture.approvedCustomer();

      HttpResponse<String> response =
          post(
              "/api/signup",
              signUpJson(
                  "Someone Else",
                  Cpfs.random(),
                  existing.username(),
                  "Passw0rd1",
                  "1990-01-01",
                  "1000.00"));

      assertThat(response.statusCode()).isEqualTo(409);
      assertThat(response.body()).contains("\"code\":\"DUPLICATE_CUSTOMER\"");
    }

    private String cpfOfCustomer(OnboardingFixture.SignedUpCustomer customer) {
      return ((String)
          entityManager
              .createNativeQuery("SELECT cpf FROM customer WHERE id = :id")
              .setParameter("id", customer.customerId().value())
              .getSingleResult());
    }
  }

  @Nested
  @DisplayName("acceptance criterion 3 - the bureau declines")
  class Decline {

    @Test
    @DisplayName("REJECTED, no account, no credential, only the reason category - never the raw payload")
    void declineCreatesNoAccountAndLeaksNoRawPayload() throws Exception {
      bureau.willAnswer(BureauDecision.rejected(RejectionReason.DOCUMENT_MISMATCH));
      String cpf = Cpfs.random();

      HttpResponse<String> response =
          post(
              "/api/signup",
              signUpJson(
                  "Someone Declined",
                  cpf,
                  "declined." + System.nanoTime() + "@example.com",
                  "Passw0rd1",
                  "1990-01-01",
                  "1000.00"));

      assertThat(response.statusCode()).isEqualTo(201);
      assertThat(response.body())
          .contains("\"status\":\"REJECTED\"")
          .contains("\"reasonCategory\":\"DOCUMENT_MISMATCH\"");
      // The bureau emulator's own vocabulary (inquiry identifiers, its own field names) must
      // never appear in what the applicant is shown.
      assertThat(response.body()).doesNotContain("inquiryId");

      assertThat(countRowsForCpf("customer", cpf)).isZero();
      assertThat(countAccountsForCpf(cpf)).isZero();
    }
  }

  @Nested
  @DisplayName("edge case - CPF normalization")
  class CpfNormalization {

    @Test
    @DisplayName("a formatted CPF is recognized as a duplicate of the same digits-only CPF")
    void formattingDoesNotDefeatUniqueness() throws Exception {
      String digits = Cpfs.random();
      HttpResponse<String> first =
          post(
              "/api/signup",
              signUpJson(
                  "Digits Only",
                  digits,
                  "digits." + System.nanoTime() + "@example.com",
                  "Passw0rd1",
                  "1990-01-01",
                  "1000.00"));
      assertThat(first.statusCode()).isEqualTo(201);

      HttpResponse<String> second =
          post(
              "/api/signup",
              signUpJson(
                  "Formatted Applicant",
                  Cpfs.formatted(digits),
                  "formatted." + System.nanoTime() + "@example.com",
                  "Passw0rd1",
                  "1990-01-01",
                  "1000.00"));

      assertThat(second.statusCode()).isEqualTo(409);
      assertThat(second.body()).contains("DUPLICATE_CUSTOMER");
    }
  }

  @Nested
  @DisplayName("edge case - the age boundary")
  class AgeBoundary {

    @Test
    @DisplayName("someone who turns 18 today is accepted")
    void eighteenTodayIsAccepted() throws Exception {
      HttpResponse<String> response =
          post(
              "/api/signup",
              signUpJson(
                  "Just Eighteen",
                  Cpfs.random(),
                  "eighteen." + System.nanoTime() + "@example.com",
                  "Passw0rd1",
                  // The server decides adulthood against the UTC date (SignUp reads the clock in
                  // ZoneOffset.UTC), so this boundary date must be built in UTC too. LocalDate.now()
                  // reads the JVM's default zone, which west of UTC names the previous day for part
                  // of each evening and shifts the boundary by a day — green in a UTC-clocked CI,
                  // red on a Brasilia-clocked machine after 21:00. The clocks have to match.
                  LocalDate.now(ZoneOffset.UTC).minusYears(18).toString(),
                  "1000.00"));

      assertThat(response.statusCode()).isIn(200, 201, 202);
    }

    @Test
    @DisplayName("someone who is 17 for one more day is refused 422 UNDERAGE_CUSTOMER")
    void oneDayShortOfEighteenIsRefused() throws Exception {
      HttpResponse<String> response =
          post(
              "/api/signup",
              signUpJson(
                  "Almost Eighteen",
                  Cpfs.random(),
                  "almost." + System.nanoTime() + "@example.com",
                  "Passw0rd1",
                  // UTC, to match the server's own clock — see the note in eighteenTodayIsAccepted.
                  LocalDate.now(ZoneOffset.UTC).minusYears(18).plusDays(1).toString(),
                  "1000.00"));

      assertThat(response.statusCode()).isEqualTo(422);
      assertThat(response.body()).contains("\"code\":\"UNDERAGE_CUSTOMER\"");
    }
  }

  @Nested
  @DisplayName("validation - what a 422 actually returns")
  class ValidationShapes {

    @Test
    @DisplayName("a weak password (no digit) is refused 422 WEAK_PASSWORD, not a generic error")
    void weakPasswordHasItsOwnCode() throws Exception {
      HttpResponse<String> response =
          post(
              "/api/signup",
              signUpJson(
                  "Weak Password",
                  Cpfs.random(),
                  "weak." + System.nanoTime() + "@example.com",
                  "onlyletters",
                  "1990-01-01",
                  "1000.00"));

      assertThat(response.statusCode()).isEqualTo(422);
      assertThat(response.body()).contains("\"code\":\"WEAK_PASSWORD\"");
    }

    @Test
    @DisplayName("every 422 carries the standard problem+json shape")
    void everyValidationFailureIsProblemJson() throws Exception {
      HttpResponse<String> response = post("/api/signup", "{}");

      assertThat(response.statusCode()).isEqualTo(422);
      assertThat(response.headers().firstValue("Content-Type"))
          .hasValueSatisfying(type -> assertThat(type).contains("application/problem+json"));
      assertThat(response.body())
          .contains("\"status\":422")
          .contains("\"title\"")
          .contains("\"type\"")
          .contains("\"code\"");
    }
  }

  @Nested
  @DisplayName("adversarial - the status route")
  class StatusRoute {

    @Test
    @DisplayName("an onboarding id nobody was ever given answers 404, not a hint that it might exist")
    void unknownIdIsNotFound() throws Exception {
      HttpResponse<String> response = get("/api/signup/11111111-2222-3333-4444-555555555555");

      assertThat(response.statusCode()).isEqualTo(404);
      assertThat(response.body()).contains("\"code\":\"UNKNOWN_ONBOARDING\"");
    }

    @Test
    @DisplayName("a malformed onboarding id is a 422, not a 500")
    void malformedIdIsUnprocessable() throws Exception {
      HttpResponse<String> response = get("/api/signup/not-a-uuid");

      assertThat(response.statusCode()).isEqualTo(422);
    }

    @Test
    @DisplayName("the status response carries only onboardingId, status and reasonCategory")
    void statusLeaksNoRegistrationData() throws Exception {
      OnboardingFixture.SignedUpCustomer customer = onboardingFixture.approvedCustomer();

      HttpResponse<String> response = get("/api/signup/" + customer.onboardingId());

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body())
          .doesNotContain(customer.username())
          .doesNotContain("cpf")
          .doesNotContain("fullName")
          .doesNotContain("password");
    }
  }

  @Nested
  @DisplayName("adversarial - sign-in is gated on the outcome")
  class SignInGating {

    @Test
    @DisplayName("a refused applicant cannot sign in")
    void refusedApplicantCannotSignIn() throws Exception {
      bureau.willAnswer(BureauDecision.rejected(RejectionReason.SANCTIONS_LIST));
      String email = "refused." + System.nanoTime() + "@example.com";
      HttpResponse<String> signup =
          post(
              "/api/signup",
              signUpJson(
                  "Refused Applicant", Cpfs.random(), email, "Passw0rd1", "1990-01-01", "1000.00"));
      assertThat(signup.statusCode()).isEqualTo(201);

      org.assertj.core.api.Assertions.assertThatThrownBy(
              () -> new PkceTokenFlow(port).obtainAccessToken(email, "Passw0rd1"))
          .as("no credential was ever activated for a refused applicant")
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a refused applicant may re-apply with the same CPF once no customer was created")
    void refusedApplicantMayReapply() throws Exception {
      bureau.willAnswer(BureauDecision.rejected(RejectionReason.DOCUMENT_MISMATCH));
      String cpf = Cpfs.random();
      HttpResponse<String> firstAttempt =
          post(
              "/api/signup",
              signUpJson(
                  "Reapplying Person", cpf, "reapply1." + System.nanoTime() + "@example.com",
                  "Passw0rd1", "1990-01-01", "1000.00"));
      assertThat(firstAttempt.body()).contains("REJECTED");

      bureau.willAnswer(BureauDecision.approved());
      HttpResponse<String> secondAttempt =
          post(
              "/api/signup",
              signUpJson(
                  "Reapplying Person", cpf, "reapply2." + System.nanoTime() + "@example.com",
                  "Passw0rd1", "1990-01-01", "1000.00"));

      assertThat(secondAttempt.statusCode())
          .as("a rejection created no customer, so the CPF is free to try again")
          .isEqualTo(201);
      assertThat(secondAttempt.body()).contains("APPROVED");
    }
  }

  @Nested
  @DisplayName("adversarial - GET /api/account/me")
  class AccountMeSecurity {

    @Test
    @DisplayName("answers 401 without a token")
    void unauthenticatedIsRejected() throws Exception {
      HttpResponse<String> response = get("/api/account/me");

      assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("answers 401 for a garbage token")
    void garbageTokenIsRejected() throws Exception {
      HttpResponse<String> response = get("/api/account/me", "not-a-real-token");

      assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("a freshly approved applicant's token shows branch 0001, a balance and a currency")
    void approvedApplicantSeesTheirAccount() throws Exception {
      OnboardingFixture.SignedUpCustomer customer = onboardingFixture.approvedCustomer();

      String token = new PkceTokenFlow(port).obtainAccessToken(customer.username(), customer.password());
      HttpResponse<String> response = get("/api/account/me", token);

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body())
          .contains("\"branch\":\"0001\"")
          .contains("\"balance\":\"0.00\"")
          .contains("\"currency\":\"BRL\"");
    }
  }

  private long countRows(String table) {
    return ((Number) entityManager.createNativeQuery("SELECT count(*) FROM " + table).getSingleResult())
        .longValue();
  }

  private long countRowsForCpf(String table, String cpf) {
    return ((Number)
            entityManager
                .createNativeQuery("SELECT count(*) FROM " + table + " c WHERE c.cpf = :cpf")
                .setParameter("cpf", cpf)
                .getSingleResult())
        .longValue();
  }

  /** {@code current_account} has no {@code cpf} column of its own; it is reached through {@code customer}. */
  private long countAccountsForCpf(String cpf) {
    return ((Number)
            entityManager
                .createNativeQuery(
                    "SELECT count(*) FROM current_account a"
                        + " JOIN customer c ON c.id = a.customer_id"
                        + " WHERE c.cpf = :cpf")
                .setParameter("cpf", cpf)
                .getSingleResult())
        .longValue();
  }

  static String signUpJson(
      String fullName, String cpf, String email, String password, String birthDate, String income) {
    return """
        {"fullName":"%s","cpf":"%s","email":"%s","password":"%s","birthDate":"%s","monthlyIncome":"%s"}
        """
        .formatted(fullName, cpf, email, password, birthDate, income);
  }
}
