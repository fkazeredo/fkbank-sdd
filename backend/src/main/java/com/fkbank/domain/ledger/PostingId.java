package com.fkbank.domain.ledger;

import java.util.Objects;
import java.util.UUID;

/** Identifies a posting. */
public record PostingId(UUID value) {

  public PostingId {
    Objects.requireNonNull(value, "posting id must not be null");
  }

  public static PostingId of(UUID value) {
    return new PostingId(value);
  }

  /** Mints an identity for a posting about to be recorded. */
  public static PostingId next() {
    return new PostingId(UUID.randomUUID());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
