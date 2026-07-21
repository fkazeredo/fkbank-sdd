package com.fkbank.domain.ledger;

import java.util.Objects;

/**
 * One posting as it appears on a single account's statement: which leg the account was on, and
 * what the account held immediately after this posting was recorded.
 *
 * <p>The running balance is computed over every posting the account has ever had, not only the
 * ones a given page returns — a page boundary must never change the value a line reports.
 */
public record PostingLine(Posting posting, Direction direction, Money runningBalance) {

  public PostingLine {
    Objects.requireNonNull(posting, "posting must not be null");
    Objects.requireNonNull(direction, "direction must not be null");
    Objects.requireNonNull(runningBalance, "running balance must not be null");
  }
}
