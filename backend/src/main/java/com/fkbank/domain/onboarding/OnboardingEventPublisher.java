package com.fkbank.domain.onboarding;

/** Announces a successful application, keeping the framework's publisher out of the domain. */
public interface OnboardingEventPublisher {

  void publish(OnboardingApproved event);
}
