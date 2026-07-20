package com.fkbank.acceptance.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.fkbank.domain.ledger.Account;
import com.fkbank.domain.ledger.AccountId;
import com.fkbank.domain.ledger.AccountKind;
import com.fkbank.domain.ledger.InsufficientFundsException;
import com.fkbank.domain.ledger.Ledger;
import com.fkbank.domain.ledger.Money;
import com.fkbank.domain.ledger.Posting;
import com.fkbank.domain.ledger.ReversalNotAllowedException;
import com.fkbank.testsupport.LedgerIntegrationTest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The races a ledger has to survive, run against the real PostgreSQL rather than an in-memory
 * profile, because the guarantees under test are the database's: row locks, a partial unique
 * index, and transaction atomicity.
 *
 * <p>Two windows are attacked. The first is the classic double spend: two transfers draining an
 * account funded for only one of them. The second is the reversal window, where the application
 * reads "has this been reversed?" and writes the contra as two separate steps — the interval
 * between them is exactly where a second reversal would slip through if the unique index were
 * not carrying the guarantee.
 *
 * <p>The assertions are on outcomes and on the books, never on timing.
 */
class LedgerRaceAcceptanceIT extends LedgerIntegrationTest {

  private static final int RACE_TIMEOUT_SECONDS = 30;

  @Autowired private Ledger ledger;
  @Autowired private JdbcTemplate jdbc;

  /** Every account this test opened, so drift can be asserted without depending on run order. */
  private final List<AccountId> accountsUnderTest = new ArrayList<>();

  private Account track(Account account) {
    accountsUnderTest.add(account.id());
    return account;
  }

  private Account customer() {
    return track(
        ledger.openAccount(AccountKind.CUSTOMER_AVAILABLE, "qa:customer:" + UUID.randomUUID()));
  }

  private Account internal() {
    return track(
        ledger.openAccount(AccountKind.INTERNAL_SETTLEMENT, "qa:internal:" + UUID.randomUUID()));
  }

  /**
   * Asserts the books agree with the postings for the accounts this test touched.
   *
   * <p>Scoped rather than global because the PostgreSQL container is shared across the whole
   * integration suite, and postings are append-only: drift written directly into the table by
   * another class can never be cleared, so a global check would depend on run order.
   */
  private void assertNoDriftOnAccountsUnderTest() {
    assertThat(ledger.trialBalance().driftedAccounts())
        .as("the accounts this test touched must still agree with their postings")
        .doesNotContainAnyElementsOf(accountsUnderTest);
  }

  private BigDecimal sumOfPostings(AccountId account) {
    return jdbc.queryForObject(
        """
        select coalesce(sum(case when credit_account_id = ? then amount else 0 end), 0)
             - coalesce(sum(case when debit_account_id  = ? then amount else 0 end), 0)
          from posting
        """,
        BigDecimal.class,
        account.value(),
        account.value());
  }

  private long contrasFor(Posting original) {
    return jdbc.queryForObject(
        "select count(*) from posting where reverses_posting_id = ?",
        Long.class,
        original.id().value());
  }

  /** Releases every task at once and collects whatever each one produced, success or failure. */
  private <T> List<Outcome<T>> runTogether(List<Callable<T>> tasks) throws Exception {
    CountDownLatch startGate = new CountDownLatch(1);
    CountDownLatch ready = new CountDownLatch(tasks.size());
    ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
    try {
      List<Future<Outcome<T>>> futures = new ArrayList<>();
      for (Callable<T> task : tasks) {
        futures.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  startGate.await();
                  try {
                    return Outcome.succeeded(task.call());
                  } catch (Throwable failure) {
                    return Outcome.<T>failed(failure);
                  }
                }));
      }
      assertThat(ready.await(RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
      startGate.countDown();

      List<Outcome<T>> outcomes = new ArrayList<>();
      for (Future<Outcome<T>> future : futures) {
        outcomes.add(future.get(RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
      }
      return outcomes;
    } finally {
      pool.shutdownNow();
    }
  }

  private record Outcome<T>(T value, Throwable failure) {
    static <T> Outcome<T> succeeded(T value) {
      return new Outcome<>(value, null);
    }

    static <T> Outcome<T> failed(Throwable failure) {
      return new Outcome<>(null, failure);
    }

    boolean isSuccess() {
      return failure == null;
    }
  }

  // ------------------------------------------------- criterion 4: two drains, one funded account

  @RepeatedTest(3)
  @DisplayName("AC4 - two concurrent drains of a single-funded account: exactly one commits")
  void exactlyOneOfTwoConcurrentDrainsCommits() throws Exception {
    Account customer = customer();
    Account sinkA = internal();
    Account sinkB = internal();
    ledger.record(internal().id(), customer.id(), Money.of("100.00"));

    List<Outcome<Posting>> outcomes =
        runTogether(
            List.of(
                () -> ledger.record(customer.id(), sinkA.id(), Money.of("100.00")),
                () -> ledger.record(customer.id(), sinkB.id(), Money.of("100.00"))));

    assertThat(outcomes.stream().filter(Outcome::isSuccess).count())
        .as("a single-funded account must fund exactly one drain, never two")
        .isEqualTo(1);

    Throwable loser =
        outcomes.stream().filter(outcome -> !outcome.isSuccess()).findFirst().orElseThrow().failure();
    assertThat(rootCauseOf(loser)).isInstanceOf(InsufficientFundsException.class);

    assertThat(ledger.balanceOf(customer.id())).isEqualTo(Money.zero());
    assertThat(ledger.balanceOf(customer.id()).isNegative()).isFalse();
    assertThat(sumOfPostings(customer.id())).isEqualByComparingTo(BigDecimal.ZERO);
    assertNoDriftOnAccountsUnderTest();
    assertThat(ledger.trialBalance().isBalanced()).isTrue();
  }

  @RepeatedTest(3)
  @DisplayName("AC4 - opposing transfers over the same pair neither deadlock nor lose an update")
  void opposingTransfersDoNotDeadlockOrLoseAnUpdate() throws Exception {
    Account left = customer();
    Account right = customer();
    ledger.record(internal().id(), left.id(), Money.of("100.00"));
    ledger.record(internal().id(), right.id(), Money.of("100.00"));

    List<Outcome<Posting>> outcomes =
        runTogether(
            List.of(
                () -> ledger.record(left.id(), right.id(), Money.of("40.00")),
                () -> ledger.record(right.id(), left.id(), Money.of("25.00"))));

    assertThat(outcomes).allMatch(Outcome::isSuccess);
    assertThat(ledger.balanceOf(left.id())).isEqualTo(Money.of("85.00"));
    assertThat(ledger.balanceOf(right.id())).isEqualTo(Money.of("115.00"));
    assertNoDriftOnAccountsUnderTest();
  }

  // ----------------------------------------- the reversal window: read-then-write under a race

  @RepeatedTest(5)
  @DisplayName("ADVERSARIAL - racing two reversals of one posting produces exactly one contra")
  void racingTwoReversalsProducesExactlyOneContra() throws Exception {
    Account source = internal();
    Account destination = customer();
    Posting original = ledger.record(source.id(), destination.id(), Money.of("30.00"));

    List<Outcome<Posting>> outcomes =
        runTogether(List.of(() -> ledger.reverse(original.id()), () -> ledger.reverse(original.id())));

    assertThat(outcomes.stream().filter(Outcome::isSuccess).count())
        .as("a posting is reversed at most once, even when two reversals race")
        .isEqualTo(1);
    assertThat(contrasFor(original))
        .as("exactly one contra-posting may reference the original")
        .isEqualTo(1L);

    assertThat(ledger.balanceOf(destination.id()))
        .as("a double reversal would fabricate a debt on the customer side")
        .isEqualTo(Money.zero());
    assertThat(ledger.balanceOf(source.id())).isEqualTo(Money.zero());
    assertThat(sumOfPostings(destination.id())).isEqualByComparingTo(BigDecimal.ZERO);
    assertNoDriftOnAccountsUnderTest();
    assertThat(ledger.trialBalance().isBalanced()).isTrue();
  }

  @RepeatedTest(5)
  @DisplayName("ADVERSARIAL - the loser of a reversal race gets the stable REVERSAL_NOT_ALLOWED")
  void theLoserOfAReversalRaceGetsTheStableDomainError() throws Exception {
    Account source = internal();
    Account destination = customer();
    Posting original = ledger.record(source.id(), destination.id(), Money.of("30.00"));

    List<Outcome<Posting>> outcomes =
        runTogether(List.of(() -> ledger.reverse(original.id()), () -> ledger.reverse(original.id())));

    Throwable loser =
        outcomes.stream().filter(outcome -> !outcome.isSuccess()).findFirst().orElseThrow().failure();

    assertThat(rootCauseOf(loser))
        .as(
            "the specification promises REVERSAL_NOT_ALLOWED for a repeat reversal; a race must"
                + " not degrade that into a raw persistence error, got: %s",
            describe(loser))
        .isInstanceOf(ReversalNotAllowedException.class);
  }

  @RepeatedTest(5)
  @DisplayName("ADVERSARIAL - a funded second reversal is stopped by the index, not by the balance")
  void theLoserOfAFundedReversalRaceGetsTheStableDomainError() throws Exception {
    Account source = internal();
    Account destination = customer();
    Posting original = ledger.record(source.id(), destination.id(), Money.of("30.00"));
    // Extra funds, so the losing reversal's debit succeeds and the race reaches the insert.
    ledger.record(internal().id(), destination.id(), Money.of("100.00"));

    List<Outcome<Posting>> outcomes =
        runTogether(List.of(() -> ledger.reverse(original.id()), () -> ledger.reverse(original.id())));

    assertThat(contrasFor(original)).isEqualTo(1L);
    assertThat(ledger.balanceOf(destination.id())).isEqualTo(Money.of("100.00"));
    assertNoDriftOnAccountsUnderTest();

    Throwable loser =
        outcomes.stream().filter(outcome -> !outcome.isSuccess()).findFirst().orElseThrow().failure();
    assertThat(rootCauseOf(loser))
        .as(
            "with funds available the second reversal reaches the unique index; the caller must"
                + " still see REVERSAL_NOT_ALLOWED rather than a persistence error, got: %s",
            describe(loser))
        .isInstanceOf(ReversalNotAllowedException.class);
  }

  @Test
  @DisplayName("ADVERSARIAL - a reversal racing a spend cannot leave the customer negative")
  void aReversalRacingASpendNeverDrivesTheCustomerNegative() throws Exception {
    Account source = internal();
    Account customer = customer();
    Account sink = internal();
    Posting deposit = ledger.record(source.id(), customer.id(), Money.of("100.00"));

    List<Outcome<Posting>> outcomes =
        runTogether(
            List.of(
                () -> ledger.reverse(deposit.id()),
                () -> ledger.record(customer.id(), sink.id(), Money.of("100.00"))));

    assertThat(outcomes.stream().filter(Outcome::isSuccess).count())
        .as("only one of the two claims on the same hundred may commit")
        .isEqualTo(1);
    assertThat(ledger.balanceOf(customer.id()).isNegative())
        .as("a customer balance may never end a race below zero")
        .isFalse();
    assertThat(ledger.balanceOf(customer.id())).isEqualTo(Money.zero());
    assertThat(sumOfPostings(customer.id())).isEqualByComparingTo(BigDecimal.ZERO);
    assertNoDriftOnAccountsUnderTest();
  }

  private static Throwable rootCauseOf(Throwable failure) {
    Throwable current = failure;
    while (current.getCause() != null && current.getCause() != current) {
      if (current instanceof com.fkbank.domain.ledger.LedgerException) {
        return current;
      }
      current = current.getCause();
    }
    return current;
  }

  private static String describe(Throwable failure) {
    StringBuilder chain = new StringBuilder();
    Throwable current = failure;
    while (current != null) {
      chain.append(current.getClass().getName()).append(": ").append(current.getMessage());
      current = current.getCause();
      if (current != null) {
        chain.append(" <- ");
      }
    }
    return chain.toString();
  }
}
