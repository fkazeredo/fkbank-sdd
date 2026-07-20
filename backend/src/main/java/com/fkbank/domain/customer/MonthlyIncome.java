package com.fkbank.domain.customer;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What a person says they earn in a month.
 *
 * <p>Deliberately not the ledger's money type. This is a self-reported reference figure that
 * feeds how large a limit someone may be granted; it is never a balance, never an amount that
 * moves, and never part of a posting. Giving it the same type as money that exists would invite
 * exactly that confusion, and would make this context depend on the accounting core for a number
 * the accounting core knows nothing about.
 *
 * <p>Two decimals, because that is how a person states an income. Values are rounded rather than
 * refused: someone typing a third decimal made a typing mistake, not an accounting one.
 */
public record MonthlyIncome(BigDecimal value) {

  private static final int SCALE = 2;

  public MonthlyIncome {
    if (value == null) {
      throw new IllegalArgumentException("monthly income must not be null");
    }
    if (value.signum() < 0) {
      throw new IllegalArgumentException("monthly income must not be negative");
    }
    value = value.setScale(SCALE, RoundingMode.HALF_UP);
  }

  /**
   * Builds a declared income from a decimal value.
   *
   * @throws IllegalArgumentException if the value is null or negative
   */
  public static MonthlyIncome of(BigDecimal value) {
    return new MonthlyIncome(value);
  }

  /**
   * Builds a declared income from text as it arrives from a form.
   *
   * @throws IllegalArgumentException if the text is null, not a number, or negative
   */
  public static MonthlyIncome of(String value) {
    if (value == null) {
      throw new IllegalArgumentException("monthly income must not be null");
    }
    try {
      return new MonthlyIncome(new BigDecimal(value.trim()));
    } catch (NumberFormatException notANumber) {
      throw new IllegalArgumentException("monthly income is not a number", notANumber);
    }
  }

  public static MonthlyIncome zero() {
    return new MonthlyIncome(BigDecimal.ZERO);
  }

  @Override
  public String toString() {
    return value.toPlainString();
  }
}
