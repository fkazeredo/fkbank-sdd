package com.fkbank.domain.customer;

/**
 * A Brazilian individual taxpayer registry number.
 *
 * <p>Stored as eleven digits with the punctuation people type stripped away, because a number
 * that is unique only in the form it happened to be typed is not unique at all: the same person
 * writing {@code 123.456.789-09} and {@code 12345678909} would otherwise open two accounts.
 *
 * <p>The check digits are verified on construction. They catch the transposed and mistyped
 * digits that make up most bad input, and rejecting those here means no later layer has to
 * wonder whether the value it holds is plausible.
 */
public record Cpf(String value) {

  private static final int LENGTH = 11;

  public Cpf {
    if (value == null) {
      throw new IllegalArgumentException("cpf must not be null");
    }
    value = value.replaceAll("[^0-9]", "");
    if (value.length() != LENGTH) {
      throw new IllegalArgumentException("cpf must have " + LENGTH + " digits");
    }
    if (allDigitsEqual(value) || !checkDigitsMatch(value)) {
      throw new IllegalArgumentException("cpf is not a valid number");
    }
  }

  /**
   * Builds a CPF from untrusted input, accepting formatted or digits-only text.
   *
   * @throws IllegalArgumentException if the value is null, is not eleven digits, or fails its
   *     own check digits
   */
  public static Cpf of(String value) {
    return new Cpf(value);
  }

  /**
   * The form shown to a person, with the middle digits hidden.
   *
   * <p>Enough to recognize your own number, not enough to be worth stealing from a log line or
   * a screenshot.
   */
  public String masked() {
    return "***." + value.substring(3, 6) + "." + value.substring(6, 9) + "-**";
  }

  /**
   * Repeated digits pass the check-digit arithmetic but are not issued to anyone, and they are
   * exactly what someone types to get past a form.
   */
  private static boolean allDigitsEqual(String digits) {
    return digits.chars().distinct().count() == 1;
  }

  private static boolean checkDigitsMatch(String digits) {
    return checkDigit(digits, 9) == digits.charAt(9) - '0'
        && checkDigit(digits, 10) == digits.charAt(10) - '0';
  }

  /**
   * Computes one check digit as the registry defines it: each preceding digit is weighted by
   * its distance from the end, and the remainder of the weighted sum decides the digit.
   */
  private static int checkDigit(String digits, int position) {
    int sum = 0;
    for (int i = 0; i < position; i++) {
      sum += (digits.charAt(i) - '0') * (position + 1 - i);
    }
    int remainder = sum % LENGTH;
    return remainder < 2 ? 0 : LENGTH - remainder;
  }

  /** Masked, so a CPF never reaches a log line by someone printing the object that holds it. */
  @Override
  public String toString() {
    return masked();
  }
}
