package com.fkbank.infra.persistence.ledger;

import com.fkbank.domain.ledger.AccountId;
import com.fkbank.domain.ledger.Direction;
import com.fkbank.domain.ledger.Money;
import com.fkbank.domain.ledger.Posting;
import com.fkbank.domain.ledger.PostingId;
import com.fkbank.domain.ledger.PostingLine;
import com.fkbank.domain.ledger.PostingRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Stores postings and answers the ledger's questions about its own history. */
@Repository
class JpaPostingRepository implements PostingRepository {

  /**
   * Stands in for "no cursor" in the native query: a value later than any real posting can carry,
   * so every row compares before it regardless of what the sentinel posting id is paired with.
   */
  private static final Instant NO_CURSOR_OCCURRED_AT = Instant.parse("9999-12-31T23:59:59Z");

  private static final UUID NO_CURSOR_POSTING_ID = new UUID(0L, 0L);

  private final PostingJpaRepository postings;

  JpaPostingRepository(PostingJpaRepository postings) {
    this.postings = postings;
  }

  @Override
  public Posting save(Posting posting) {
    postings.save(
        new PostingEntity(
            posting.id().value(),
            posting.debitAccountId().value(),
            posting.creditAccountId().value(),
            posting.amount().amount(),
            posting.amount().currency().getCurrencyCode(),
            posting.occurredAt(),
            posting.reverses().map(PostingId::value).orElse(null)));
    return posting;
  }

  @Override
  public Optional<Posting> findById(PostingId id) {
    return postings.findById(id.value()).map(JpaPostingRepository::toDomain);
  }

  @Override
  public boolean existsReversalOf(PostingId id) {
    return postings.existsByReversesPostingId(id.value());
  }

  @Override
  public Map<AccountId, Money> netAmountByAccount() {
    Map<AccountId, Money> net = new HashMap<>();
    for (Object[] row : postings.netAmountByAccount()) {
      net.put(
          AccountId.of(((Number) row[0]).longValue()), Money.of((BigDecimal) row[1]));
    }
    return net;
  }

  @Override
  public Money totalDebits() {
    return Money.of(postings.sumOfDebitLegs());
  }

  @Override
  public Money totalCredits() {
    return Money.of(postings.sumOfCreditLegs());
  }

  @Override
  public List<PostingLine> statementOf(
      AccountId accountId,
      Instant from,
      Instant to,
      Direction direction,
      Instant cursorOccurredAt,
      PostingId cursorPostingId,
      int limit) {
    List<Object[]> rows =
        postings.statementOf(
            accountId.value(),
            from,
            to,
            direction == null ? "BOTH" : direction.name(),
            cursorOccurredAt == null ? NO_CURSOR_OCCURRED_AT : cursorOccurredAt,
            cursorPostingId == null ? NO_CURSOR_POSTING_ID : cursorPostingId.value(),
            limit);

    List<PostingLine> lines = new ArrayList<>(rows.size());
    for (Object[] row : rows) {
      UUID reverses = (UUID) row[6];
      Posting posting =
          Posting.existing(
              PostingId.of((UUID) row[0]),
              AccountId.of(((Number) row[4]).longValue()),
              AccountId.of(((Number) row[5]).longValue()),
              Money.of((BigDecimal) row[2]),
              toInstant(row[1]),
              reverses == null ? null : PostingId.of(reverses));
      lines.add(
          new PostingLine(posting, Direction.valueOf((String) row[7]), Money.of((BigDecimal) row[8])));
    }
    return lines;
  }

  /**
   * A native query's timestamptz column may surface as {@link Timestamp} or {@link
   * OffsetDateTime} depending on the JDBC driver and Hibernate version in play; the shape used
   * elsewhere in this class comes from JPA-mapped entities, which always give an {@link Instant}
   * directly and never exercise this path.
   */
  private static Instant toInstant(Object value) {
    if (value instanceof Instant instant) {
      return instant;
    }
    if (value instanceof OffsetDateTime offsetDateTime) {
      return offsetDateTime.toInstant();
    }
    if (value instanceof Timestamp timestamp) {
      return timestamp.toInstant();
    }
    throw new IllegalStateException("unexpected occurred_at type: " + value.getClass());
  }

  private static Posting toDomain(PostingEntity entity) {
    UUID reverses = entity.getReversesPostingId();
    return Posting.existing(
        PostingId.of(entity.getId()),
        AccountId.of(entity.getDebitAccountId()),
        AccountId.of(entity.getCreditAccountId()),
        Money.of(entity.getAmount()),
        entity.getOccurredAt(),
        reverses == null ? null : PostingId.of(reverses));
  }
}
