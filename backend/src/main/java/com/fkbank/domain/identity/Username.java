package com.fkbank.domain.identity;

import java.util.Locale;

/**
 * The name a person signs in with.
 *
 * <p>A record is appropriate here (docs/ARCHITECTURE.md §Behavioral domain modeling): the
 * compact constructor rejects every invalid value, so the type cannot represent invalid state,
 * and it owns its own normalization rather than leaving callers to remember it.
 */
public record Username(String value) {

  /** Long enough for an e-mail address, short enough to bound storage and log lines. */
  private static final int MAX_LENGTH = 120;

  public Username {
    if (value == null) {
      throw new IllegalArgumentException("username must not be null");
    }
    value = value.trim().toLowerCase(Locale.ROOT);
    if (value.isEmpty()) {
      throw new IllegalArgumentException("username must not be blank");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("username must not exceed " + MAX_LENGTH + " characters");
    }
  }

  /**
   * Builds a username from untrusted input (a token claim, a form field).
   *
   * @throws IllegalArgumentException if the value is null, blank or too long
   */
  public static Username of(String value) {
    return new Username(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
