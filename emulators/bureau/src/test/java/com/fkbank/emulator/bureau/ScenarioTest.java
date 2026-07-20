package com.fkbank.emulator.bureau;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Scenario")
class ScenarioTest {

  @ParameterizedTest(name = "[{0}] is read as {1}")
  @CsvSource({
    "approve, APPROVE",
    "decline, DECLINE",
    "delay, DELAY",
    "duplicate-webhook, DUPLICATE_WEBHOOK",
    "DUPLICATE_WEBHOOK, DUPLICATE_WEBHOOK",
    "  Decline  , DECLINE"
  })
  @DisplayName("reads the wire spelling and the constant name alike")
  void readsBothSpellings(String input, Scenario expected) {
    assertThat(Scenario.fromWireName(input)).as("parsed scenario").isEqualTo(expected);
  }

  @Test
  @DisplayName("writes the hyphenated lowercase spelling callers configure it with")
  void writesTheWireSpelling() {
    assertThat(Scenario.DUPLICATE_WEBHOOK.wireName())
        .as("wire spelling")
        .isEqualTo("duplicate-webhook");
  }

  @Test
  @DisplayName("refuses a scenario it does not implement instead of quietly falling back")
  void rejectsAnUnknownScenario() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> Scenario.fromWireName("explode"))
        .withMessageContaining("explode");
  }

  @Test
  @DisplayName("refuses a null scenario")
  void rejectsNull() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> Scenario.fromWireName(null))
        .withMessageContaining("null");
  }
}
