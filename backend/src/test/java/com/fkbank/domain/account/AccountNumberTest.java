package com.fkbank.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("AccountNumber")
class AccountNumberTest {

  @Nested
  @DisplayName("allocation from a sequence")
  class AllocationFromASequence {

    @ParameterizedTest
    @CsvSource({
        "1,        00000001",
        "42,       00000042",
        "999999,   00999999",
        "10000000, 10000000"
    })
    @DisplayName("zero-pads the sequence to eight digits")
    void padsToEightDigits(long sequence, String expected) {
      AccountNumber number = AccountNumber.of(sequence);

      assertThat(number.number())
          .as("padding makes it read like an account number rather than a counter, and makes two"
              + " numbers sort the way people expect")
          .isEqualTo(expected);
    }

    @Test
    @DisplayName("allocates at the only branch the bank has")
    void allocatesAtTheDefaultBranch() {
      assertThat(AccountNumber.of(7).branch()).isEqualTo("0001");
      assertThat(AccountNumber.DEFAULT_BRANCH).isEqualTo("0001");
    }

    @Test
    @DisplayName("does not truncate a sequence that has outgrown eight digits")
    void doesNotTruncateALongSequence() {
      AccountNumber number = AccountNumber.of(123456789L);

      assertThat(number.number())
          .as("silently dropping a digit would hand two customers the same account number")
          .isEqualTo("123456789")
          .hasSize(9);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    @DisplayName("refuses a non-positive sequence")
    void refusesANonPositiveSequence(long sequence) {
      assertThatThrownBy(() -> AccountNumber.of(sequence))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("positive");
    }
  }

  @Nested
  @DisplayName("reconstitution")
  class Reconstitution {

    @Test
    @DisplayName("rebuilds a stored number and trims incidental whitespace")
    void rebuildsAndTrims() {
      AccountNumber number = AccountNumber.of(" 0001 ", " 00000042 ");

      assertThat(number.branch()).isEqualTo("0001");
      assertThat(number.number()).isEqualTo("00000042");
    }

    @Test
    @DisplayName("refuses a blank branch or a blank number")
    void refusesBlankParts() {
      assertThatThrownBy(() -> AccountNumber.of("  ", "00000042"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("branch");
      assertThatThrownBy(() -> AccountNumber.of("0001", "  "))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("account number");
      assertThatThrownBy(() -> AccountNumber.of(null, "00000042"))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> AccountNumber.of("0001", null))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  @DisplayName("two numbers allocated from the same sequence are the same value")
  void comparesByValue() {
    assertThat(AccountNumber.of(42)).isEqualTo(AccountNumber.of("0001", "00000042"));
  }

  @Test
  @DisplayName("reads as branch and number, the way a person reads it out loud")
  void printsAsBranchAndNumber() {
    assertThat(AccountNumber.of(42)).hasToString("0001-00000042");
  }
}
