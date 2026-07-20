package com.fkbank.domain.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Announces that money moved.
 *
 * <p>Carries plain values rather than the ledger's own types because it leaves the bounded
 * context and is serialized into the publication registry: consumers should be able to read it
 * without compiling against the ledger, and a stored event should still be readable after the
 * model that produced it has moved on.
 *
 * @param reversesPostingId the posting this movement corrects, or {@code null} for an original
 *     movement
 */
public record PostingRecorded(
    UUID postingId,
    long debitAccountId,
    long creditAccountId,
    BigDecimal amount,
    String currency,
    Instant occurredAt,
    UUID reversesPostingId) {

  static PostingRecorded from(Posting posting) {
    return new PostingRecorded(
        posting.id().value(),
        posting.debitAccountId().value(),
        posting.creditAccountId().value(),
        posting.amount().amount(),
        posting.amount().currency().getCurrencyCode(),
        posting.occurredAt(),
        posting.reverses().map(PostingId::value).orElse(null));
  }

  public boolean isReversal() {
    return reversesPostingId != null;
  }
}
