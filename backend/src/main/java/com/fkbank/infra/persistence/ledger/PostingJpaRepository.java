package com.fkbank.infra.persistence.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PostingJpaRepository extends JpaRepository<PostingEntity, UUID> {

  boolean existsByReversesPostingId(UUID reversesPostingId);

  /**
   * One account's statement, keyset-paginated, each row carrying the account's running balance.
   *
   * <p>The window function summing {@code signed_amount} runs over the {@code legs} CTE, which
   * holds every posting the account was ever a leg of, unfiltered by period, direction or
   * cursor — those are applied only in the outer query, so a filter or a page boundary can never
   * change the running balance a row reports. {@code direction} and the cursor columns are
   * compared against sentinel values ({@code 'BOTH'}, a far-future instant, a nil UUID) rather
   * than {@code NULL}, because Postgres cannot infer a bind parameter's type from a clause where
   * the only thing done with it is an {@code IS NULL} check.
   *
   * <p>Column order in the result row: {@code posting_id, occurred_at, amount, currency,
   * debit_account_id, credit_account_id, reverses_posting_id, direction, running_balance}.
   */
  @Query(
      value =
          """
          WITH legs AS (
              SELECT p.id AS posting_id,
                     p.occurred_at,
                     p.amount,
                     p.currency,
                     p.debit_account_id,
                     p.credit_account_id,
                     p.reverses_posting_id,
                     CASE WHEN p.debit_account_id = :accountId THEN 'DEBIT' ELSE 'CREDIT' END
                         AS direction,
                     CASE WHEN p.debit_account_id = :accountId THEN -p.amount ELSE p.amount END
                         AS signed_amount
              FROM posting p
              WHERE p.debit_account_id = :accountId OR p.credit_account_id = :accountId
          ),
          running AS (
              SELECT legs.*,
                     SUM(signed_amount) OVER (ORDER BY occurred_at ASC, posting_id ASC)
                         AS running_balance
              FROM legs
          )
          SELECT posting_id, occurred_at, amount, currency, debit_account_id, credit_account_id,
                 reverses_posting_id, direction, running_balance
          FROM running
          WHERE occurred_at >= :from
            AND occurred_at < :to
            AND (:direction = 'BOTH' OR direction = :direction)
            AND (occurred_at, posting_id) < (:cursorOccurredAt, :cursorPostingId)
          ORDER BY occurred_at DESC, posting_id DESC
          LIMIT :pageLimit
          """,
      nativeQuery = true)
  List<Object[]> statementOf(
      @Param("accountId") long accountId,
      @Param("from") Instant from,
      @Param("to") Instant to,
      @Param("direction") String direction,
      @Param("cursorOccurredAt") Instant cursorOccurredAt,
      @Param("cursorPostingId") UUID cursorPostingId,
      @Param("pageLimit") int pageLimit);

  /**
   * Everything ever taken out of any account.
   *
   * <p>Both totals sum the same column, and that is not an oversight to be dressed up: a posting
   * carries one amount that is debited from one account and credited to another, so with this
   * schema debits equal credits by construction and {@code isBalanced()} cannot report otherwise.
   * The comparison is kept because the day a posting grows a third leg — a fee, a split
   * settlement — is the day it stops being free, and a check that was already there fails loudly
   * instead of being remembered.
   *
   * <p>What actually catches a corrupt ledger today is the per-account comparison in
   * {@link #netAmountByAccount()}: a tampered balance leaves debits equal to credits while the
   * account itself no longer matches its own postings.
   */
  @Query("select coalesce(sum(p.amount), 0) from PostingEntity p")
  BigDecimal sumOfDebitLegs();

  /** Everything ever put into any account. See {@link #sumOfDebitLegs()}. */
  @Query("select coalesce(sum(p.amount), 0) from PostingEntity p")
  BigDecimal sumOfCreditLegs();

  /**
   * Net movement per account computed from the postings alone: credits minus debits.
   *
   * <p>Deliberately reads only the posting table. A check that consulted the balances it is meant
   * to be checking would agree with them by construction and detect nothing.
   */
  @Query(
      value =
          """
          SELECT account_id, SUM(net) AS net FROM (
              SELECT credit_account_id AS account_id,  amount AS net FROM posting
              UNION ALL
              SELECT debit_account_id  AS account_id, -amount AS net FROM posting
          ) legs GROUP BY account_id
          """,
      nativeQuery = true)
  List<Object[]> netAmountByAccount();
}
