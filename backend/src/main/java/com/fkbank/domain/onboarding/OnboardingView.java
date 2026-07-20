package com.fkbank.domain.onboarding;

/**
 * What an applicant is told about their application.
 *
 * <p>A projection carrying only the identifier, the outcome, and — when refused — the category
 * behind it. Everything else the application holds is either the applicant's own submission,
 * which they already have, or the bureau's answer, which is not ours to pass on.
 */
public record OnboardingView(
    OnboardingId id, OnboardingStatus status, RejectionReason reason) {

  static OnboardingView of(Onboarding onboarding) {
    return new OnboardingView(
        onboarding.id(), onboarding.status(), onboarding.reason().orElse(null));
  }
}
