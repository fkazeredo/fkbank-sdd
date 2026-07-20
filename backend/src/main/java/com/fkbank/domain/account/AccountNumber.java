package com.fkbank.domain.account;

/**
 * Where a person's account is, in the form they read out loud.
 *
 * <p>One branch exists, so it is fixed rather than configurable — inventing a branch structure
 * before there is a second branch would be a shape maintained for nothing.
 *
 * <p>The number is zero-padded to a fixed width so it looks like an account number rather than
 * a counter, and so two numbers sort and compare the way people expect.
 */
public record AccountNumber(String branch, String number) {

  /** The only branch the bank has. */
  public static final String DEFAULT_BRANCH = "0001";

  private static final int NUMBER_WIDTH = 8;

  public AccountNumber {
    if (branch == null || branch.isBlank()) {
      throw new IllegalArgumentException("branch must not be blank");
    }
    if (number == null || number.isBlank()) {
      throw new IllegalArgumentException("account number must not be blank");
    }
    branch = branch.trim();
    number = number.trim();
  }

  /** Formats a freshly allocated sequence value as an account number at the default branch. */
  public static AccountNumber of(long sequence) {
    if (sequence <= 0) {
      throw new IllegalArgumentException("account number sequence must be positive");
    }
    return new AccountNumber(DEFAULT_BRANCH, pad(sequence));
  }

  /** Rebuilds an account number that already exists. */
  public static AccountNumber of(String branch, String number) {
    return new AccountNumber(branch, number);
  }

  private static String pad(long sequence) {
    String digits = Long.toString(sequence);
    if (digits.length() >= NUMBER_WIDTH) {
      return digits;
    }
    return "0".repeat(NUMBER_WIDTH - digits.length()) + digits;
  }

  @Override
  public String toString() {
    return branch + "-" + number;
  }
}
