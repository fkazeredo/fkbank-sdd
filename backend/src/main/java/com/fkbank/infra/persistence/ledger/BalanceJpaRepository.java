package com.fkbank.infra.persistence.ledger;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface BalanceJpaRepository extends JpaRepository<BalanceEntity, Long> {

  /**
   * Reads a balance and holds the row until the transaction ends.
   *
   * <p>Issues {@code SELECT ... FOR UPDATE}. A second transaction asking for the same row waits
   * here, and when it is let through it reads the value the first one committed rather than the
   * one it saw before waiting — which is the whole reason the read happens after the lock rather
   * than before it.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from BalanceEntity b where b.accountId = :accountId")
  Optional<BalanceEntity> lockByAccountId(@Param("accountId") Long accountId);
}
