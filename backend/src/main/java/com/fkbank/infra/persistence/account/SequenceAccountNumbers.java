package com.fkbank.infra.persistence.account;

import com.fkbank.domain.account.AccountNumbers;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

/**
 * Hands out account numbers from a database sequence.
 *
 * <p>A sequence rather than a count of existing accounts: two accounts opened at the same
 * instant would both count the same total and both claim the same number. A sequence never
 * returns the same value twice, whatever else is happening, and it does so without taking a lock
 * that other openings would queue behind.
 */
@Component
class SequenceAccountNumbers implements AccountNumbers {

  private final EntityManager entityManager;

  SequenceAccountNumbers(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public long next() {
    return ((Number)
            entityManager
                .createNativeQuery("SELECT nextval('current_account_number_seq')")
                .getSingleResult())
        .longValue();
  }
}
