package com.fkbank.domain.ledger;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One movement of money: the same amount leaving one account and arriving in another.
 *
 * <p>Immutable once created. Nothing in the product updates or deletes a posting — a mistake is
 * corrected by recording a contra-posting that points back at the original, so the books show
 * both what was believed at the time and what was done about it.
 *
 * <p>A regular class rather than a record: this is an entity with an identity and a lifecycle,
 * and its factories are where a movement that would not make sense is refused.
 */
public final class Posting {

  private final PostingId id;
  private final AccountId debitAccountId;
  private final AccountId creditAccountId;
  private final Money amount;
  private final Instant occurredAt;
  private final PostingId reverses;

  private Posting(
      PostingId id,
      AccountId debitAccountId,
      AccountId creditAccountId,
      Money amount,
      Instant occurredAt,
      PostingId reverses) {
    this.id = Objects.requireNonNull(id, "posting id must not be null");
    this.debitAccountId = Objects.requireNonNull(debitAccountId, "debit account must not be null");
    this.creditAccountId =
        Objects.requireNonNull(creditAccountId, "credit account must not be null");
    this.amount = Objects.requireNonNull(amount, "amount must not be null");
    this.occurredAt = Objects.requireNonNull(occurredAt, "occurrence instant must not be null");
    this.reverses = reverses;

    if (!amount.isPositive()) {
      throw new IllegalArgumentException(
          "a posting moves a positive amount, was " + amount);
    }
    if (debitAccountId.equals(creditAccountId)) {
      throw new IllegalArgumentException(
          "a posting moves money between two accounts, not from account "
              + debitAccountId
              + " to itself");
    }
  }

  /** Records money moving out of {@code debit} and into {@code credit}. */
  public static Posting of(
      PostingId id, AccountId debit, AccountId credit, Money amount, Instant occurredAt) {
    return new Posting(id, debit, credit, amount, occurredAt, null);
  }

  /**
   * Rebuilds a posting that already exists.
   *
   * @param reverses the posting this one corrects, or {@code null} for an original movement
   */
  public static Posting existing(
      PostingId id,
      AccountId debit,
      AccountId credit,
      Money amount,
      Instant occurredAt,
      PostingId reverses) {
    return new Posting(id, debit, credit, amount, occurredAt, reverses);
  }

  /**
   * Builds the contra-posting that undoes {@code original}: the same amount travelling the other
   * way, carrying a reference back to what it corrects.
   *
   * @throws ReversalNotAllowedException if {@code original} is itself a reversal
   */
  public static Posting reverseOf(PostingId id, Posting original, Instant occurredAt) {
    Objects.requireNonNull(original, "original posting must not be null");
    if (original.isReversal()) {
      throw ReversalNotAllowedException.isItselfAReversal(original.id());
    }
    return new Posting(
        id,
        original.creditAccountId,
        original.debitAccountId,
        original.amount,
        occurredAt,
        original.id);
  }

  public PostingId id() {
    return id;
  }

  public AccountId debitAccountId() {
    return debitAccountId;
  }

  public AccountId creditAccountId() {
    return creditAccountId;
  }

  public Money amount() {
    return amount;
  }

  public Instant occurredAt() {
    return occurredAt;
  }

  /** The posting this one corrects, when it is a contra-posting. */
  public Optional<PostingId> reverses() {
    return Optional.ofNullable(reverses);
  }

  public boolean isReversal() {
    return reverses != null;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Posting posting && id.equals(posting.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    String suffix = isReversal() ? " (reverses " + reverses + ")" : "";
    return "%s: %s -> %s%s".formatted(amount, debitAccountId, creditAccountId, suffix);
  }
}
