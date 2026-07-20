package com.fkbank.domain.ledger;

/**
 * What an account is for, and therefore whether it may hold a negative balance.
 *
 * <p>A customer account holding less than zero means the bank handed out money nobody had — the
 * error the ledger exists to make impossible. An internal account going negative is ordinary
 * double-entry bookkeeping: when a customer is paid out, the settlement account it came from is
 * legitimately short until the counterparty settles.
 *
 * <p>A closed set fixed by the chart of accounts, so an enum rather than seeded reference data.
 */
public enum AccountKind {

  /** A customer's spendable balance. */
  CUSTOMER_AVAILABLE(false),

  /** Money a customer has set aside in a yield box. */
  CUSTOMER_BOX(false),

  /** Funds in transit to or from an external rail. */
  INTERNAL_SETTLEMENT(true),

  /** What the bank pays out, such as yield credited to boxes. */
  INTERNAL_EXPENSE(true),

  /** Credit extended to customers and the receivables it creates. */
  INTERNAL_CREDIT(true);

  private final boolean allowsNegativeBalance;

  AccountKind(boolean allowsNegativeBalance) {
    this.allowsNegativeBalance = allowsNegativeBalance;
  }

  public boolean allowsNegativeBalance() {
    return allowsNegativeBalance;
  }

  public boolean isCustomerAccount() {
    return !allowsNegativeBalance;
  }
}
