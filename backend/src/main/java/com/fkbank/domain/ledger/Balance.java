package com.fkbank.domain.ledger;

import java.util.Objects;

/**
 * What an account currently holds.
 *
 * <p>Kept alongside the postings rather than derived on every read, because recomputing a balance
 * from the whole history of an account gets slower every day. The saved figure is a convenience,
 * not the truth — the postings are the truth, and {@link Ledger#trialBalance()} exists to prove
 * the two still agree.
 *
 * <p>The never-below-zero rule lives here, on the thing it constrains, so no caller can move
 * money by assembling a balance field by field and forgetting to check.
 */
public final class Balance {

  private final AccountId accountId;
  private final AccountKind kind;
  private Money amount;

  private Balance(AccountId accountId, AccountKind kind, Money amount) {
    this.accountId = Objects.requireNonNull(accountId, "account id must not be null");
    this.kind = Objects.requireNonNull(kind, "account kind must not be null");
    this.amount = Objects.requireNonNull(amount, "amount must not be null");
  }

  /** The balance of a newly opened account. */
  public static Balance opening(Account account) {
    Objects.requireNonNull(account, "account must not be null");
    return new Balance(account.id(), account.kind(), Money.zero());
  }

  /** Rebuilds a balance that already exists. */
  public static Balance existing(AccountId accountId, AccountKind kind, Money amount) {
    return new Balance(accountId, kind, amount);
  }

  /**
   * Takes money out of the account.
   *
   * @throws InsufficientFundsException if the account would end up below zero and its kind does
   *     not tolerate that
   */
  public void debit(Money value) {
    requirePositive(value);
    Money next = amount.subtract(value);
    if (next.isNegative() && !kind.allowsNegativeBalance()) {
      throw new InsufficientFundsException(accountId, amount, value);
    }
    amount = next;
  }

  /** Puts money into the account. */
  public void credit(Money value) {
    requirePositive(value);
    amount = amount.add(value);
  }

  public AccountId accountId() {
    return accountId;
  }

  public AccountKind kind() {
    return kind;
  }

  public Money amount() {
    return amount;
  }

  private static void requirePositive(Money value) {
    Objects.requireNonNull(value, "amount must not be null");
    if (!value.isPositive()) {
      throw new IllegalArgumentException("a balance moves by a positive amount, was " + value);
    }
  }

  @Override
  public String toString() {
    return "balance of account " + accountId + ": " + amount;
  }
}
