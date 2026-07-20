package com.fkbank.domain.account;

import java.util.UUID;

/** Identifies a current account. */
public record CurrentAccountId(UUID value) {

  public CurrentAccountId {
    if (value == null) {
      throw new IllegalArgumentException("current account id must not be null");
    }
  }

  public static CurrentAccountId next() {
    return new CurrentAccountId(UUID.randomUUID());
  }

  public static CurrentAccountId of(UUID value) {
    return new CurrentAccountId(value);
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
