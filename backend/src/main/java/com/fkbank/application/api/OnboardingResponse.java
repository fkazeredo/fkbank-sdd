package com.fkbank.application.api;

import com.fkbank.domain.onboarding.OnboardingView;

/**
 * What an applicant is told about their application.
 *
 * <p>Three fields and no more. The submission is theirs already, and the bureau's own answer
 * describes a person's record at a third party — a refusal message is not the place to disclose
 * it.
 */
public record OnboardingResponse(String onboardingId, String status, String reasonCategory) {

  static OnboardingResponse of(OnboardingView application) {
    return new OnboardingResponse(
        application.id().toString(),
        application.status().name(),
        application.reason() == null ? null : application.reason().name());
  }
}
