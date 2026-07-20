package com.fkbank.domain.identity;

import java.util.Objects;
import java.util.UUID;

/**
 * What a person signs in with.
 *
 * <p>Issued inactive and activated only once the application to open an account has actually
 * succeeded. That ordering is the point of the type: a credential created at sign-up but
 * activated on approval means a refused applicant never holds working sign-in details, and it
 * means the window between the two states is explicit rather than implied by the absence of
 * some other row.
 *
 * <p>A class rather than a record because activation is a state change with a rule attached,
 * and because lockout and rotation will live here as the product grows.
 */
public final class Credential {

  private final CredentialId id;
  private final UUID ownerId;
  private final Username username;
  private final PasswordHash passwordHash;
  private boolean active;

  private Credential(
      CredentialId id, UUID ownerId, Username username, PasswordHash passwordHash, boolean active) {
    this.id = Objects.requireNonNull(id, "credential id must not be null");
    this.ownerId = Objects.requireNonNull(ownerId, "owner id must not be null");
    this.username = Objects.requireNonNull(username, "username must not be null");
    this.passwordHash = Objects.requireNonNull(passwordHash, "password hash must not be null");
    this.active = active;
  }

  /**
   * Issues sign-in details that cannot yet be used.
   *
   * @param ownerId the person these details belong to
   */
  public static Credential issue(
      CredentialId id, UUID ownerId, Username username, PasswordHash passwordHash) {
    return new Credential(id, ownerId, username, passwordHash, false);
  }

  /** Rebuilds a credential that already exists. */
  public static Credential existing(
      CredentialId id, UUID ownerId, Username username, PasswordHash passwordHash, boolean active) {
    return new Credential(id, ownerId, username, passwordHash, active);
  }

  /** Lets these details be used to sign in. Activating twice is not an error. */
  public void activate() {
    this.active = true;
  }

  public boolean isActive() {
    return active;
  }

  public CredentialId id() {
    return id;
  }

  public UUID ownerId() {
    return ownerId;
  }

  public Username username() {
    return username;
  }

  public PasswordHash passwordHash() {
    return passwordHash;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Credential credential && id.equals(credential.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "Credential[" + id + " " + username + (active ? " active]" : " inactive]");
  }
}
