package com.fkbank.domain.ledger;

import java.util.Objects;

/**
 * An account in the chart of accounts.
 *
 * <p>Identified to people by a stable code such as {@code internal:settlement:pix} or
 * {@code customer:available:42}, and to the ledger by its numeric id, which also fixes the order
 * locks are taken in.
 */
public final class Account {

  private final AccountId id;
  private final AccountKind kind;
  private final String code;

  private Account(AccountId id, AccountKind kind, String code) {
    this.id = Objects.requireNonNull(id, "account id must not be null");
    this.kind = Objects.requireNonNull(kind, "account kind must not be null");
    this.code = requireCode(code);
  }

  /** Rebuilds an account that already exists. */
  public static Account existing(AccountId id, AccountKind kind, String code) {
    return new Account(id, kind, code);
  }

  public AccountId id() {
    return id;
  }

  public AccountKind kind() {
    return kind;
  }

  public String code() {
    return code;
  }

  public boolean allowsNegativeBalance() {
    return kind.allowsNegativeBalance();
  }

  public boolean isCustomerAccount() {
    return kind.isCustomerAccount();
  }

  private static String requireCode(String code) {
    Objects.requireNonNull(code, "account code must not be null");
    String trimmed = code.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("account code must not be blank");
    }
    return trimmed;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Account account && id.equals(account.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return code + " (#" + id + ")";
  }
}
