package com.fkbank.domain.identity;

/**
 * A password exactly as a person typed it, on its way to being hashed.
 *
 * <p>This type exists to be short-lived. It is never stored, never logged and never returned;
 * it carries a secret from the edge to the hasher and nowhere else. Its {@code toString} is
 * masked so that an exception, a debug statement or a structured log line cannot spill it, and
 * the strength rule lives here so no caller can create a credential that skipped the check.
 */
public record RawPassword(String value) {

  /** Short enough to type, long enough that the strength rule is worth stating. */
  public static final int MINIMUM_LENGTH = 8;

  /** Bounded because the hashing cost grows with the input a caller controls. */
  private static final int MAX_LENGTH = 200;

  public RawPassword {
    if (value == null) {
      throw new WeakPasswordException("a password is required");
    }
    if (value.length() < MINIMUM_LENGTH) {
      throw new WeakPasswordException(
          "a password must have at least " + MINIMUM_LENGTH + " characters");
    }
    if (value.length() > MAX_LENGTH) {
      throw new WeakPasswordException(
          "a password must not exceed " + MAX_LENGTH + " characters");
    }
    if (!containsLetter(value) || !containsDigit(value)) {
      throw new WeakPasswordException("a password must contain at least one letter and one digit");
    }
  }

  /**
   * Accepts a password from untrusted input.
   *
   * @throws WeakPasswordException if it is absent, too short, too long, or lacks a letter or a
   *     digit
   */
  public static RawPassword of(String value) {
    return new RawPassword(value);
  }

  private static boolean containsLetter(String value) {
    return value.chars().anyMatch(Character::isLetter);
  }

  private static boolean containsDigit(String value) {
    return value.chars().anyMatch(Character::isDigit);
  }

  /** Masked, so the secret cannot reach a log line through the object that holds it. */
  @Override
  public String toString() {
    return "RawPassword[protected]";
  }
}
