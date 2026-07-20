package com.fkbank.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * The money rules stated as properties rather than examples.
 *
 * <p>Rounding and splitting are where a bank loses fractions of a cent: the failing input is
 * almost never one anybody would have written down as a test case.
 */
class MoneyPropertiesTest {

  @Provide
  Arbitrary<BigDecimal> amounts() {
    return Arbitraries.bigDecimals()
        .between(new BigDecimal("-1000000"), new BigDecimal("1000000"))
        .ofScale(Money.INTERNAL_SCALE);
  }

  @Property
  void edgeRoundingKeepsTwoDecimalsAndLeavesTheStoredValueUntouched(
      @ForAll("amounts") BigDecimal raw) {
    Money money = Money.of(raw);

    assertThat(money.atEdge().scale()).isEqualTo(Money.EDGE_SCALE);
    assertThat(money.amount().scale()).isEqualTo(Money.INTERNAL_SCALE);
    assertThat(money.atEdge())
        .isEqualByComparingTo(money.amount().setScale(Money.EDGE_SCALE, RoundingMode.HALF_UP));
  }

  @Property
  void edgeRoundingNeverMovesTheValueByHalfACentOrMore(@ForAll("amounts") BigDecimal raw) {
    Money money = Money.of(raw);

    BigDecimal drift = money.atEdge().subtract(money.amount()).abs();

    assertThat(drift).isLessThanOrEqualTo(new BigDecimal("0.005"));
  }

  @Property
  void aSplitSumsBackToTheOriginal(
      @ForAll("amounts") BigDecimal raw, @ForAll @IntRange(min = 1, max = 50) int parts) {
    Money original = Money.of(raw);

    List<Money> shares = original.split(parts);

    assertThat(shares).hasSize(parts);
    assertThat(shares.stream().reduce(Money.zero(), Money::add)).isEqualTo(original);
  }

  @Property
  void everyShareButTheLastIsIdentical(
      @ForAll("amounts") BigDecimal raw, @ForAll @IntRange(min = 2, max = 50) int parts) {
    List<Money> shares = Money.of(raw).split(parts);

    assertThat(shares.subList(0, parts - 1)).containsOnly(shares.get(0));
  }

  @Property
  void addingThenSubtractingTheSameAmountChangesNothing(
      @ForAll("amounts") BigDecimal left, @ForAll("amounts") BigDecimal right) {
    Money original = Money.of(left);
    Money other = Money.of(right);

    assertThat(original.add(other).subtract(other)).isEqualTo(original);
  }

  @Property
  void negatingTwiceReturnsTheOriginal(@ForAll("amounts") BigDecimal raw) {
    Money original = Money.of(raw);

    assertThat(original.negate().negate()).isEqualTo(original);
  }

  @Property
  void additionIsOrderIndependent(
      @ForAll("amounts") BigDecimal left, @ForAll("amounts") BigDecimal right) {
    Money first = Money.of(left);
    Money second = Money.of(right);

    assertThat(first.add(second)).isEqualTo(second.add(first));
  }
}
