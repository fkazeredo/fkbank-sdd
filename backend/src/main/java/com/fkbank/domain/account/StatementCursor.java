package com.fkbank.domain.account;

import com.fkbank.domain.ledger.PostingId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Anchors a statement page to the last line the caller already saw.
 *
 * <p>Encodes to an opaque string a client passes back unexamined. A row inserted anywhere in the
 * account's history cannot move what an already-issued cursor points at, which is what keeps
 * paging stable while postings are concurrently recorded (BR-3).
 */
public record StatementCursor(Instant occurredAt, PostingId postingId) {

  private static final char FIELD_SEPARATOR = '|';

  public StatementCursor {
    Objects.requireNonNull(occurredAt, "occurred at must not be null");
    Objects.requireNonNull(postingId, "posting id must not be null");
  }

  public String encode() {
    String raw = occurredAt + String.valueOf(FIELD_SEPARATOR) + postingId.value();
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Rebuilds a cursor a client sent back.
   *
   * @throws IllegalArgumentException if it is not a cursor this API ever issued — the same
   *     submission failure every other malformed request produces, not a special case
   */
  public static StatementCursor decode(String cursor) {
    try {
      String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      int separator = raw.indexOf(FIELD_SEPARATOR);
      if (separator < 0) {
        throw new IllegalArgumentException("cursor has no field separator");
      }
      Instant occurredAt = Instant.parse(raw.substring(0, separator));
      PostingId postingId = PostingId.of(UUID.fromString(raw.substring(separator + 1)));
      return new StatementCursor(occurredAt, postingId);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("malformed statement cursor", e);
    }
  }
}
