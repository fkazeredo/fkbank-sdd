package com.fkbank.domain.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Cpf")
class CpfTest {

  /** A number whose check digits are genuinely correct, used wherever a valid CPF is needed. */
  private static final String VALID_DIGITS = "12345678909";

  @Nested
  @DisplayName("normalization")
  class Normalization {

    @Test
    @DisplayName("reads the same number whether it was typed with punctuation or without")
    void punctuationDoesNotChangeTheValue() {
      Cpf formatted = Cpf.of("123.456.789-09");
      Cpf digitsOnly = Cpf.of(VALID_DIGITS);

      assertThat(formatted)
          .as("the same person typing the number two ways must not become two customers")
          .isEqualTo(digitsOnly);
      assertThat(formatted.value()).isEqualTo(VALID_DIGITS);
    }

    @Test
    @DisplayName("strips surrounding whitespace and stray separators")
    void stripsWhitespaceAndSeparators() {
      assertThat(Cpf.of("  123 456 789 09  ").value()).isEqualTo(VALID_DIGITS);
    }
  }

  @Nested
  @DisplayName("validation")
  class Validation {

    @Test
    @DisplayName("accepts a number whose check digits are correct")
    void acceptsAValidNumber() {
      assertThat(Cpf.of("111.444.777-35").value()).isEqualTo("11144477735");
    }

    @Test
    @DisplayName("rejects a number with two digits transposed - the check digits exist for this")
    void rejectsATransposedDigit() {
      assertThatThrownBy(() -> Cpf.of("213.456.789-09"))
          .as("swapping the first two digits keeps the length but breaks the arithmetic")
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("valid");
    }

    @ParameterizedTest
    @ValueSource(strings = {"00000000000", "11111111111", "99999999999"})
    @DisplayName("rejects a number made of one repeated digit even though the arithmetic passes")
    void rejectsRepeatedDigits(String repeated) {
      assertThatThrownBy(() -> Cpf.of(repeated))
          .as("these satisfy the check digits but are never issued, and are what someone types"
              + " to get past a form")
          .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567890", "123456789012", "", "123.456.789-0"})
    @DisplayName("rejects anything that is not exactly eleven digits")
    void rejectsWrongLength(String wrongLength) {
      assertThatThrownBy(() -> Cpf.of(wrongLength))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("11 digits");
    }

    @Test
    @DisplayName("rejects null")
    void rejectsNull() {
      assertThatThrownBy(() -> Cpf.of(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("null");
    }
  }

  @Nested
  @DisplayName("disclosure")
  class Disclosure {

    @Test
    @DisplayName("masks the first three and the last two digits")
    void masksTheEnds() {
      assertThat(Cpf.of(VALID_DIGITS).masked())
          .as("enough to recognize your own number, not enough to be worth stealing")
          .isEqualTo("***.456.789-**");
    }

    @Test
    @DisplayName("prints masked, so the number cannot reach a log through the object holding it")
    void toStringIsMasked() {
      Cpf cpf = Cpf.of(VALID_DIGITS);

      assertThat(cpf.toString()).isEqualTo(cpf.masked());
      assertThat(cpf.toString())
          .as("a full CPF appearing in a log line is a data leak, so no printing path may expose"
              + " it")
          .doesNotContain(VALID_DIGITS)
          .doesNotContain("123");
    }
  }
}
