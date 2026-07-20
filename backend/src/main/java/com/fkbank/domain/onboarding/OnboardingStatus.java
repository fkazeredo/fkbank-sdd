package com.fkbank.domain.onboarding;

/**
 * Where an application to open an account has got to.
 *
 * <p>A closed state machine, so an enum rather than reference data. {@code PENDING} is the only
 * state that can still change, and it is a real state rather than an absence of one: an
 * applicant whose bureau check has not come back yet is waiting, not rejected.
 */
public enum OnboardingStatus {

  /** Submitted, and the bureau has not answered yet. */
  PENDING,

  /** The bureau approved: the customer, their credential and their account exist. */
  APPROVED,

  /** The bureau refused. Nothing was created. */
  REJECTED;

  public boolean isPending() {
    return this == PENDING;
  }

  /** Whether an application in this state can still change. */
  public boolean isSettled() {
    return this != PENDING;
  }
}
