package com.fkbank.domain.ledger;

/** No account exists with the given identity. */
public class UnknownAccountException extends LedgerException {

  public static final String CODE = "UNKNOWN_ACCOUNT";

  private final AccountId accountId;

  public UnknownAccountException(AccountId accountId) {
    super(CODE, "no account %s".formatted(accountId));
    this.accountId = accountId;
  }

  public AccountId accountId() {
    return accountId;
  }
}
