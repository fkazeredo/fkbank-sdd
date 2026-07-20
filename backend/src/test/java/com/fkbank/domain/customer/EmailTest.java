package com.fkbank.domain.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Email")
class EmailTest {

  @Nested
  @DisplayName("normalization")
  class Normalization {

    @Test
    @DisplayName("lower-cases and trims, so uniqueness survives however it was typed")
    void lowerCasesAndTrims() {
      Email email = Email.of("  Ana.Souza@Example.COM  ");

      assertThat(email.value()).isEqualTo("ana.souza@example.com");
    }

    @Test
    @DisplayName("treats two spellings of the same address as the same value")
    void spellingsOfTheSameAddressAreEqual() {
      assertThat(Email.of("ANA@EXAMPLE.COM"))
          .as("otherwise one person could register twice by changing capitalization")
          .isEqualTo(Email.of("ana@example.com"));
    }
  }

  @Nested
  @DisplayName("shape")
  class Shape {

    @ParameterizedTest
    @ValueSource(strings = {
        "ana@example.com",
        "ana.souza+bank@sub.example.co.uk",
        "a@b.co"
    })
    @DisplayName("accepts addresses that genuinely deliver")
    void acceptsDeliverableAddresses(String address) {
      assertThat(Email.of(address).value()).isEqualTo(address);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ana",
        "ana@",
        "@example.com",
        "ana@example",
        "ana@@example.com",
        "ana souza@example.com",
        "ana@exa mple.com"
    })
    @DisplayName("rejects text that is not an address at all")
    void rejectsMalformedAddresses(String malformed) {
      assertThatThrownBy(() -> Email.of(malformed))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects a blank value, including one that is only whitespace")
    void rejectsBlank() {
      assertThatThrownBy(() -> Email.of("   "))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("blank");
    }

    @Test
    @DisplayName("rejects null")
    void rejectsNull() {
      assertThatThrownBy(() -> Email.of(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("null");
    }
  }

  @Nested
  @DisplayName("length")
  class Length {

    @Test
    @DisplayName("accepts an address at the longest length that is allowed to exist")
    void acceptsTheLongestAllowedAddress() {
      String local = "a".repeat(254 - "@example.com".length());

      assertThat(Email.of(local + "@example.com").value()).hasSize(254);
    }

    @Test
    @DisplayName("rejects an address one character past the bound")
    void rejectsOnePastTheBound() {
      String local = "a".repeat(255 - "@example.com".length());

      assertThatThrownBy(() -> Email.of(local + "@example.com"))
          .as("an unbounded address is unbounded storage and an unbounded log line")
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("254");
    }
  }

  @Test
  @DisplayName("prints as the address itself")
  void printsAsTheAddress() {
    assertThat(Email.of("ana@example.com")).hasToString("ana@example.com");
  }
}
