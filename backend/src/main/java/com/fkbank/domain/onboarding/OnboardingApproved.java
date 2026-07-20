package com.fkbank.domain.onboarding;

import java.util.UUID;

/**
 * An application succeeded and the person is now a customer.
 *
 * <p>Carries plain values rather than domain types: the fact is stored in the event registry and
 * read by consumers in other contexts, and a stored event that depends on a class keeps working
 * only for as long as nobody changes that class.
 *
 * <p>It deliberately carries no CPF, e-mail or name. A consumer that needs registration data can
 * ask the customer context for it; an event that is kept indefinitely should not be a second
 * copy of someone's personal details.
 */
public record OnboardingApproved(UUID onboardingId, UUID customerId) {

  static OnboardingApproved from(Onboarding onboarding) {
    return new OnboardingApproved(
        onboarding.id().value(),
        onboarding
            .customerId()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "an approved onboarding must name the customer it created"))
            .value());
  }
}
