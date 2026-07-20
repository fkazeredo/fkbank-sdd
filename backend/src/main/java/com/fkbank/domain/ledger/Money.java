package com.fkbank.domain.ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

/**
 * An amount of money.
 *
 * <p>Carries four decimal places internally and rounds to two, half-up, only when the value
 * reaches an edge — a screen, a statement line, an API response. Interest and splits routinely
 * produce fractions smaller than a cent; rounding them away as they are computed loses money a
 * little at a time, and the loss never shows up in one place big enough to notice.
 *
 * <p>A record is appropriate here: the compact constructor rejects every invalid value and
 * normalizes the scale, so the type cannot represent an amount whose precision is in doubt, and
 * it owns its own arithmetic rather than leaving callers to remember the rounding rule.
 *
 * <p>Values may be negative. Whether a particular account is allowed to hold a negative balance
 * is a question about that account, not about the amount, and is answered by {@link Balance}.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

  /** Precision kept while money is being calculated. */
  public static final int INTERNAL_SCALE = 4;

  /** Precision money is presented and settled with. */
  public static final int EDGE_SCALE = 2;

  /** The only currency the product handles; the field exists so adding a second one is a change,
   *  not a rewrite. */
  public static final Currency BRL = Currency.getInstance("BRL");

  public Money {
    Objects.requireNonNull(amount, "amount must not be null");
    Objects.requireNonNull(currency, "currency must not be null");
    amount = amount.setScale(INTERNAL_SCALE, RoundingMode.HALF_UP);
  }

  /** Builds an amount in the product's currency. */
  public static Money of(BigDecimal amount) {
    return new Money(amount, BRL);
  }

  /** Builds an amount in the product's currency from a decimal literal such as {@code "10.00"}. */
  public static Money of(String amount) {
    return of(new BigDecimal(amount));
  }

  /** Zero in the product's currency. */
  public static Money zero() {
    return of(BigDecimal.ZERO);
  }

  public Money add(Money other) {
    requireSameCurrency(other);
    return new Money(amount.add(other.amount), currency);
  }

  public Money subtract(Money other) {
    requireSameCurrency(other);
    return new Money(amount.subtract(other.amount), currency);
  }

  public Money negate() {
    return new Money(amount.negate(), currency);
  }

  public boolean isNegative() {
    return amount.signum() < 0;
  }

  public boolean isPositive() {
    return amount.signum() > 0;
  }

  public boolean isZero() {
    return amount.signum() == 0;
  }

  /**
   * Splits the amount into {@code parts} shares that sum back to exactly this amount.
   *
   * <p>Every share but the last is the truncated quotient; the last one absorbs whatever the
   * division could not distribute. Splitting 10.00 three ways yields 3.3333, 3.3333 and 3.3334 —
   * the alternative, three equal rounded shares, would quietly create or destroy a fraction of a
   * cent on every split.
   *
   * @throws IllegalArgumentException if {@code parts} is not positive
   */
  public List<Money> split(int parts) {
    if (parts <= 0) {
      throw new IllegalArgumentException("cannot split money into " + parts + " parts");
    }
    BigDecimal share =
        amount.divide(BigDecimal.valueOf(parts), INTERNAL_SCALE, RoundingMode.DOWN);
    List<Money> shares = new ArrayList<>(parts);
    for (int index = 0; index < parts - 1; index++) {
      shares.add(new Money(share, currency));
    }
    BigDecimal distributed = share.multiply(BigDecimal.valueOf(parts - 1L));
    shares.add(new Money(amount.subtract(distributed), currency));
    return List.copyOf(shares);
  }

  /**
   * The value as it leaves the system: two decimal places, half-up.
   *
   * <p>This is the only rounding the domain performs, and it is deliberately not applied to the
   * stored value — an amount that has been through an edge keeps its full internal precision.
   */
  public BigDecimal atEdge() {
    return amount.setScale(EDGE_SCALE, RoundingMode.HALF_UP);
  }

  @Override
  public int compareTo(Money other) {
    requireSameCurrency(other);
    return amount.compareTo(other.amount);
  }

  @Override
  public String toString() {
    return currency.getCurrencyCode() + " " + amount.toPlainString();
  }

  private void requireSameCurrency(Money other) {
    Objects.requireNonNull(other, "other amount must not be null");
    if (!currency.equals(other.currency)) {
      throw new IllegalArgumentException(
          "cannot combine %s with %s".formatted(currency.getCurrencyCode(),
              other.currency.getCurrencyCode()));
    }
  }
}
