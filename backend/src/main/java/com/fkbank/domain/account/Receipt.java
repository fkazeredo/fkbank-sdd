package com.fkbank.domain.account;

import com.fkbank.domain.ledger.Direction;
import com.fkbank.domain.ledger.Money;
import com.fkbank.domain.ledger.PostingId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Public proof of one completed movement, from one of its two parties' own perspective.
 *
 * <p>{@code counterparty} is the other party's masked CPF when the other leg is a customer's own
 * available balance — a peer transfer — and empty otherwise, since the receipt's {@code rail}
 * already names the channel when the other leg is an internal settlement, expense or credit
 * account.
 */
public record Receipt(
    PostingId id,
    Instant occurredAt,
    Money amount,
    Direction direction,
    Rail rail,
    ReceiptStatus status,
    Optional<String> counterparty) {

  public Receipt {
    Objects.requireNonNull(id, "posting id must not be null");
    Objects.requireNonNull(occurredAt, "occurred at must not be null");
    Objects.requireNonNull(amount, "amount must not be null");
    Objects.requireNonNull(direction, "direction must not be null");
    Objects.requireNonNull(rail, "rail must not be null");
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(counterparty, "counterparty must not be null");
  }
}
