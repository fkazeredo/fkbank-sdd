package com.fkbank.emulator.bureau;

import java.util.UUID;

/**
 * The bureau's answer.
 *
 * <p>{@code reasonCategory} is present and null on an approval rather than absent, so a caller
 * parses one shape instead of two.
 */
public record InquiryResponse(UUID inquiryId, Outcome outcome, ReasonCategory reasonCategory) {

  static InquiryResponse approved(UUID inquiryId) {
    return new InquiryResponse(inquiryId, Outcome.APPROVED, null);
  }

  static InquiryResponse rejected(UUID inquiryId, ReasonCategory reasonCategory) {
    return new InquiryResponse(inquiryId, Outcome.REJECTED, reasonCategory);
  }
}
