package com.fkbank.domain.customer;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * An e-mail address, which is also what a person signs in with.
 *
 * <p>Normalized to lower case so that uniqueness survives however the address was typed. The
 * shape check is deliberately shallow — one at-sign, something either side, a dot in the
 * domain. Anything stricter rejects addresses that genuinely deliver, and only actually sending
 * mail proves an address exists.
 */
public record Email(String value) {

  private static final int MAX_LENGTH = 254;

  private static final Pattern SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");

  public Email {
    if (value == null) {
      throw new IllegalArgumentException("email must not be null");
    }
    value = value.trim().toLowerCase(Locale.ROOT);
    if (value.isEmpty()) {
      throw new IllegalArgumentException("email must not be blank");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("email must not exceed " + MAX_LENGTH + " characters");
    }
    if (!SHAPE.matcher(value).matches()) {
      throw new IllegalArgumentException("email is not a valid address");
    }
  }

  /**
   * Builds an e-mail address from untrusted input.
   *
   * @throws IllegalArgumentException if the value is null, blank, too long or malformed
   */
  public static Email of(String value) {
    return new Email(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
