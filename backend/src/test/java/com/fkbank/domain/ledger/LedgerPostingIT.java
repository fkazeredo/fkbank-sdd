package com.fkbank.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fkbank.testsupport.LedgerFixture;
import com.fkbank.testsupport.LedgerIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Recording money movements against a real PostgreSQL. */
@DisplayName("Ledger - recording a posting")
class LedgerPostingIT extends LedgerIntegrationTest {

  @Autowired private Ledger ledger;
  @Autowired private LedgerFixture fixture;

  @Test
  @DisplayName("moves the amount between the two accounts and keeps the books balanced")
  void movesMoneyAndKeepsTheBooksBalanced() {
    Account from = fixture.customerAccountHolding("100.00");
    Account to = fixture.emptyCustomerAccount();

    ledger.record(from.id(), to.id(), Money.of("30.00"));

    assertThat(balanceOf(from)).isEqualTo(Money.of("70.00"));
    assertThat(balanceOf(to)).isEqualTo(Money.of("30.00"));

    TrialBalance trial = ledger.trialBalance();
    assertThat(trial.isConsistent())
        .as("every balance must equal the sum of its own postings, and debits must equal credits")
        .isTrue();
  }

  @Test
  @DisplayName("records the posting with both legs and the instant it happened")
  void recordsBothLegs() {
    Account from = fixture.customerAccountHolding("50.00");
    Account to = fixture.emptyCustomerAccount();

    Posting posting = ledger.record(from.id(), to.id(), Money.of("12.3456"));

    assertThat(ledger.findPosting(posting.id())).contains(posting);
    assertThat(posting.debitAccountId()).isEqualTo(from.id());
    assertThat(posting.creditAccountId()).isEqualTo(to.id());
    assertThat(posting.amount()).isEqualTo(Money.of("12.3456"));
    assertThat(posting.occurredAt()).isNotNull();
    assertThat(posting.isReversal()).isFalse();
  }

  @Test
  @DisplayName("keeps four decimal places through the database round trip")
  void keepsFourDecimalsThroughPersistence() {
    Account from = fixture.customerAccountHolding("10.00");
    Account to = fixture.emptyCustomerAccount();

    ledger.record(from.id(), to.id(), Money.of("0.0046"));

    assertThat(balanceOf(to).amount()).isEqualByComparingTo("0.0046");
    assertThat(balanceOf(to).atEdge()).isEqualByComparingTo("0.00");
  }

  @Test
  @DisplayName("refuses to overdraw a customer account, writing nothing and changing no balance")
  void refusesToOverdrawAndWritesNothing() {
    Account from = fixture.customerAccountHolding("10.00");
    Account to = fixture.emptyCustomerAccount();

    assertThatThrownBy(() -> ledger.record(from.id(), to.id(), Money.of("10.01")))
        .isInstanceOf(InsufficientFundsException.class);

    assertThat(balanceOf(from))
        .as("a refused posting must leave the debited account exactly as it was")
        .isEqualTo(Money.of("10.00"));
    assertThat(balanceOf(to)).isEqualTo(Money.zero());
    assertThat(ledger.trialBalance().isConsistent()).isTrue();
  }

  @Test
  @DisplayName("lets an internal account go negative")
  void allowsInternalAccountsToGoNegative() {
    Account settlement = fixture.settlementAccount();
    Account customer = fixture.emptyCustomerAccount();

    ledger.record(settlement.id(), customer.id(), Money.of("500.00"));

    assertThat(balanceOf(settlement)).isEqualTo(Money.of("-500.00"));
    assertThat(balanceOf(customer)).isEqualTo(Money.of("500.00"));
    assertThat(ledger.trialBalance().isConsistent()).isTrue();
  }

  @Test
  @DisplayName("lets a customer account be drained to exactly zero")
  void allowsDrainingToZero() {
    Account from = fixture.customerAccountHolding("42.00");
    Account to = fixture.emptyCustomerAccount();

    ledger.record(from.id(), to.id(), Money.of("42.00"));

    assertThat(balanceOf(from)).isEqualTo(Money.zero());
    assertThat(ledger.trialBalance().isConsistent()).isTrue();
  }

  private Money balanceOf(Account account) {
    return ledger.balanceOf(account.id());
  }
}
