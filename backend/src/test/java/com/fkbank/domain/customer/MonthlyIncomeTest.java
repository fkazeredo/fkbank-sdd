package com.fkbank.domain.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("MonthlyIncome")
class MonthlyIncomeTest {

  @Nested
  @DisplayName("scale")
  class Scale {

    @Test
    @DisplayName("normalizes any input to two decimals, because that is how a person states it")
    void normalizesToTwoDecimals() {
      assertThat(MonthlyIncome.of("3500").value().scale()).isEqualTo(2);
      assertThat(MonthlyIncome.of("3500.5").value()).isEqualByComparingTo("3500.50");
    }

    @ParameterizedTest
    @CsvSource({
        "3500.005, 3500.01",
        "3500.004, 3500.00",
        "3500.015, 3500.02",
        "0.005,    0.01"
    })
    @DisplayName("rounds a third decimal half-up instead of refusing it")
    void roundsTheThirdDecimalHalfUp(String typed, String stored) {
      assertThat(MonthlyIncome.of(typed).value())
          .as("someone typing a third decimal made a typing mistake, not an accounting one")
          .isEqualByComparingTo(stored);
    }

    @Test
    @DisplayName("rounds a half away from zero rather than to even")
    void roundsHalfUpNotHalfEven() {
      assertThat(MonthlyIncome.of("0.125").value()).isEqualByComparingTo("0.13");
      assertThat(MonthlyIncome.of("0.135").value()).isEqualByComparingTo("0.14");
    }
  }

  @Nested
  @DisplayName("range")
  class Range {

    @Test
    @DisplayName("allows zero - declaring no income is an answer, not an invalid one")
    void allowsZero() {
      assertThat(MonthlyIncome.zero().value()).isEqualByComparingTo("0.00");
      assertThat(MonthlyIncome.of("0").value()).isEqualByComparingTo("0.00");
      assertThat(MonthlyIncome.of(BigDecimal.ZERO)).isEqualTo(MonthlyIncome.zero());
    }

    @Test
    @DisplayName("rejects a negative figure from either factory")
    void rejectsNegative() {
      assertThatThrownBy(() -> MonthlyIncome.of("-0.01"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("negative");
      assertThatThrownBy(() -> MonthlyIncome.of(new BigDecimal("-1")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("negative");
    }
  }

  @Nested
  @DisplayName("factories")
  class Factories {

    @Test
    @DisplayName("text and decimal factories produce the same value")
    void factoriesAgree() {
      assertThat(MonthlyIncome.of("3500.005"))
          .as("a figure typed into a form and the same figure read from storage must not differ")
          .isEqualTo(MonthlyIncome.of(new BigDecimal("3500.005")));
      assertThat(MonthlyIncome.of("  3500.00  "))
          .isEqualTo(MonthlyIncome.of(new BigDecimal("3500")));
    }

    @Test
    @DisplayName("rejects text that is not a number, without leaking a parsing failure")
    void rejectsTextThatIsNotANumber() {
      assertThatThrownBy(() -> MonthlyIncome.of("three thousand"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not a number")
          .hasCauseInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("rejects null from either factory")
    void rejectsNull() {
      assertThatThrownBy(() -> MonthlyIncome.of((String) null))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> MonthlyIncome.of((BigDecimal) null))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  @DisplayName("prints as a plain decimal, never in scientific notation")
  void printsAsPlainDecimal() {
    assertThat(MonthlyIncome.of("3500.00")).hasToString("3500.00");
    assertThat(MonthlyIncome.of(new BigDecimal("1E+4"))).hasToString("10000.00");
  }
}
