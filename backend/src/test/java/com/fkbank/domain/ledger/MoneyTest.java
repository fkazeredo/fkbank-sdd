package com.fkbank.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Money")
class MoneyTest {

  @Nested
  @DisplayName("precision")
  class Precision {

    @Test
    @DisplayName("keeps four decimal places internally")
    void keepsFourDecimalsInternally() {
      Money money = Money.of("10.00").subtract(Money.of("9.9954"));

      assertThat(money.amount()).isEqualByComparingTo("0.0046");
      assertThat(money.amount().scale()).isEqualTo(4);
    }

    @Test
    @DisplayName("normalizes any input to four decimal places")
    void normalizesInputScale() {
      assertThat(Money.of("1").amount().scale()).isEqualTo(4);
      assertThat(Money.of("1.5").amount().scale()).isEqualTo(4);
      assertThat(Money.of(new BigDecimal("1.00000")).amount()).isEqualByComparingTo("1.0000");
    }

    @Test
    @DisplayName("rounds half-up to two decimals only at the edge, leaving the stored value alone")
    void roundsHalfUpAtTheEdge() {
      Money money = Money.of("0.00456");

      assertThat(money.atEdge()).isEqualByComparingTo("0.00");
      assertThat(money.amount()).isEqualByComparingTo("0.0046");
    }

    @Test
    @DisplayName("rounds a half away from zero rather than to even")
    void roundsHalfUpNotHalfEven() {
      assertThat(Money.of("0.125").atEdge()).isEqualByComparingTo("0.13");
      assertThat(Money.of("0.135").atEdge()).isEqualByComparingTo("0.14");
    }
  }

  @Nested
  @DisplayName("arithmetic")
  class Arithmetic {

    @Test
    @DisplayName("adds and subtracts")
    void addsAndSubtracts() {
      assertThat(Money.of("10.00").add(Money.of("2.50"))).isEqualTo(Money.of("12.50"));
      assertThat(Money.of("10.00").subtract(Money.of("2.50"))).isEqualTo(Money.of("7.50"));
    }

    @Test
    @DisplayName("goes negative when subtracting more than it holds - the sign rule belongs to the"
        + " account, not the amount")
    void allowsNegativeValues() {
      Money money = Money.of("1.00").subtract(Money.of("3.00"));

      assertThat(money.isNegative()).isTrue();
      assertThat(money.amount()).isEqualByComparingTo("-2.0000");
    }

    @Test
    @DisplayName("refuses to combine different currencies")
    void refusesMixedCurrencies() {
      Money reais = Money.of("10.00");
      Money dollars = new Money(new BigDecimal("10.00"), Currency.getInstance("USD"));

      assertThatThrownBy(() -> reais.add(dollars))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("BRL")
          .hasMessageContaining("USD");
    }

    @Test
    @DisplayName("rejects null")
    void rejectsNull() {
      assertThatThrownBy(() -> Money.of((BigDecimal) null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("split")
  class Split {

    @Test
    @DisplayName("puts the undistributable remainder on the last share")
    void remainderGoesToTheLastShare() {
      List<Money> shares = Money.of("10.00").split(3);

      assertThat(shares).containsExactly(
          Money.of("3.3333"), Money.of("3.3333"), Money.of("3.3334"));
    }

    @Test
    @DisplayName("divides evenly when it can")
    void dividesEvenly() {
      assertThat(Money.of("10.00").split(2))
          .containsExactly(Money.of("5.00"), Money.of("5.00"));
    }

    @Test
    @DisplayName("returns the whole amount when split once")
    void splitOnceReturnsTheWhole() {
      assertThat(Money.of("10.00").split(1)).containsExactly(Money.of("10.00"));
    }

    @Test
    @DisplayName("refuses a non-positive number of parts")
    void refusesNonPositiveParts() {
      Money ten = Money.of("10.00");

      assertThatThrownBy(() -> ten.split(0)).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> ten.split(-1)).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  @DisplayName("compares by value")
  void comparesByValue() {
    assertThat(Money.of("10.00")).isEqualByComparingTo(Money.of("10.0000"));
    assertThat(Money.of("10.00")).isGreaterThan(Money.of("9.99"));
  }

  @Test
  @DisplayName("treats amounts written with different scales as equal")
  void equalityIgnoresWrittenScale() {
    assertThat(Money.of("10.00")).isEqualTo(Money.of("10.0000"));
    assertThat(Money.of("10.00")).hasSameHashCodeAs(Money.of("10"));
  }

  @Test
  @DisplayName("reads as an amount with its currency")
  void readsAsAmountWithCurrency() {
    assertThat(Money.of("10.00")).hasToString("BRL 10.0000");
  }
}
