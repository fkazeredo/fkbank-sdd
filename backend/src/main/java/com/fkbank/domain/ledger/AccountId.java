package com.fkbank.domain.ledger;

/**
 * Identifies an account in the chart of accounts.
 *
 * <p>Comparable because the order matters: a posting locks the two accounts it touches in
 * ascending id order. Two transfers moving money in opposite directions between the same pair of
 * accounts would otherwise each hold the lock the other one needs, and deadlock.
 */
public record AccountId(long value) implements Comparable<AccountId> {

  public AccountId {
    if (value <= 0) {
      throw new IllegalArgumentException("account id must be positive, was " + value);
    }
  }

  public static AccountId of(long value) {
    return new AccountId(value);
  }

  @Override
  public int compareTo(AccountId other) {
    return Long.compare(value, other.value);
  }

  @Override
  public String toString() {
    return Long.toString(value);
  }
}
