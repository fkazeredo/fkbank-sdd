package com.fkbank.domain.onboarding;

/**
 * An application for this CPF is already waiting on the bureau.
 *
 * <p>Raised by the store when two applications for the same person arrive together and the
 * second one loses. It is not an error the applicant ever sees: the caller answers it by
 * fetching the application that won and returning that, so both people see one application
 * rather than one of them seeing a failure.
 */
public class OnboardingAlreadyPendingException extends OnboardingException {

  private static final long serialVersionUID = 1L;

  /** The stable code carried to the edge, on the paths where it is not handled earlier. */
  public static final String CODE = "ONBOARDING_ALREADY_PENDING";

  public OnboardingAlreadyPendingException(String message, Throwable cause) {
    super(CODE, message);
    initCause(cause);
  }
}
