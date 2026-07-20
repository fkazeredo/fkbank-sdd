package com.fkbank.domain.onboarding;

import java.util.UUID;

/**
 * Identifies an application to open an account.
 *
 * <p>Also the only thing an applicant holds while they wait. It is unguessable on purpose: the
 * status of an application can be read without signing in, because the person cannot sign in
 * until it succeeds, so the identifier itself has to be the thing that is hard to guess.
 */
public record OnboardingId(UUID value) {

  public OnboardingId {
    if (value == null) {
      throw new IllegalArgumentException("onboarding id must not be null");
    }
  }

  public static OnboardingId next() {
    return new OnboardingId(UUID.randomUUID());
  }

  public static OnboardingId of(UUID value) {
    return new OnboardingId(value);
  }

  /**
   * Reads an identifier that arrived as text.
   *
   * @throws IllegalArgumentException if the text is not a valid identifier
   */
  public static OnboardingId of(String value) {
    if (value == null) {
      throw new IllegalArgumentException("onboarding id must not be null");
    }
    try {
      return new OnboardingId(UUID.fromString(value));
    } catch (IllegalArgumentException malformed) {
      throw new IllegalArgumentException("onboarding id is not a valid identifier", malformed);
    }
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
