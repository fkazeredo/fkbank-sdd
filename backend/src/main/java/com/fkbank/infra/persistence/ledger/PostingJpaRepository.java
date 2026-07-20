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
   * <p>Every posting has exactly one debit leg carrying the whole amount, so this sums the amount
   * column. The credit total does the same for the other leg: with this schema the two are equal
   * by construction, and the trial balance compares them anyway so that a future posting shape
   * with more than two legs cannot break double entry unnoticed.
   */
  @Query("select coalesce(sum(p.amount), 0) from PostingEntity p")
  BigDecimal sumOfDebitLegs();

  /** Everything ever put into any account. */
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
