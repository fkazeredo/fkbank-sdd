package com.fkbank.domain.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import com.fkbank.domain.customer.Cpf;
import com.fkbank.domain.customer.DuplicateCustomerException;
import com.fkbank.testsupport.ControllableBureau;
import com.fkbank.testsupport.Cpfs;
import com.fkbank.testsupport.OnboardingFixture;
import com.fkbank.testsupport.OnboardingIntegrationTest;
import jakarta.persistence.EntityManager;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Two people submitting the same CPF at the same instant.
 *
 * <p>Both requests check for an existing customer, both find none, and both go on to write. The
 * check cannot settle this — it is true when it runs and false a moment later — so what decides
 * the outcome is a partial unique index in the database. The test exists to prove that it does,
 * because the failure it prevents is one person holding two accounts under one tax number, and
 * that failure is invisible until someone audits.
 *
 * <p>Deterministic rather than hopeful: a barrier releases both threads at the same moment
 * instead of relying on them happening to overlap.
 */
@DisplayName("Two sign-ups for the same CPF at once")
class SignUpConcurrencyIT extends OnboardingIntegrationTest {

  private static final int ATTEMPTS = 2;

  @Autowired private SignUp signUp;
  @Autowired private ControllableBureau bureau;
  @Autowired private OnboardingRepository onboardings;
  @Autowired private EntityManager entityManager;

  @BeforeEach
  void resetTheBureau() {
    bureau.reset();
  }

  @Test
  @DisplayName("produce exactly one customer and one account")
  void exactlyOneOfEachSurvives() throws Exception {
    String cpf = Cpfs.random();
    bureau.willAnswer(BureauDecision.approved());

    List<Outcome> outcomes = submitConcurrently(cpf);

    assertThat(outcomes)
        .as("both attempts must finish; a thread that died proves nothing about the rule")
        .hasSize(ATTEMPTS);

    long customersWithThisCpf = countRows("customer", cpf);
    long accountsOpened = countAccountsFor(cpf);

    assertThat(customersWithThisCpf)
        .as("one tax number, one customer, however concurrent the submissions were")
        .isEqualTo(1);
    assertThat(accountsOpened)
        .as("one customer, one account")
        .isEqualTo(1);
  }

  @Test
  @DisplayName("leave exactly one application behind, not two")
  void exactlyOneApplicationSurvives() throws Exception {
    String cpf = Cpfs.random();
    // Nobody answers, so both attempts stay pending and the partial unique index is the only
    // thing standing between them and two open applications for one person.
    bureau.willAnswer(BureauDecision.undetermined());

    submitConcurrently(cpf);

    assertThat(countRows("onboarding", cpf))
        .as("a second pending application for the same person must never be written")
        .isEqualTo(1);
    assertThat(onboardings.findPendingByCpf(Cpf.of(cpf)))
        .as("the application that won is the one the applicant is shown")
        .isPresent();
  }

  /** Releases both submissions at the same instant and collects what each one saw. */
  private List<Outcome> submitConcurrently(String cpf) throws Exception {
    CyclicBarrier startTogether = new CyclicBarrier(ATTEMPTS);
    ExecutorService threads = Executors.newFixedThreadPool(ATTEMPTS);
    try {
      List<Callable<Outcome>> attempts = new ArrayList<>();
      for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
        attempts.add(
            () -> {
              startTogether.await(10, TimeUnit.SECONDS);
              try {
                return new Outcome(
                    signUp.submit(
                        new SignUpRequest(
                            "Ada Lovelace",
                            cpf,
                            OnboardingFixture.uniqueEmail().value(),
                            OnboardingFixture.password(),
                            OnboardingFixture.birthDate().toString(),
                            "4500.00")),
                    null);
              } catch (DuplicateCustomerException | OnboardingException refused) {
                // A refusal is a legitimate outcome here: whichever attempt loses is supposed
                // to be turned away rather than to write a second row.
                return new Outcome(null, refused);
              }
            });
      }

      List<Outcome> outcomes = new ArrayList<>();
      for (Future<Outcome> finished : threads.invokeAll(attempts, 60, TimeUnit.SECONDS)) {
        outcomes.add(finished.get());
      }
      return outcomes;
    } finally {
      threads.shutdownNow();
    }
  }

  private long countRows(String table, String cpf) {
    // Counted in SQL rather than through a repository: the question is how many rows exist, and
    // a query that reads through the persistence context could be answered from a cache.
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

  /** What one attempt saw: either a result, or the refusal that turned it away. */
  private record Outcome(SignUpResult result, RuntimeException refusal) {}
}
