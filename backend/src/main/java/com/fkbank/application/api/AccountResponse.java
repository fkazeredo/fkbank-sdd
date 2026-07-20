package com.fkbank.application.api;

import com.fkbank.domain.account.AccountSummary;

/**
 * The account details and balance shown on the home screen.
 *
 * <p>The balance travels as text rather than as a JSON number. A number in JSON is a binary
 * floating-point value in most clients, and a balance that arrives as {@code 0.1 + 0.2} is a
 * balance nobody can trust; sending the exact decimal leaves the client with nothing to round.
 */
public record AccountResponse(String branch, String number, String balance, String currency) {

  static AccountResponse of(AccountSummary summary) {
    return new AccountResponse(
        summary.number().branch(),
        summary.number().number(),
        summary.balance().toPlainString(),
        summary.currency());
  }
}
