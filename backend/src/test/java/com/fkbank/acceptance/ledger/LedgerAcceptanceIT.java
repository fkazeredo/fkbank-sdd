package com.fkbank.acceptance.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fkbank.domain.ledger.Account;
import com.fkbank.domain.ledger.AccountId;
import com.fkbank.domain.ledger.AccountKind;
import com.fkbank.domain.ledger.InsufficientFundsException;
import com.fkbank.domain.ledger.Ledger;
import com.fkbank.domain.ledger.Money;
import com.fkbank.domain.ledger.Posting;
import com.fkbank.domain.ledger.ReversalNotAllowedException;
import com.fkbank.domain.ledger.TrialBalance;
import com.fkbank.testsupport.LedgerIntegrationTest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Acceptance suite written against the ledger's published API and the real PostgreSQL schema,
 * independently of how either is implemented.
 *
 * <p>Each acceptance criterion of the ledger specification is executed literally here, plus the
 * adversarial cases a money module has to survive: driving a customer account negative through
 * every route the API offers, tampering with the books behind the application's back, and
 * checking that the verification routine reports what the posting table actually says rather
 * than agreeing with the balances it is supposed to be auditing.
 *
 * <p>Every test that deliberately corrupts the books repairs them before it returns. The
 * PostgreSQL container is shared by the whole integration suite, so drift left behind here would
 * surface as an unexplained failure in an unrelated class.
 */
class LedgerAcceptanceIT extends LedgerIntegrationTest {

  @Autowired private Ledger ledger;
  @Autowired private JdbcTemplate jdbc;

  /** Every account this test opened, so drift can be asserted without depending on run order. */
  private final List<AccountId> accountsUnderTest = new ArrayList<>();

  // ---------------------------------------------------------------- helpers

  private Account track(Account account) {
    accountsUnderTest.add(account.id());
    return account;
  }

  private Account customer() {
    return track(
        ledger.openAccount(AccountKind.CUSTOMER_AVAILABLE, "qa:customer:" + UUID.randomUUID()));
  }

  private Account box() {
    return track(ledger.openAccount(AccountKind.CUSTOMER_BOX, "qa:box:" + UUID.randomUUID()));
  }

  private Account internal() {
    return track(
        ledger.openAccount(AccountKind.INTERNAL_SETTLEMENT, "qa:internal:" + UUID.randomUUID()));
  }

  /**
   * Asserts the books agree with the postings for the accounts this test touched.
   *
   * <p>Scoped rather than global on purpose. The PostgreSQL container is shared by every
   * integration class, and a class that writes postings straight into the table without moving the
   * matching balances leaves drift no later test can clear, because postings are append-only. A
   * global assertion here would therefore pass or fail according to which class ran first.
   */
  private void assertNoDriftOnAccountsUnderTest() {
    assertThat(ledger.trialBalance().driftedAccounts())
        .as("the accounts this test touched must still agree with their postings")
        .doesNotContainAnyElementsOf(accountsUnderTest);
  }

  private Posting fund(Account target, String amount) {
    return ledger.record(internal().id(), target.id(), Money.of(amount));
  }

  /** The balance this account's own postings imply, read straight from the posting table. */
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

  private BigDecimal storedBalance(AccountId account) {
    return jdbc.queryForObject(
        "select amount from balance where account_id = ?", BigDecimal.class, account.value());
  }

  private long postingCount() {
    return jdbc.queryForObject("select count(*) from posting", Long.class);
  }

  private long balanceRowCount() {
    return jdbc.queryForObject("select count(*) from balance", Long.class);
  }

  // ------------------------------------------------- criterion 1: a posting moves both sides

  @Test
  @DisplayName("AC1 - a posting moves both balances, each equals the sum of its own postings")
  void postingMovesBothBalancesAndKeepsTheBooksBalanced() {
    Account source = internal();
    Account destination = customer();

    ledger.record(source.id(), destination.id(), Money.of("100.00"));

    assertThat(ledger.balanceOf(destination.id())).isEqualTo(Money.of("100.00"));
    assertThat(ledger.balanceOf(source.id())).isEqualTo(Money.of("-100.00"));

    assertThat(storedBalance(destination.id()))
        .isEqualByComparingTo(sumOfPostings(destination.id()));
    assertThat(storedBalance(source.id())).isEqualByComparingTo(sumOfPostings(source.id()));

    TrialBalance trialBalance = ledger.trialBalance();
    assertThat(trialBalance.isBalanced()).isTrue();
    assertNoDriftOnAccountsUnderTest();
  }

  // --------------------------------------- criterion 2: a customer account never goes negative

  @Test
  @DisplayName("AC2 - overdrawing a customer account raises INSUFFICIENT_FUNDS and writes nothing")
  void refusesToOverdrawACustomerAccountAndWritesNothing() {
    Account customer = customer();
    Account sink = internal();
    fund(customer, "50.00");

    long postingsBefore = postingCount();
    long balanceRowsBefore = balanceRowCount();
    BigDecimal customerBefore = storedBalance(customer.id());
    BigDecimal sinkBefore = storedBalance(sink.id());

    assertThatThrownBy(() -> ledger.record(customer.id(), sink.id(), Money.of("50.01")))
        .isInstanceOf(InsufficientFundsException.class)
        .extracting(error -> ((InsufficientFundsException) error).code())
        .isEqualTo("INSUFFICIENT_FUNDS");

    assertThat(postingCount()).isEqualTo(postingsBefore);
    assertThat(balanceRowCount()).isEqualTo(balanceRowsBefore);
    assertThat(storedBalance(customer.id())).isEqualByComparingTo(customerBefore);
    assertThat(storedBalance(sink.id())).isEqualByComparingTo(sinkBefore);
  }

  @Test
  @DisplayName("AC2 - a box account is protected exactly like an available account")
  void refusesToOverdrawABoxAccount() {
    Account box = box();
    Account sink = internal();
    fund(box, "10.0000");

    assertThatThrownBy(() -> ledger.record(box.id(), sink.id(), Money.of("10.0001")))
        .isInstanceOf(InsufficientFundsException.class);

    assertThat(ledger.balanceOf(box.id())).isEqualTo(Money.of("10.0000"));
  }

  @Test
  @DisplayName("AC2 - an internal account is allowed to go negative")
  void allowsAnInternalAccountToGoNegative() {
    Account source = internal();
    Account destination = customer();

    ledger.record(source.id(), destination.id(), Money.of("500.00"));

    assertThat(ledger.balanceOf(source.id()).isNegative()).isTrue();
    assertNoDriftOnAccountsUnderTest();
  }

  @Test
  @DisplayName("AC2 - draining a customer account to exactly zero is allowed")
  void allowsDrainingToExactlyZero() {
    Account customer = customer();
    Account sink = internal();
    fund(customer, "75.25");

    ledger.record(customer.id(), sink.id(), Money.of("75.25"));

    assertThat(ledger.balanceOf(customer.id())).isEqualTo(Money.zero());
    assertThat(ledger.balanceOf(customer.id()).isNegative()).isFalse();
  }

  // ----------------------------------------------------- criterion 3: reversal is a contra row

  @Test
  @DisplayName("AC3 - reversal writes a contra referencing the original and restores both sides")
  void reversalWritesAContraAndRestoresBothBalances() {
    Account source = internal();
    Account destination = customer();
    Money before = ledger.balanceOf(destination.id());

    Posting original = ledger.record(source.id(), destination.id(), Money.of("42.50"));
    Posting contra = ledger.reverse(original.id());

    assertThat(contra.reverses()).contains(original.id());
    assertThat(contra.isReversal()).isTrue();
    assertThat(contra.debitAccountId()).isEqualTo(original.creditAccountId());
    assertThat(contra.creditAccountId()).isEqualTo(original.debitAccountId());
    assertThat(contra.amount()).isEqualTo(original.amount());

    assertThat(ledger.balanceOf(destination.id())).isEqualTo(before);
    assertThat(ledger.balanceOf(source.id())).isEqualTo(Money.zero());
    assertNoDriftOnAccountsUnderTest();
  }

  @Test
  @DisplayName("AC3 - the original row survives the reversal byte for byte")
  void theOriginalRowIsNeverMutatedByItsReversal() {
    Account source = internal();
    Account destination = customer();
    Posting original = ledger.record(source.id(), destination.id(), Money.of("13.37"));

    var snapshot =
        jdbc.queryForMap(
            "select * from posting where id = ?", UUID.fromString(original.id().value().toString()));

    ledger.reverse(original.id());

    var afterReversal =
        jdbc.queryForMap(
            "select * from posting where id = ?", UUID.fromString(original.id().value().toString()));
    assertThat(afterReversal).isEqualTo(snapshot);
  }

  // ---------------------------------------- criterion 7 / BR-6: reversal happens at most once

  @Test
  @DisplayName("AC7 - reversing an already-reversed posting raises REVERSAL_NOT_ALLOWED")
  void refusesASecondReversalAndWritesNothing() {
    Account source = internal();
    Account destination = customer();
    Posting original = ledger.record(source.id(), destination.id(), Money.of("20.00"));
    ledger.reverse(original.id());

    long postingsBefore = postingCount();
    Money destinationBefore = ledger.balanceOf(destination.id());
    Money sourceBefore = ledger.balanceOf(source.id());

    assertThatThrownBy(() -> ledger.reverse(original.id()))
        .isInstanceOf(ReversalNotAllowedException.class)
        .extracting(error -> ((ReversalNotAllowedException) error).code())
        .isEqualTo("REVERSAL_NOT_ALLOWED");

    assertThat(postingCount()).isEqualTo(postingsBefore);
    assertThat(ledger.balanceOf(destination.id())).isEqualTo(destinationBefore);
    assertThat(ledger.balanceOf(source.id())).isEqualTo(sourceBefore);
    assertNoDriftOnAccountsUnderTest();
  }

  @Test
  @DisplayName("AC7 - a reversal is never itself reversed")
  void refusesToReverseAReversal() {
    Account source = internal();
    Account destination = customer();
    Posting original = ledger.record(source.id(), destination.id(), Money.of("20.00"));
    Posting contra = ledger.reverse(original.id());

    long postingsBefore = postingCount();

    assertThatThrownBy(() -> ledger.reverse(contra.id()))
        .isInstanceOf(ReversalNotAllowedException.class)
        .extracting(error -> ((ReversalNotAllowedException) error).code())
        .isEqualTo("REVERSAL_NOT_ALLOWED");

    assertThat(postingCount()).isEqualTo(postingsBefore);
    assertNoDriftOnAccountsUnderTest();
  }

  @Test
  @DisplayName("ADVERSARIAL - reversing a deposit already spent cannot drive the customer negative")
  void reversingASpentDepositIsRefusedRatherThanFabricatingADebt() {
    Account source = internal();
    Account customer = customer();
    Account sink = internal();

    Posting deposit = ledger.record(source.id(), customer.id(), Money.of("100.00"));
    ledger.record(customer.id(), sink.id(), Money.of("100.00"));
    assertThat(ledger.balanceOf(customer.id())).isEqualTo(Money.zero());

    long postingsBefore = postingCount();

    assertThatThrownBy(() -> ledger.reverse(deposit.id()))
        .isInstanceOf(InsufficientFundsException.class);

    assertThat(postingCount()).isEqualTo(postingsBefore);
    assertThat(ledger.balanceOf(customer.id())).isEqualTo(Money.zero());
    assertThat(ledger.balanceOf(customer.id()).isNegative()).isFalse();
    assertNoDriftOnAccountsUnderTest();
  }

  @Test
  @DisplayName("ADVERSARIAL - a refused reversal does not consume the single reversal allowance")
  void aRefusedReversalCanStillBePerformedOnceFundsReturn() {
    Account source = internal();
    Account customer = customer();
    Account sink = internal();

    Posting deposit = ledger.record(source.id(), customer.id(), Money.of("100.00"));
    ledger.record(customer.id(), sink.id(), Money.of("100.00"));
    assertThatThrownBy(() -> ledger.reverse(deposit.id()))
        .isInstanceOf(InsufficientFundsException.class);

    ledger.record(sink.id(), customer.id(), Money.of("100.00"));
    Posting contra = ledger.reverse(deposit.id());

    assertThat(contra.reverses()).contains(deposit.id());
    assertNoDriftOnAccountsUnderTest();
  }

  // ------------------------------------------------ criterion 5: the verification routine bites

  @Test
  @DisplayName("AC5 - a materialized balance tampered behind the ledger is flagged as drifted")
  void verificationFlagsATamperedBalance() {
    Account source = internal();
    Account destination = customer();
    ledger.record(source.id(), destination.id(), Money.of("64.00"));

    assertThat(ledger.trialBalance().driftedAccounts()).doesNotContain(destination.id());

    BigDecimal honest = storedBalance(destination.id());
    jdbc.update(
        "update balance set amount = amount + 1 where account_id = ?", destination.id().value());
    try {
      TrialBalance tampered = ledger.trialBalance();
      assertThat(tampered.driftedAccounts()).contains(destination.id());
      assertThat(tampered.isConsistent()).isFalse();
      assertThat(tampered.isBalanced())
          .as("the totals check must not mask a per-account drift")
          .isTrue();
    } finally {
      jdbc.update(
          "update balance set amount = ? where account_id = ?", honest, destination.id().value());
    }

    assertNoDriftOnAccountsUnderTest();
  }

  @Test
  @DisplayName("AC5 - a posting inserted behind the ledger is detected through the balances")
  void verificationDetectsAPostingSmuggledInByRawSql() {
    Account source = internal();
    Account destination = customer();
    ledger.record(source.id(), destination.id(), Money.of("10.00"));

    jdbc.update(
        """
        insert into posting (id, debit_account_id, credit_account_id, amount, currency, occurred_at)
        values (?, ?, ?, ?, 'BRL', now())
        """,
        UUID.randomUUID(),
        source.id().value(),
        destination.id().value(),
        new BigDecimal("999.0000"));

    TrialBalance smuggled = ledger.trialBalance();
    assertThat(smuggled.driftedAccounts()).contains(destination.id(), source.id());
    assertThat(smuggled.isConsistent()).isFalse();

    // The posting is append-only, so the books are repaired by moving the balances to it.
    jdbc.update(
        "update balance set amount = ? where account_id = ?",
        sumOfPostings(destination.id()),
        destination.id().value());
    jdbc.update(
        "update balance set amount = ? where account_id = ?",
        sumOfPostings(source.id()),
        source.id().value());
    assertNoDriftOnAccountsUnderTest();
  }

  @Test
  @DisplayName("AC5 - the trial balance reads the posting table, not the balances it audits")
  void verificationIsNotRecomputedFromTheBalancesItAudits() {
    Account source = internal();
    Account destination = customer();
    ledger.record(source.id(), destination.id(), Money.of("7.00"));

    BigDecimal honestSource = storedBalance(source.id());
    BigDecimal honestDestination = storedBalance(destination.id());

    // Both sides moved by the same amount: the totals stay equal while each account drifts.
    jdbc.update(
        "update balance set amount = amount + 5 where account_id = ?", destination.id().value());
    jdbc.update("update balance set amount = amount - 5 where account_id = ?", source.id().value());
    try {
      TrialBalance drifted = ledger.trialBalance();
      assertThat(drifted.driftedAccounts()).contains(destination.id(), source.id());
      assertThat(drifted.isConsistent()).isFalse();
    } finally {
      jdbc.update(
          "update balance set amount = ? where account_id = ?",
          honestDestination,
          destination.id().value());
      jdbc.update(
          "update balance set amount = ? where account_id = ?", honestSource, source.id().value());
    }
  }

  // ------------------------------------------------------------- criterion 6: Money at the edge

  @Test
  @DisplayName("AC6 - the edge rounds half-up to two decimals while math keeps four")
  void moneyRoundsHalfUpOnlyAtTheEdge() {
    Money yield = Money.of("0.00456");

    assertThat(yield.amount()).isEqualByComparingTo("0.0046");
    assertThat(yield.amount().scale()).isEqualTo(4);
    assertThat(yield.atEdge()).isEqualByComparingTo("0.00");
    assertThat(yield.atEdge().scale()).isEqualTo(2);

    assertThat(Money.of("0.005").atEdge()).isEqualByComparingTo("0.01");
    assertThat(Money.of("0.015").atEdge()).isEqualByComparingTo("0.02");
    assertThat(Money.of("-0.005").atEdge()).isEqualByComparingTo("-0.01");
  }

  @Test
  @DisplayName("AC6 - repeated additions never round mid-calculation")
  void moneyDoesNotRoundMidCalculation() {
    Money total = Money.zero();
    for (int i = 0; i < 100; i++) {
      total = total.add(Money.of("0.0001"));
    }
    assertThat(total.amount()).isEqualByComparingTo("0.0100");
    assertThat(total.atEdge()).isEqualByComparingTo("0.01");
  }

  @Test
  @DisplayName("AC6 - an N-way split sums exactly back with the remainder on the last entry")
  void splitNeverLosesOrInventsAFraction() {
    for (int parts = 1; parts <= 13; parts++) {
      for (String amount : List.of("10.00", "0.0001", "100.0003", "1.0000", "0.0007")) {
        Money original = Money.of(amount);
        List<Money> parted = original.split(parts);

        assertThat(parted).hasSize(parts);
        Money recomposed = parted.stream().reduce(Money.zero(), Money::add);
        assertThat(recomposed)
            .as("split of %s into %d parts must sum back exactly", amount, parts)
            .isEqualTo(original);
      }
    }
  }

  @Test
  @DisplayName("AC6 - a four-decimal amount survives the PostgreSQL round trip unrounded")
  void fourDecimalsSurvivePersistence() {
    Account source = internal();
    Account destination = customer();

    ledger.record(source.id(), destination.id(), Money.of("0.0001"));

    assertThat(ledger.balanceOf(destination.id())).isEqualTo(Money.of("0.0001"));
    assertThat(storedBalance(destination.id())).isEqualByComparingTo("0.0001");
    assertThat(ledger.balanceOf(destination.id()).atEdge()).isEqualByComparingTo("0.00");
  }

  // --------------------------------------------------------------- edge cases the spec names

  @Test
  @DisplayName("EDGE - zero, negative and self-directed postings are all refused")
  void refusesDegeneratePostings() {
    Account source = internal();
    Account destination = customer();
    long postingsBefore = postingCount();

    assertThatCode(() -> ledger.record(source.id(), destination.id(), Money.zero()))
        .as("a posting of zero moves no money and must be refused")
        .isInstanceOf(RuntimeException.class);
    assertThatCode(() -> ledger.record(source.id(), destination.id(), Money.of("-1.00")))
        .as("a negative posting is a debit disguised as a credit and must be refused")
        .isInstanceOf(RuntimeException.class);
    assertThatCode(() -> ledger.record(source.id(), source.id(), Money.of("1.00")))
        .as("an account cannot post against itself")
        .isInstanceOf(RuntimeException.class);

    assertThat(postingCount()).isEqualTo(postingsBefore);
  }

  @Test
  @DisplayName("EDGE - reversing a posting that does not exist is refused, not silently ignored")
  void refusesToReverseAnUnknownPosting() {
    long postingsBefore = postingCount();

    assertThatCode(() -> ledger.reverse(com.fkbank.domain.ledger.PostingId.next()))
        .isInstanceOf(RuntimeException.class);

    assertThat(postingCount()).isEqualTo(postingsBefore);
  }
}
