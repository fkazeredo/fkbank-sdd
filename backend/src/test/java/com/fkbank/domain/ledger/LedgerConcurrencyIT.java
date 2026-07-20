package com.fkbank.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.fkbank.testsupport.LedgerFixture;
import com.fkbank.testsupport.LedgerIntegrationTest;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The double-spend test.
 *
 * <p>Two transactions try to drain the same account at the same moment, and it holds enough for
 * exactly one of them. Without the pessimistic lock both read the same starting balance, both
 * conclude the money is there, and both commit — the account ends up negative and the ledger is
 * still internally consistent about it, which is the worst kind of wrong.
 *
 * <p>Deliberately run against a real PostgreSQL rather than an in-memory profile: this asserts
 * the behaviour of {@code SELECT ... FOR UPDATE}, which is the database's, not Java's. The
 * assertions are on outcomes and on the invariants at the end, never on which thread won.
 */
@DisplayName("Ledger - concurrent postings")
class LedgerConcurrencyIT extends LedgerIntegrationTest {

  private static final int TIMEOUT_SECONDS = 30;

  @Autowired private Ledger ledger;
  @Autowired private LedgerFixture fixture;

  @Test
  @DisplayName("two drains of a single-funded account: exactly one commits, the other is refused")
  void exactlyOneOfTwoConcurrentDrainsCommits() throws Exception {
    Account funded = fixture.customerAccountHolding("100.00");
    Account first = fixture.emptyCustomerAccount();
    Account second = fixture.emptyCustomerAccount();

    List<Outcome> outcomes =
        runTogether(
            () -> attemptDrain(funded, first),
            () -> attemptDrain(funded, second));

    assertThat(outcomes)
        .as("the account funded one withdrawal, so exactly one may succeed")
        .containsExactlyInAnyOrder(Outcome.COMMITTED, Outcome.INSUFFICIENT_FUNDS);

    assertThat(ledger.balanceOf(funded.id()))
        .as("a customer account may never end below zero")
        .isEqualTo(Money.zero());

    Money movedOut = ledger.balanceOf(first.id()).add(ledger.balanceOf(second.id()));
    assertThat(movedOut)
        .as("the money that left the account arrived in exactly one of the destinations")
        .isEqualTo(Money.of("100.00"));

    TrialBalance trial = ledger.trialBalance();
    assertThat(trial.isBalanced()).isTrue();
    assertThat(trial.driftedAccounts()).isEmpty();
  }

  @Test
  @DisplayName("opposing transfers between the same two accounts do not deadlock")
  void opposingTransfersDoNotDeadlock() throws Exception {
    Account left = fixture.customerAccountHolding("100.00");
    Account right = fixture.customerAccountHolding("100.00");

    List<Outcome> outcomes =
        runTogether(
            () -> attempt(left, right, "40.00"),
            () -> attempt(right, left, "25.00"));

    assertThat(outcomes)
        .as("both movements are affordable; locking in ascending id order keeps them from"
            + " waiting on each other")
        .containsExactly(Outcome.COMMITTED, Outcome.COMMITTED);

    assertThat(ledger.balanceOf(left.id())).isEqualTo(Money.of("85.00"));
    assertThat(ledger.balanceOf(right.id())).isEqualTo(Money.of("115.00"));
    assertThat(ledger.trialBalance().isConsistent()).isTrue();
  }

  private Outcome attemptDrain(Account from, Account to) {
    return attempt(from, to, "100.00");
  }

  private Outcome attempt(Account from, Account to, String amount) {
    try {
      ledger.record(from.id(), to.id(), Money.of(amount));
      return Outcome.COMMITTED;
    } catch (InsufficientFundsException expected) {
      return Outcome.INSUFFICIENT_FUNDS;
    }
  }

  @Test
  @DisplayName("two reversals of one posting: one contra is written, the loser is told why")
  void concurrentReversalsProduceOneContraAndAStableRefusal() throws Exception {
    Account settlement = fixture.settlementAccount();
    Account customer = fixture.emptyCustomerAccount();
    Posting deposit = ledger.record(settlement.id(), customer.id(), Money.of("30.00"));

    List<Outcome> outcomes =
        runTogether(() -> attemptReversal(deposit), () -> attemptReversal(deposit));

    assertThat(outcomes)
        .as("the loser must learn that the posting was already reversed, not that the account"
            + " it drained happened to be empty, and must not be handed a persistence error")
        .containsExactlyInAnyOrder(Outcome.COMMITTED, Outcome.REVERSAL_NOT_ALLOWED);

    assertThat(ledger.balanceOf(customer.id()))
        .as("exactly one contra was applied, so the deposit is undone once")
        .isEqualTo(Money.zero());
    assertThat(ledger.balanceOf(settlement.id())).isEqualTo(Money.zero());
    assertThat(ledger.trialBalance().isConsistent()).isTrue();
  }

  private Outcome attemptReversal(Posting original) {
    try {
      ledger.reverse(original.id());
      return Outcome.COMMITTED;
    } catch (ReversalNotAllowedException expected) {
      return Outcome.REVERSAL_NOT_ALLOWED;
    } catch (InsufficientFundsException wrongReason) {
      return Outcome.INSUFFICIENT_FUNDS;
    }
  }

  /** Releases both callables at the same instant and collects what each one concluded. */
  private static List<Outcome> runTogether(Callable<Outcome> left, Callable<Outcome> right)
      throws Exception {
    CountDownLatch releaseGate = new CountDownLatch(1);
    ExecutorService threads = Executors.newFixedThreadPool(2);
    try {
      Future<Outcome> first = threads.submit(gatedBy(releaseGate, left));
      Future<Outcome> second = threads.submit(gatedBy(releaseGate, right));

      releaseGate.countDown();

      return List.of(
          first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
          second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    } finally {
      threads.shutdownNow();
      assertThat(threads.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS))
          .as("a thread still running here means a lock was never released")
          .isTrue();
    }
  }

  private static Callable<Outcome> gatedBy(CountDownLatch gate, Callable<Outcome> work) {
    return () -> {
      gate.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      return work.call();
    };
  }

  private enum Outcome {
    COMMITTED,
    INSUFFICIENT_FUNDS,
    REVERSAL_NOT_ALLOWED
  }
}
