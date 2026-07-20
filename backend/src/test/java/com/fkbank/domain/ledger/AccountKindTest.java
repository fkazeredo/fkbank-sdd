package com.fkbank.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("AccountKind")
class AccountKindTest {

  @ParameterizedTest
  @EnumSource(value = AccountKind.class, names = {"CUSTOMER_AVAILABLE", "CUSTOMER_BOX"})
  @DisplayName("a customer account may never go below zero")
  void customerAccountsMayNotGoNegative(AccountKind kind) {
    assertThat(kind.allowsNegativeBalance()).isFalse();
    assertThat(kind.isCustomerAccount()).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = AccountKind.class,
      names = {"INTERNAL_SETTLEMENT", "INTERNAL_EXPENSE", "INTERNAL_CREDIT"})
  @DisplayName("an internal account may go below zero")
  void internalAccountsMayGoNegative(AccountKind kind) {
    assertThat(kind.allowsNegativeBalance()).isTrue();
    assertThat(kind.isCustomerAccount()).isFalse();
  }

  @Test
  @DisplayName("every kind is either a customer account or an internal one")
  void everyKindIsClassified() {
    assertThat(AccountKind.values())
        .allSatisfy(kind ->
            assertThat(kind.isCustomerAccount()).isNotEqualTo(kind.allowsNegativeBalance()));
  }
}
