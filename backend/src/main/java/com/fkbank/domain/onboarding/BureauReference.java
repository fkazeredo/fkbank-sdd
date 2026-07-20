package com.fkbank.domain.onboarding;

import java.util.UUID;

/**
 * What the bureau echoes back to say which application it is answering.
 *
 * <p>Deliberately not the onboarding's own identifier. That one is given to the applicant and
 * appears in the public status address, so using it here would mean anyone who obtained a
 * callback signing key could name a real application to decide using a value the application
 * itself had handed out. The signature is what authenticates the bureau; keeping the reference
 * private removes the other half of what a forgery needs.
 *
 * <p>It is also not the bureau's own inquiry id. On the path where a callback is the whole point
 * — the bureau taking too long to answer — the bank never receives the response that would have
 * carried that id, so it cannot be recorded and cannot be checked against.
 */
public record BureauReference(UUID value) {

  public BureauReference {
    if (value == null) {
      throw new IllegalArgumentException("bureau reference must not be null");
    }
  }

  public static BureauReference next() {
    return new BureauReference(UUID.randomUUID());
  }

  public static BureauReference of(UUID value) {
    return new BureauReference(value);
  }

  /**
   * Reads a reference that arrived in a callback body.
   *
   * @throws IllegalArgumentException if the text is not a valid reference
   */
  public static BureauReference of(String value) {
    if (value == null) {
      throw new IllegalArgumentException("bureau reference must not be null");
    }
    try {
      return new BureauReference(UUID.fromString(value));
    } catch (IllegalArgumentException malformed) {
      throw new IllegalArgumentException("bureau reference is not a valid reference", malformed);
    }
  }

  /** Masked: this value is what stops a forged callback naming a real application. */
  @Override
  public String toString() {
    return "BureauReference[protected]";
  }
}
