package com.fkbank.domain.customer;

/**
 * The name a person registered under.
 *
 * <p>Requires two parts, because a single word is almost always an incomplete form rather than
 * someone's whole legal name, and the bureau is asked to match on the full one. Beyond that the
 * value is left alone: names carry accents, particles, apostrophes and hyphens, and a validator
 * that "cleans" them mostly rejects real people.
 */
public record FullName(String value) {

  private static final int MIN_LENGTH = 3;

  private static final int MAX_LENGTH = 160;

  public FullName {
    if (value == null) {
      throw new IllegalArgumentException("full name must not be null");
    }
    value = value.trim().replaceAll("\\s+", " ");
    if (value.length() < MIN_LENGTH) {
      throw new IllegalArgumentException(
          "full name must have at least " + MIN_LENGTH + " characters");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("full name must not exceed " + MAX_LENGTH + " characters");
    }
    if (!value.contains(" ")) {
      throw new IllegalArgumentException("full name must include a family name");
    }
  }

  /**
   * Builds a full name from untrusted input, collapsing incidental whitespace.
   *
   * @throws IllegalArgumentException if the value is null, too short, too long, or a single word
   */
  public static FullName of(String value) {
    return new FullName(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
