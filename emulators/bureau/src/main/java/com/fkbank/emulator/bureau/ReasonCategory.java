package com.fkbank.emulator.bureau;

/**
 * Why an applicant was rejected, at the only granularity the bureau publishes.
 *
 * <p>The set is closed and stays small on purpose. A real bureau's internal findings are its own,
 * and a caller that could see them would end up branching on them; a caller that can only see a
 * category has to keep its own rules. Nothing beyond these values ever leaves the service.
 */
public enum ReasonCategory {
  DOCUMENT_MISMATCH,
  SANCTIONS_LIST,
  INCOMPLETE_RECORD,
  UNSPECIFIED
}
