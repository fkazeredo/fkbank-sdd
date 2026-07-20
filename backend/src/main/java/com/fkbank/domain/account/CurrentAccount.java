package com.fkbank.domain.account;

import com.fkbank.domain.customer.CustomerId;
import java.time.Instant;
import java.util.Objects;

/**
 * The account a customer holds.
 *
 * <p>Named for what a person owns, to keep it distinct from an account in the chart of
 * accounts: this is the product, and an entry in the chart is where its money is recorded.
 *
 * <p>The account holds no reference to its ledger account. The code is derived from the
 * customer it belongs to, so the two can never disagree about which balance is whose — a stored
 * pointer is one more thing that can be wrong about where someone's money is.
 */
public final class CurrentAccount {

  private static final String LEDGER_ACCOUNT_PREFIX = "customer:available:";

  private final CurrentAccountId id;
  private final CustomerId customerId;
  private final AccountNumber number;
  private final Instant openedAt;

  private CurrentAccount(
      CurrentAccountId id, CustomerId customerId, AccountNumber number, Instant openedAt) {
    this.id = Objects.requireNonNull(id, "current account id must not be null");
    this.customerId = Objects.requireNonNull(customerId, "customer id must not be null");
    this.number = Objects.requireNonNull(number, "account number must not be null");
    this.openedAt = Objects.requireNonNull(openedAt, "opened at must not be null");
  }

  /** Opens an account for a customer at the number they were allocated. */
  public static CurrentAccount openFor(
      CurrentAccountId id, CustomerId customerId, AccountNumber number, Instant openedAt) {
    return new CurrentAccount(id, customerId, number, openedAt);
  }

  /** Rebuilds an account that already exists. */
  public static CurrentAccount existing(
      CurrentAccountId id, CustomerId customerId, AccountNumber number, Instant openedAt) {
    return new CurrentAccount(id, customerId, number, openedAt);
  }

  /**
   * Where this account's money is recorded in the chart of accounts.
   *
   * <p>Derived from the customer rather than stored, so the account and its balance cannot drift
   * apart.
   */
  public String ledgerAccountCode() {
    return ledgerAccountCodeFor(customerId);
  }

  /** The chart-of-accounts code that holds a given customer's spendable balance. */
  public static String ledgerAccountCodeFor(CustomerId customerId) {
    Objects.requireNonNull(customerId, "customer id must not be null");
    return LEDGER_ACCOUNT_PREFIX + customerId.value();
  }

  public CurrentAccountId id() {
    return id;
  }

  public CustomerId customerId() {
    return customerId;
  }

  public AccountNumber number() {
    return number;
  }

  public Instant openedAt() {
    return openedAt;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof CurrentAccount account && id.equals(account.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "CurrentAccount[" + number + "]";
  }
}
