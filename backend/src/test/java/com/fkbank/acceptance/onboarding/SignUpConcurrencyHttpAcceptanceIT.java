package com.fkbank.acceptance.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import com.fkbank.domain.customer.Cpf;
import com.fkbank.domain.onboarding.BureauDecision;
import com.fkbank.domain.onboarding.Onboarding;
import com.fkbank.domain.onboarding.OnboardingOutcome;
import com.fkbank.domain.onboarding.OnboardingRepository;
import com.fkbank.testsupport.ControllableBureau;
import com.fkbank.testsupport.Cpfs;
import com.fkbank.testsupport.OnboardingFixture;
import jakarta.persistence.EntityManager;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Acceptance criterion 4, exercised through the real HTTP edge rather than only at the domain
 * level: several genuinely concurrent {@code POST /api/signup} requests for one CPF, released at
 * the same instant by a barrier rather than left to happen to overlap.
 */
@DisplayName("Two or more sign-ups for the same CPF at once, over HTTP")
class SignUpConcurrencyHttpAcceptanceIT extends OnboardingHttpAcceptanceTest {

  // Kept comfortably under PostgresContainer's per-context pool size (5): a higher count here
  // produces HTTP timeouts from this test's own connection-pool contention, not from anything
  // the sign-up route does under real concurrency (the black-box pass exercised 5- and 8-way
  // races against the E2E stack's own, differently sized pool without this artifact).
  private static final int ATTEMPTS = 4;

  @Autowired private ControllableBureau bureau;
  @Autowired private EntityManager entityManager;
  @Autowired private OnboardingRepository onboardings;
  @Autowired private OnboardingOutcome outcome;
  @Autowired private OnboardingFixture onboardingFixture;

  @BeforeEach
  void resetTheBureau() {
    bureau.reset();
  }

  @Nested
  @DisplayName("acceptance criterion 4 - money and rows")
  class RowSafety {

    @Test
    @DisplayName("exactly one customer and one account persist, however many requests raced")
    void exactlyOneOfEachSurvives() throws Exception {
      String cpf = Cpfs.random();
      bureau.willAnswer(BureauDecision.approved());

      List<HttpResponse<String>> responses = submitConcurrently(cpf);

      assertThat(responses).hasSize(ATTEMPTS);
      assertThat(countRowsForCpf("customer", cpf)).isEqualTo(1);
      assertThat(countAccountsFor(cpf)).isEqualTo(1);
      assertThat(countRowsForCpf("onboarding", cpf)).isEqualTo(1);

      long winners = responses.stream().filter(r -> r.statusCode() == 201).count();
      assertThat(winners).as("exactly one submission actually started the application").isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("QA2-02 - the fallback a concurrent loser relies on")
  class LoserFallback {

    /**
     * QA2-02 was that {@code SignUp.submit()}'s race-loss recovery looked only for a still-{@code
     * PENDING} application ({@code findPendingByCpf}), so once the winner had already been
     * decided - which the synchronous {@code approve}/{@code decline} bureau scenarios can
     * complete before a genuinely raced loser gets to retry - the lookup found nothing and a
     * resubmission surfaced as a raw {@code 409 ONBOARDING_ALREADY_PENDING} instead of the
     * graceful answer BR-5 promises. The fix widened the recovery lookup to {@code
     * findLatestByCpf} (any status) and re-runs the duplicate check when the winner turns out to
     * be settled, so a late arrival for a CPF that already belongs to a customer is now told the
     * same thing a plain resubmission would be told: {@code 409 DUPLICATE_CUSTOMER}.
     *
     * <p>This proves the fixed, observable contract at the real HTTP edge - controller, error
     * mapping, response body included, not just the repository call {@code
     * SignUpConcurrencyIT.theRecoveryLookupSeesASettledWinner} (domain-level) already proves.
     * Forcing a genuinely concurrent loser through this exact interleaving on demand would be a
     * flaky, timing-dependent test; the black-box adversarial pass demonstrated the real
     * interleaving happens under true HTTP concurrency (one 5-way race in twenty-five trials
     * produced exactly this decided-before-fallback timing, and every loser in it received {@code
     * 409 DUPLICATE_CUSTOMER}, never the old raw conflict) - see {@code qa-report.md}, run 2. This
     * test manufactures the "winner already decided" state directly so the contract is checked on
     * every run rather than only when the race happens to land that way.
     */
    @Test
    @DisplayName("a CPF whose earlier application was already decided is told DUPLICATE_CUSTOMER, not the old raw conflict")
    void aLateArrivalForAnAlreadyDecidedCpfGetsTheHonestOutcome() throws Exception {
      Cpf cpf = Cpf.of(Cpfs.random());
      Onboarding winner = onboardingFixture.pendingApplicationFor(cpf, OnboardingFixture.uniqueEmail());

      outcome.apply(winner.id(), BureauDecision.approved());

      assertThat(onboardings.findLatestByCpf(cpf))
          .as("the recovery path a concurrent loser depends on must find the decided winner")
          .hasValueSatisfying(found -> assertThat(found.status().name()).isEqualTo("APPROVED"));

      HttpResponse<String> response =
          post(
              "/api/signup",
              SignUpHttpAcceptanceIT.signUpJson(
                  "Late Arrival",
                  cpf.value(),
                  OnboardingFixture.uniqueEmail().value(),
                  "Passw0rd1",
                  "1990-01-01",
                  "1000.00"));

      assertThat(response.statusCode())
          .as("BR-1: once the CPF belongs to a customer, a late arrival is a duplicate, not a raw conflict")
          .isEqualTo(409);
      assertThat(response.body()).contains("\"code\":\"DUPLICATE_CUSTOMER\"");
      assertThat(response.body())
          .as("the old bug's undocumented conflict code must never surface again")
          .doesNotContain("ONBOARDING_ALREADY_PENDING");
    }
  }

  /** Releases every attempt at the same instant and collects each HTTP response. */
  private List<HttpResponse<String>> submitConcurrently(String cpf) throws Exception {
    CyclicBarrier startTogether = new CyclicBarrier(ATTEMPTS);
    ExecutorService threads = Executors.newFixedThreadPool(ATTEMPTS);
    try {
      List<Callable<HttpResponse<String>>> attempts = new ArrayList<>();
      for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
        int n = attempt;
        attempts.add(
            () -> {
              startTogether.await(10, TimeUnit.SECONDS);
              return post(
                  "/api/signup",
                  SignUpHttpAcceptanceIT.signUpJson(
                      "Race Attempt " + n,
                      cpf,
                      "race-http-" + n + "-" + System.nanoTime() + "@example.com",
                      "Passw0rd1",
                      "1990-01-01",
                      "1000.00"));
            });
      }

      List<HttpResponse<String>> responses = new ArrayList<>();
      for (Future<HttpResponse<String>> finished : threads.invokeAll(attempts, 60, TimeUnit.SECONDS)) {
        responses.add(finished.get());
      }
      return responses;
    } finally {
      threads.shutdownNow();
    }
  }

  private long countRowsForCpf(String table, String cpf) {
    return ((Number)
            entityManager
                .createNativeQuery("SELECT count(*) FROM " + table + " WHERE cpf = :cpf")
                .setParameter("cpf", cpf)
                .getSingleResult())
        .longValue();
  }

  private long countAccountsFor(String cpf) {
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
}
