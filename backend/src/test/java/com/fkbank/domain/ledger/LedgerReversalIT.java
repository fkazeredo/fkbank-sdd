package com.fkbank.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fkbank.testsupport.LedgerFixture;
import com.fkbank.testsupport.LedgerIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Undoing a movement without editing history. */
@DisplayName("Ledger - reversing a posting")
class LedgerReversalIT extends LedgerIntegrationTest {

  @Autowired private Ledger ledger;
  @Autowired private LedgerFixture fixture;

  @Test
  @DisplayName("writes a contra-posting that returns both balances to what they were")
  void reversalRestoresBothBalances() {
    Account from = fixture.customerAccountHolding("100.00");
    Account to = fixture.emptyCustomerAccount();
    Posting original = ledger.record(from.id(), to.id(), Money.of("30.00"));

    Posting contra = ledger.reverse(original.id());

    assertThat(ledger.balanceOf(from.id())).isEqualTo(Money.of("100.00"));
    assertThat(ledger.balanceOf(to.id())).isEqualTo(Money.zero());

    assertThat(contra.debitAccountId()).isEqualTo(to.id());
    assertThat(contra.creditAccountId()).isEqualTo(from.id());
    assertThat(contra.amount()).isEqualTo(original.amount());
    assertThat(contra.reverses()).contains(original.id());
    assertThat(ledger.trialBalance().isConsistent()).isTrue();
  }

  @Test
  @DisplayName("leaves the original posting exactly as it was recorded")
  void originalPostingStaysImmutable() {
    Account from = fixture.customerAccountHolding("100.00");
    Account to = fixture.emptyCustomerAccount();
    Posting original = ledger.record(from.id(), to.id(), Money.of("30.00"));

    ledger.reverse(original.id());

    assertThat(ledger.findPosting(original.id()))
        .hasValueSatisfying(
            stored -> {
              assertThat(stored.amount()).isEqualTo(Money.of("30.00"));
              assertThat(stored.debitAccountId()).isEqualTo(from.id());
              assertThat(stored.creditAccountId()).isEqualTo(to.id());
              assertThat(stored.isReversal()).isFalse();
            });
  }

  @Test
  @DisplayName("refuses to reverse the same posting twice and fabricates nothing")
  void refusesASecondReversal() {
    Account from = fixture.customerAccountHolding("100.00");
    Account to = fixture.emptyCustomerAccount();
    Posting original = ledger.record(from.id(), to.id(), Money.of("30.00"));
    ledger.reverse(original.id());

    assertThatThrownBy(() -> ledger.reverse(original.id()))
        .isInstanceOf(ReversalNotAllowedException.class)
        .satisfies(
            thrown ->
                assertThat(((ReversalNotAllowedException) thrown).code())
                    .isEqualTo("REVERSAL_NOT_ALLOWED"));

    assertThat(ledger.balanceOf(from.id()))
        .as("the account keeps its single-reversal balance; a second contra would invent money")
        .isEqualTo(Money.of("100.00"));
    assertThat(ledger.balanceOf(to.id())).isEqualTo(Money.zero());
    assertThat(ledger.trialBalance().isConsistent()).isTrue();
  }

  @Test
  @DisplayName("refuses to reverse a reversal")
  void refusesToReverseAReversal() {
    Account from = fixture.customerAccountHolding("100.00");
    Account to = fixture.emptyCustomerAccount();
    Posting original = ledger.record(from.id(), to.id(), Money.of("30.00"));
    Posting contra = ledger.reverse(original.id());

    assertThatThrownBy(() -> ledger.reverse(contra.id()))
        .isInstanceOf(ReversalNotAllowedException.class);

    assertThat(ledger.balanceOf(from.id())).isEqualTo(Money.of("100.00"));
    assertThat(ledger.balanceOf(to.id())).isEqualTo(Money.zero());
    assertThat(ledger.trialBalance().isConsistent()).isTrue();
  }

  @Test
  @DisplayName("a reversal obeys the never-below-zero rule like any other posting")
  void reversalIsSubjectToTheSignRule() {
    Account settlement = fixture.settlementAccount();
    Account customer = fixture.emptyCustomerAccount();
    Posting deposit = ledger.record(settlement.id(), customer.id(), Money.of("100.00"));

    Account elsewhere = fixture.emptyCustomerAccount();
    ledger.record(customer.id(), elsewhere.id(), Money.of("100.00"));

    assertThatThrownBy(() -> ledger.reverse(deposit.id()))
        .as("undoing the deposit would take the customer account below zero")
        .isInstanceOf(InsufficientFundsException.class);

    assertThat(ledger.balanceOf(customer.id())).isEqualTo(Money.zero());
    assertThat(ledger.trialBalance().isConsistent()).isTrue();
  }
}
