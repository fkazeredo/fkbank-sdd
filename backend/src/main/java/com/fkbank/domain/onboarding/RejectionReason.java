package com.fkbank.domain.onboarding;

import java.util.Locale;

/**
 * Why an application was refused, in the terms the applicant is told.
 *
 * <p>A deliberately small, closed set. The bureau's own answer is far more detailed and is not
 * ours to disclose: it describes a person's record at a third party, and passing it through
 * would turn a refusal message into a data leak. The applicant learns the category so they know
 * whether to correct something and try again.
 */
public enum RejectionReason {

  /** The details given do not match the ones on record. */
  DOCUMENT_MISMATCH,

  /** The applicant appears on a list the bank may not open an account against. */
  SANCTIONS_LIST,

  /** The bureau holds too little about this person to reach a decision. */
  INCOMPLETE_RECORD,

  /** The bureau refused without giving a category we recognize. */
  UNSPECIFIED;

  /**
   * Maps whatever the bureau called it onto a category we are willing to show.
   *
   * <p>Anything unrecognized becomes {@link #UNSPECIFIED} rather than being passed through: a
   * new category invented upstream must not reach an applicant unreviewed.
   */
  public static RejectionReason from(String bureauCategory) {
    if (bureauCategory == null || bureauCategory.isBlank()) {
      return UNSPECIFIED;
    }
    try {
      return valueOf(bureauCategory.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException unrecognized) {
      return UNSPECIFIED;
    }
  }
}
