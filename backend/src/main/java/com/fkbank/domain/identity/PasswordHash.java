package com.fkbank.domain.identity;

/**
 * The stored form of a password.
 *
 * <p>Opaque on purpose: the domain treats it as a value it moves around and stores, and knows
 * nothing about which algorithm produced it. The encoding carries its own algorithm marker, so
 * adopting a stronger one later re-hashes on next use instead of locking everyone out.
 */
public record PasswordHash(String value) {

  public PasswordHash {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("password hash must not be blank");
    }
  }

  public static PasswordHash of(String value) {
    return new PasswordHash(value);
  }

  /** Masked: a hash is not a password, but it is still not something to print. */
  @Override
  public String toString() {
    return "PasswordHash[protected]";
  }
}
