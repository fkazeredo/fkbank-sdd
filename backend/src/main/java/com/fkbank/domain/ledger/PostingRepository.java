package com.fkbank.domain.ledger;

import java.util.Map;
import java.util.Optional;

/** Stores postings and answers the questions the ledger asks of its own history. */
public interface PostingRepository {

  Posting save(Posting posting);

  Optional<Posting> findById(PostingId id);

  /** Whether a contra-posting already points at {@code id}. */
  boolean existsReversalOf(PostingId id);

  /**
   * Net movement per account, computed from the postings themselves: everything credited to an
   * account minus everything debited from it. This is what a saved balance is checked against, so
   * it must never be derived from the saved balances.
   */
  Map<AccountId, Money> netAmountByAccount();

  /** Everything ever taken out of any account. */
  Money totalDebits();

  /** Everything ever put into any account. */
  Money totalCredits();
}
