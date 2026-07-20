package com.fkbank.domain.onboarding;

/**
 * What the bureau said about an applicant.
 *
 * <p>Three outcomes, not two: besides approving and refusing, the bureau may fail to answer in
 * time. That is not a refusal — the applicant did nothing wrong and their application stays
 * open — so it is a decision the domain names rather than an error the caller has to interpret.
 *
 * <p>The reason is already reduced to a category we are willing to show. The bureau's own
 * wording never gets this far.
 */
public record BureauDecision(Outcome outcome, RejectionReason reason) {

  /** What the bureau concluded, or that it did not conclude anything yet. */
  public enum Outcome {
    APPROVED,
    REJECTED,

    /** No answer within the time we were prepared to wait. */
    UNDETERMINED
  }

  public BureauDecision {
    if (outcome == null) {
      throw new IllegalArgumentException("bureau outcome must not be null");
    }
    if (outcome == Outcome.REJECTED && reason == null) {
      reason = RejectionReason.UNSPECIFIED;
    }
    if (outcome != Outcome.REJECTED) {
      reason = null;
    }
  }

  public static BureauDecision approved() {
    return new BureauDecision(Outcome.APPROVED, null);
  }

  public static BureauDecision rejected(RejectionReason reason) {
    return new BureauDecision(Outcome.REJECTED, reason);
  }

  /** The bureau did not answer in time; the application stays open. */
  public static BureauDecision undetermined() {
    return new BureauDecision(Outcome.UNDETERMINED, null);
  }

  public boolean isApproved() {
    return outcome == Outcome.APPROVED;
  }

  public boolean isRejected() {
    return outcome == Outcome.REJECTED;
  }

  /** Whether the bureau reached any conclusion at all. */
  public boolean isDetermined() {
    return outcome != Outcome.UNDETERMINED;
  }
}
