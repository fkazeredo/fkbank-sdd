package com.fkbank.infra.persistence.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * How a posting is stored.
 *
 * <p>Carries no setters: the table rejects {@code UPDATE} outright, so a mutable mapping would
 * only offer a way to trigger a database error at flush time.
 */
@Entity
@Table(name = "posting")
class PostingEntity {

  @Id private UUID id;

  @Column(name = "debit_account_id", nullable = false, updatable = false)
  private Long debitAccountId;

  @Column(name = "credit_account_id", nullable = false, updatable = false)
  private Long creditAccountId;

  @Column(nullable = false, updatable = false)
  private BigDecimal amount;

  @Column(nullable = false, updatable = false, length = 3)
  private String currency;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  @Column(name = "reverses_posting_id", updatable = false)
  private UUID reversesPostingId;

  protected PostingEntity() {
    // for the persistence provider
  }

  PostingEntity(
      UUID id,
      Long debitAccountId,
      Long creditAccountId,
      BigDecimal amount,
      String currency,
      Instant occurredAt,
      UUID reversesPostingId) {
    this.id = id;
    this.debitAccountId = debitAccountId;
    this.creditAccountId = creditAccountId;
    this.amount = amount;
    this.currency = currency;
    this.occurredAt = occurredAt;
    this.reversesPostingId = reversesPostingId;
  }

  UUID getId() {
    return id;
  }

  Long getDebitAccountId() {
    return debitAccountId;
  }

  Long getCreditAccountId() {
    return creditAccountId;
  }

  BigDecimal getAmount() {
    return amount;
  }

  String getCurrency() {
    return currency;
  }

  Instant getOccurredAt() {
    return occurredAt;
  }

  UUID getReversesPostingId() {
    return reversesPostingId;
  }
}
