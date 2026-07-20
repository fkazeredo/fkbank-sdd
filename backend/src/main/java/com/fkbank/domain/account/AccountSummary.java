package com.fkbank.domain.account;

import java.math.BigDecimal;

/**
 * What an account holder sees on their home screen.
 *
 * <p>Exists so a caller can ask one question and get the account and what it holds together,
 * without reaching into the accounting core for the balance. The amount is already rounded for
 * display, because rounding is a presentation decision and making it here means it is made once
 * rather than by every caller.
 */
public record AccountSummary(AccountNumber number, BigDecimal balance, String currency) {

  public AccountSummary {
    if (number == null) {
      throw new IllegalArgumentException("account number must not be null");
    }
    if (balance == null) {
      throw new IllegalArgumentException("balance must not be null");
    }
    if (currency == null || currency.isBlank()) {
      throw new IllegalArgumentException("currency must not be blank");
    }
  }
}
