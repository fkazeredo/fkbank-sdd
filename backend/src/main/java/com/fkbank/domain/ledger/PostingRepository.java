package com.fkbank.domain.ledger;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Stores postings and answers the questions the ledger asks of its own history. */
public interface PostingRepository {

  Posting save(Posting posting);

  Optional<Posting> findById(PostingId id);

  /** Whether a contra-posting already points at {@code id}. */
  boolean existsReversalOf(PostingId id);

  /**
   * One account's statement: every posting where it was a leg, within {@code [from, to)},
   * newest first, each carrying the account's running balance as of that line.
   *
   * <p>The running balance is computed over the account's entire history, before the period,
   * direction or page window is applied — a filter or a page boundary changes which lines are
   * shown, never the balance value a shown line reports.
   *
   * <p>Paginated by keyset, not offset: {@code cursorOccurredAt}/{@code cursorPostingId} name the
   * last line already returned (both {@code null} for the first page), and the next page starts
   * strictly after it in the newest-first order. A row inserted anywhere in the account's history
   * cannot shift what a cursor already anchored to a specific row points at, which is what keeps
   * paging stable while postings are concurrently recorded.
   *
   * @param direction restricts to the account's debit or credit legs; {@code null} for both
   * @param cursorOccurredAt the last-seen line's occurrence instant, or {@code null} for the
   *     first page
   * @param cursorPostingId the last-seen line's posting id, or {@code null} for the first page
   * @param limit the page size
   */
  List<PostingLine> statementOf(
      AccountId accountId,
      Instant from,
      Instant to,
      Direction direction,
      Instant cursorOccurredAt,
      PostingId cursorPostingId,
      int limit);

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
