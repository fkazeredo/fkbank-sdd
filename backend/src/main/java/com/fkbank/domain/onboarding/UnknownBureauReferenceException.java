package com.fkbank.domain.onboarding;

/**
 * A callback named an application that does not exist.
 *
 * <p>The message says nothing about which part was wrong. A caller who has cleared the signature
 * check but named an unknown application is either a bureau with stale state or somebody
 * guessing, and neither should be told which.
 */
public class UnknownBureauReferenceException extends OnboardingException {

  private static final long serialVersionUID = 1L;

  /** The stable code carried to the edge. */
  public static final String CODE = "UNKNOWN_ONBOARDING";

  public UnknownBureauReferenceException() {
    super(CODE, "no onboarding matches the reference in this callback");
  }
}
