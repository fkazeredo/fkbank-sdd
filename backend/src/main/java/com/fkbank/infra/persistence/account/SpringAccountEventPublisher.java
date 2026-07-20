package com.fkbank.infra.persistence.account;

import com.fkbank.domain.account.AccountEventPublisher;
import com.fkbank.domain.account.AccountOpened;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publishes account facts through the framework.
 *
 * <p>Published inside the transaction that opened the account, so the fact and the account it
 * announces commit together: an event registry holding an announcement of something that was
 * rolled back is worse than no registry at all.
 */
@Component
class SpringAccountEventPublisher implements AccountEventPublisher {

  private final ApplicationEventPublisher publisher;

  SpringAccountEventPublisher(ApplicationEventPublisher publisher) {
    this.publisher = publisher;
  }

  @Override
  public void publish(AccountOpened event) {
    publisher.publishEvent(event);
  }
}
