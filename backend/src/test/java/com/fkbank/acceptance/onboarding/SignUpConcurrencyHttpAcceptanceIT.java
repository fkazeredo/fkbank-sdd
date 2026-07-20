package com.fkbank.acceptance.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

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
     * {@code SignUp.submit()} answers the race for a losing thread by catching the store's
     * refusal and calling {@code onboardings.findPendingByCpf(cpf)} to hand back the application
     * that won ({@code SignUp.java}, the catch block right after
     * {@code onboardings.save(Onboarding.submit(...))}). That lookup is scoped to
     * {@code status = 'PENDING'} ({@code OnboardingJpaRepository.findByCpfAndStatus}). The
     * migration comment on {@code onboarding_one_pending_per_cpf} promises "the loser reads the
     * winner's row and returns it, so the person sees one application rather than an error" -
     * with no qualification on the winner's status by the time that read happens.
     *
     * <p>This test proves the qualification exists in the code even though the comment does not
     * name it: the instant the winner's application is decided - which the synchronous
     * {@code approve}/{@code decline} bureau scenarios can complete before a genuinely raced
     * loser gets to retry - the fallback the loser depends on can no longer find it, and
     * {@code SignUp.submit()} re-throws the original {@code OnboardingAlreadyPendingException}
     * instead of the graceful "already under way" response BR-5 promises. The black-box HTTP race
     * above reproduced the resulting {@code 409 ONBOARDING_ALREADY_PENDING} once in thirteen
     * trials; this test proves the mechanism deterministically rather than relying on that timing
     * window to recur.
     */
    @Test
    @DisplayName("the recovery lookup can no longer find an application once it has been decided")
    void theFallbackLookupStillFindsADecidedWinner() {
      Onboarding winner = onboardingFixture.pendingApplication();

      outcome.apply(winner.id(), BureauDecision.approved());

      assertThat(onboardings.findPendingByCpf(winner.cpf()))
          .as(
              "a concurrent loser that reaches this exact lookup after the winner's synchronous"
                  + " decision has already landed is told the application is 'already pending' -"
                  + " even though it has, in fact, already been decided")
          .isPresent();
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
