package com.fkbank.infra.persistence.ledger;

import com.fkbank.domain.ledger.AccountId;
import com.fkbank.domain.ledger.Money;
import com.fkbank.domain.ledger.Posting;
import com.fkbank.domain.ledger.PostingId;
import com.fkbank.domain.ledger.PostingRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Stores postings and answers the ledger's questions about its own history. */
@Repository
class JpaPostingRepository implements PostingRepository {

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
