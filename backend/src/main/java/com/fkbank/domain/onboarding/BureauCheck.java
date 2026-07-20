package com.fkbank.domain.onboarding;

/**
 * Asks the credit bureau whether an applicant may open an account.
 *
 * <p>A port, so this context states what it needs — a decision — and knows nothing about how
 * the answer is fetched, how long it is worth waiting, or what the bureau's own vocabulary is.
 *
 * <p>Implementations must not throw when the bureau is slow or unreachable. Failing to get an
 * answer is an ordinary outcome of asking, and it is reported as
 * {@link BureauDecision#undetermined()} so that a timeout leaves the application open instead of
 * looking like a defect.
 */
public interface BureauCheck {

  BureauDecision decide(Onboarding onboarding);
}
