package com.fkbank.domain.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("FullName")
class FullNameTest {

  @Nested
  @DisplayName("normalization")
  class Normalization {

    @Test
    @DisplayName("collapses runs of whitespace and trims the ends")
    void collapsesWhitespace() {
      assertThat(FullName.of("  Ana   Maria \t Souza  ").value())
          .as("incidental spacing is a typing artifact, not part of anyone's name")
          .isEqualTo("Ana Maria Souza");
    }

    @Test
    @DisplayName("leaves accents, particles, apostrophes and hyphens alone")
    void leavesRealNamesAlone() {
      assertThat(FullName.of("Joana d'Ávila de Souza-Lima").value())
          .as("a validator that cleans names mostly rejects real people")
          .isEqualTo("Joana d'Ávila de Souza-Lima");
    }
  }

  @Nested
  @DisplayName("validation")
  class Validation {

    @ParameterizedTest
    @ValueSource(strings = {"Ana", "Souza", "  Ana  "})
    @DisplayName("rejects a single word - a family name is what the bureau matches on")
    void rejectsASingleWord(String singleWord) {
      assertThatThrownBy(() -> FullName.of(singleWord))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("family name");
    }

    @Test
    @DisplayName("accepts the shortest name that still has two parts")
    void acceptsTheShortestTwoPartName() {
      assertThat(FullName.of("Al Bo").value()).isEqualTo("Al Bo");
    }

    @Test
    @DisplayName("rejects a value shorter than the minimum")
    void rejectsTooShort() {
      assertThatThrownBy(() -> FullName.of("A"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("at least 3");
    }

    @Test
    @DisplayName("accepts a name at the longest allowed length")
    void acceptsTheLongestAllowedName() {
      String name = "A".repeat(80) + " " + "B".repeat(79);

      assertThat(FullName.of(name).value()).hasSize(160);
    }

    @Test
    @DisplayName("rejects a name one character past the bound")
    void rejectsOnePastTheBound() {
      String name = "A".repeat(80) + " " + "B".repeat(80);

      assertThatThrownBy(() -> FullName.of(name))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("160");
    }

    @Test
    @DisplayName("rejects null")
    void rejectsNull() {
      assertThatThrownBy(() -> FullName.of(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("null");
    }
  }

  @Test
  @DisplayName("prints as the name itself")
  void printsAsTheName() {
    assertThat(FullName.of("Ana Souza")).hasToString("Ana Souza");
  }
}
