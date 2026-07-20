package com.fkbank.infra.persistence.onboarding;

import com.fkbank.domain.onboarding.OnboardingApproved;
import com.fkbank.domain.onboarding.OnboardingEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publishes onboarding facts through the framework.
 *
 * <p>Published inside the transaction that approved the application, so the fact and everything
 * the approval created commit together.
 */
@Component
class SpringOnboardingEventPublisher implements OnboardingEventPublisher {

  private final ApplicationEventPublisher publisher;

  SpringOnboardingEventPublisher(ApplicationEventPublisher publisher) {
    this.publisher = publisher;
  }

  @Override
  public void publish(OnboardingApproved event) {
    publisher.publishEvent(event);
  }
}
