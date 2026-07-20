package com.fkbank.domain.onboarding;

/** No application exists with the given identifier. */
public class UnknownOnboardingException extends OnboardingException {

  private static final long serialVersionUID = 1L;

  /** The stable code carried to the edge. */
  public static final String CODE = "UNKNOWN_ONBOARDING";

  public UnknownOnboardingException(OnboardingId id) {
    super(CODE, "no onboarding " + id);
  }
}
