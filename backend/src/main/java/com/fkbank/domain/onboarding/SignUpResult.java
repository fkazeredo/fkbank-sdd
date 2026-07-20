package com.fkbank.domain.onboarding;

/**
 * The outcome of submitting a sign-up form.
 *
 * <p>{@code started} distinguishes an application this submission actually created from one
 * that was already under way. Resubmitting the same CPF while it is still being checked is not
 * an error and not a second application — the person gets the one they already have — and the
 * caller needs to be able to tell the two apart to answer honestly.
 */
public record SignUpResult(OnboardingView application, boolean started) {

  static SignUpResult started(Onboarding onboarding) {
    return new SignUpResult(OnboardingView.of(onboarding), true);
  }

  static SignUpResult started(OnboardingView application) {
    return new SignUpResult(application, true);
  }

  /** The application was already under way; nothing was created. */
  static SignUpResult alreadyUnderWay(Onboarding onboarding) {
    return new SignUpResult(OnboardingView.of(onboarding), false);
  }
}
