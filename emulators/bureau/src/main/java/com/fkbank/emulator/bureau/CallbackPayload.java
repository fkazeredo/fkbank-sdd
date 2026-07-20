package com.fkbank.emulator.bureau;

import java.util.UUID;

/**
 * The body the bureau posts back when it answers late.
 *
 * <p>It carries the caller's own reference alongside the outcome, because the caller that timed
 * out no longer has the inquiry id it never received.
 */
public record CallbackPayload(
    UUID inquiryId, String reference, Outcome outcome, ReasonCategory reasonCategory) {

  static CallbackPayload of(String reference, InquiryResponse response) {
    return new CallbackPayload(
        response.inquiryId(), reference, response.outcome(), response.reasonCategory());
  }
}
