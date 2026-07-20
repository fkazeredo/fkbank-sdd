package com.fkbank.testsupport;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates CPF numbers that pass their own check digits.
 *
 * <p>Tests need applicants who are distinct from each other, and the uniqueness rules under test
 * are enforced on the CPF. Hard-coding a handful of valid numbers would either make tests
 * collide with each other or make "already registered" the accidental outcome of running the
 * suite twice against the same database.
 */
public final class Cpfs {

  private Cpfs() {}

  /** A syntactically valid CPF that no other call is likely to produce. */
  public static String random() {
    int[] digits = new int[11];
    for (int i = 0; i < 9; i++) {
      digits[i] = ThreadLocalRandom.current().nextInt(10);
    }
    // All-equal digits pass the arithmetic but are refused as obviously fabricated, so a run
    // that happened to generate one would fail for a reason that has nothing to do with the
    // behaviour under test.
    if (allEqual(digits)) {
      digits[0] = (digits[0] + 1) % 10;
    }
    digits[9] = checkDigit(digits, 9);
    digits[10] = checkDigit(digits, 10);

    StringBuilder cpf = new StringBuilder(11);
    for (int digit : digits) {
      cpf.append(digit);
    }
    return cpf.toString();
  }

  /** The same number written the way a person types it into a form. */
  public static String formatted(String digits) {
    return digits.substring(0, 3)
        + "."
        + digits.substring(3, 6)
        + "."
        + digits.substring(6, 9)
        + "-"
        + digits.substring(9);
  }

  private static boolean allEqual(int[] digits) {
    for (int i = 1; i < 9; i++) {
      if (digits[i] != digits[0]) {
        return false;
      }
    }
    return true;
  }

  private static int checkDigit(int[] digits, int position) {
    int sum = 0;
    for (int i = 0; i < position; i++) {
      sum += digits[i] * (position + 1 - i);
    }
    int remainder = sum % 11;
    return remainder < 2 ? 0 : 11 - remainder;
  }
}
