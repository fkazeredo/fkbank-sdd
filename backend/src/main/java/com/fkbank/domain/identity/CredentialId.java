package com.fkbank.domain.identity;

import java.util.UUID;

/** Identifies a credential. */
public record CredentialId(UUID value) {

  public CredentialId {
    if (value == null) {
      throw new IllegalArgumentException("credential id must not be null");
    }
  }

  public static CredentialId next() {
    return new CredentialId(UUID.randomUUID());
  }

  public static CredentialId of(UUID value) {
    return new CredentialId(value);
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
