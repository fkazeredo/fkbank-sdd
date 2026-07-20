package com.fkbank.emulator.bureau;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** The answer this emulator has been told to give for an inquiry. */
public enum Scenario {

  /** Answers immediately that the applicant checks out. */
  APPROVE("approve"),

  /** Answers immediately that the applicant does not check out, with a reason category. */
  DECLINE("decline"),

  /**
   * Answers so late that a caller with a sane timeout has already given up, then tells them the
   * outcome through a signed callback. This is the case that proves the caller does not strand an
   * applicant whose check took too long.
   */
  DELAY("delay"),

  /**
   * Behaves like {@link #DELAY} but delivers the very same callback twice. A real network
   * redelivers, so the receiver has to treat the second copy as a no-op rather than as a second
   * decision; this scenario exists to make that provable instead of assumed.
   */
  DUPLICATE_WEBHOOK("duplicate-webhook");

  private final String wireName;

  Scenario(String wireName) {
    this.wireName = wireName;
  }

  /** The lowercase, hyphenated spelling used in JSON and in configuration. */
  @JsonValue
  public String wireName() {
    return wireName;
  }

  /** Accepts the wire spelling and the constant name alike, in any case. */
  @JsonCreator
  public static Scenario fromWireName(String value) {
    if (value == null) {
      throw new IllegalArgumentException("scenario must not be null");
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    for (Scenario scenario : values()) {
      if (scenario.wireName.equals(normalized)) {
        return scenario;
      }
    }
    throw new IllegalArgumentException("unknown scenario: " + value);
  }
}
