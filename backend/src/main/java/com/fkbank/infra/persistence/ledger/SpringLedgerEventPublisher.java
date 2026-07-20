package com.fkbank.infra.persistence.ledger;

import com.fkbank.domain.ledger.LedgerEventPublisher;
import com.fkbank.domain.ledger.PostingRecorded;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publishes ledger facts through the application's event machinery.
 *
 * <p>Publication happens inside the transaction that recorded the posting, so an event announcing
 * money that was ultimately rolled back is never seen.
 */
@Component
class SpringLedgerEventPublisher implements LedgerEventPublisher {

  private final ApplicationEventPublisher publisher;

  SpringLedgerEventPublisher(ApplicationEventPublisher publisher) {
    this.publisher = publisher;
  }

  @Override
  public void publish(PostingRecorded event) {
    publisher.publishEvent(event);
  }
}
