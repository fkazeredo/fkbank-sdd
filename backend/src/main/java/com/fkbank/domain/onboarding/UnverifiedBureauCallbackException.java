package com.fkbank.domain.onboarding;

/**
 * A callback arrived without a signature the bureau could have produced.
 *
 * <p>Raised before the body is interpreted, so nothing an unverified caller sent is ever acted
 * on. The message is deliberately incurious: telling an unauthenticated caller why their
 * signature failed helps them get it right next time.
 */
public class UnverifiedBureauCallbackException extends OnboardingException {

  private static final long serialVersionUID = 1L;

  /** The stable code carried to the edge. */
  public static final String CODE = "UNVERIFIED_BUREAU_CALLBACK";

  public UnverifiedBureauCallbackException() {
    super(CODE, "the callback signature could not be verified");
  }
}
