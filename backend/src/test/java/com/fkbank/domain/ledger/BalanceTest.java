package com.fkbank.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Balance")
class BalanceTest {

  private static final AccountId CUSTOMER = AccountId.of(1);
  private static final AccountId INTERNAL = AccountId.of(2);

  private static Balance customerHolding(String amount) {
    return Balance.existing(CUSTOMER, AccountKind.CUSTOMER_AVAILABLE, Money.of(amount));
  }

  private static Balance internalHolding(String amount) {
    return Balance.existing(INTERNAL, AccountKind.INTERNAL_SETTLEMENT, Money.of(amount));
  }

  @Test
  @DisplayName("opens at zero")
  void opensAtZero() {
    Account account = Account.existing(CUSTOMER, AccountKind.CUSTOMER_AVAILABLE, "customer:available:1");

    assertThat(Balance.opening(account).amount()).isEqualTo(Money.zero());
  }

  @Test
  @DisplayName("credits and debits move the amount")
  void creditsAndDebitsMoveTheAmount() {
    Balance balance = customerHolding("10.00");

    balance.credit(Money.of("5.00"));
    assertThat(balance.amount()).isEqualTo(Money.of("15.00"));

    balance.debit(Money.of("3.00"));
    assertThat(balance.amount()).isEqualTo(Money.of("12.00"));
  }

  @Test
  @DisplayName("lets a customer account be drained to exactly zero")
  void allowsDrainingToZero() {
    Balance balance = customerHolding("10.00");

    balance.debit(Money.of("10.00"));

    assertThat(balance.amount()).isEqualTo(Money.zero());
  }

  @Test
  @DisplayName("refuses to take a customer account below zero and leaves it untouched")
  void refusesToOverdrawACustomerAccount() {
    Balance balance = customerHolding("10.00");

    assertThatThrownBy(() -> balance.debit(Money.of("10.0001")))
        .isInstanceOf(InsufficientFundsException.class)
        .satisfies(thrown ->
            assertThat(((InsufficientFundsException) thrown).code())
                .isEqualTo("INSUFFICIENT_FUNDS"));

    assertThat(balance.amount())
        .as("a refused debit must leave the balance exactly as it was")
        .isEqualTo(Money.of("10.00"));
  }

  @Test
  @DisplayName("refuses to overdraw a box account too")
  void refusesToOverdrawABoxAccount() {
    Balance box = Balance.existing(CUSTOMER, AccountKind.CUSTOMER_BOX, Money.of("1.00"));

    assertThatThrownBy(() -> box.debit(Money.of("2.00")))
        .isInstanceOf(InsufficientFundsException.class);
  }

  @Test
  @DisplayName("lets an internal account go negative - money in transit is legitimately owed")
  void allowsInternalAccountsToGoNegative() {
    Balance balance = internalHolding("0.00");

    balance.debit(Money.of("50.00"));

    assertThat(balance.amount()).isEqualTo(Money.of("-50.00"));
    assertThat(balance.amount().isNegative()).isTrue();
  }

  @Test
  @DisplayName("refuses a non-positive movement in either direction")
  void refusesNonPositiveMovements() {
    Balance balance = customerHolding("10.00");

    assertThatThrownBy(() -> balance.debit(Money.zero()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> balance.credit(Money.zero()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> balance.debit(Money.of("-1.00")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> balance.credit(Money.of("-1.00")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("names the account that could not cover the debit")
  void namesTheShortAccount() {
    Balance balance = customerHolding("1.00");

    assertThatThrownBy(() -> balance.debit(Money.of("2.00")))
        .isInstanceOfSatisfying(
            InsufficientFundsException.class,
            thrown -> assertThat(thrown.accountId()).isEqualTo(CUSTOMER));
  }
}
