package com.fkbank.domain.onboarding;

/**
 * The application had already been approved or refused.
 *
 * <p>Raised by the aggregate rather than checked by callers, so that no path can settle an
 * application twice — including a bureau that delivers the same answer more than once.
 */
public class OnboardingAlreadySettledException extends OnboardingException {

  private static final long serialVersionUID = 1L;

  /** The stable code carried to the edge. */
  public static final String CODE = "ONBOARDING_ALREADY_SETTLED";

  public OnboardingAlreadySettledException(OnboardingId id, OnboardingStatus status) {
    super(CODE, "onboarding " + id + " is already " + status);
  }
}
