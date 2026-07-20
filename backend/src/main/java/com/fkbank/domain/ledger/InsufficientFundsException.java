package com.fkbank.domain.ledger;

/**
 * The account cannot cover the debit, and no customer account is allowed to go below zero.
 *
 * <p>Raised before anything is written, so a caller that sees this can be certain the ledger is
 * exactly as it was.
 */
public class InsufficientFundsException extends LedgerException {

  public static final String CODE = "INSUFFICIENT_FUNDS";

  private final AccountId accountId;

  public InsufficientFundsException(AccountId accountId, Money available, Money requested) {
    super(
        CODE,
        "account %s holds %s and cannot cover %s".formatted(accountId, available, requested));
    this.accountId = accountId;
  }

  public AccountId accountId() {
    return accountId;
  }
}
