package com.fkbank.domain.ledger;

import java.util.Optional;

/** No account exists with the given identity or code. */
public class UnknownAccountException extends LedgerException {

  public static final String CODE = "UNKNOWN_ACCOUNT";

  private final AccountId accountId;

  public UnknownAccountException(AccountId accountId) {
    super(CODE, "no account %s".formatted(accountId));
    this.accountId = accountId;
  }

  private UnknownAccountException(String accountCode) {
    super(CODE, "no account with code %s".formatted(accountCode));
    this.accountId = null;
  }

  /** Nothing in the chart of accounts is registered under the given code. */
  public static UnknownAccountException withCode(String accountCode) {
    return new UnknownAccountException(accountCode);
  }

  /**
   * The identity that was not found.
   *
   * <p>Absent when the account was looked up by its code, because then there is no identity to
   * report — the whole point of the failure is that nothing was found to have one.
   */
  public Optional<AccountId> accountId() {
    return Optional.ofNullable(accountId);
  }
}
