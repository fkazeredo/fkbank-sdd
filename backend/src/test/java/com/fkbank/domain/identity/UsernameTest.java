package com.fkbank.domain.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Username")
class UsernameTest {

  @Test
  @DisplayName("normalizes case and surrounding whitespace so one person is one identity")
  void normalizesInput() {
    assertThat(Username.of("  Ada.Lovelace  ").value()).isEqualTo("ada.lovelace");
  }

  @ParameterizedTest(name = "rejects [{0}]")
  @ValueSource(strings = {"", "   ", "\t", "\n"})
  @DisplayName("rejects a blank username")
  void rejectsBlank(String blank) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> Username.of(blank))
        .withMessageContaining("blank");
  }

  @Test
  @DisplayName("rejects a null username")
  void rejectsNull() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> Username.of(null))
        .withMessageContaining("null");
  }

  @Test
  @DisplayName("rejects a username longer than the storage bound")
  void rejectsOversized() {
    String tooLong = "a".repeat(121);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> Username.of(tooLong))
        .withMessageContaining("120");
  }

  @Test
  @DisplayName("accepts a username exactly at the bound")
  void acceptsTheBoundary() {
    String atLimit = "a".repeat(120);

    assertThat(Username.of(atLimit).value()).hasSize(120);
  }

  @Test
  @DisplayName("prints as its plain value, so log lines stay readable")
  void toStringIsThePlainValue() {
    assertThat(Username.of("ada.lovelace")).hasToString("ada.lovelace");
  }
}
