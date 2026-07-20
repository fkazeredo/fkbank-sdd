package com.fkbank.infra.observability;

import com.fkbank.domain.ledger.PostingRecorded;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Counts money movements as they are recorded.
 *
 * <p>Deliberately counts postings rather than requests: a rail that retries, replays or reverses
 * shows up here as the movements it actually caused, which is the number an operator needs when
 * a dashboard disagrees with a customer.
 *
 * <p>Reversals are counted separately from original movements, because a sudden rise in
 * corrections is a signal in its own right.
 */
@Component
class LedgerMetrics {

  private final Counter postings;
  private final Counter reversals;

  LedgerMetrics(MeterRegistry registry) {
    this.postings =
        Counter.builder("fkbank.ledger.postings")
            .description("Money movements recorded in the ledger")
            .tag("kind", "posting")
            .register(registry);
    this.reversals =
        Counter.builder("fkbank.ledger.postings")
            .description("Money movements recorded in the ledger")
            .tag("kind", "reversal")
            .register(registry);
  }

  /**
   * Counted only once the transaction commits.
   *
   * <p>A posting that is rolled back never happened, and a counter that included it would report
   * movements the ledger has no record of — the kind of discrepancy that costs hours when a
   * dashboard and a statement disagree.
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  void onPostingRecorded(PostingRecorded event) {
    if (event.isReversal()) {
      reversals.increment();
    } else {
      postings.increment();
    }
  }
}
