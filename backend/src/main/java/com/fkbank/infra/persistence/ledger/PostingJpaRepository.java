package com.fkbank.infra.persistence.ledger;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface PostingJpaRepository extends JpaRepository<PostingEntity, UUID> {

  boolean existsByReversesPostingId(UUID reversesPostingId);

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
