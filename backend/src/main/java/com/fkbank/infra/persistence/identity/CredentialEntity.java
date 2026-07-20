package com.fkbank.infra.persistence.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** How sign-in details are stored. */
@Entity
@Table(name = "credential")
class CredentialEntity {

  @Id private UUID id;

  @Column(name = "customer_id", nullable = false, unique = true)
  private UUID customerId;

  @Column(nullable = false, unique = true)
  private String username;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(nullable = false)
  private boolean active;

  protected CredentialEntity() {
    // for the persistence provider
  }

  CredentialEntity(UUID id, UUID customerId, String username, String passwordHash, boolean active) {
    this.id = id;
    this.customerId = customerId;
    this.username = username;
    this.passwordHash = passwordHash;
    this.active = active;
  }

  UUID getId() {
    return id;
  }

  UUID getCustomerId() {
    return customerId;
  }

  String getUsername() {
    return username;
  }

  String getPasswordHash() {
    return passwordHash;
  }

  boolean isActive() {
    return active;
  }

  /** Activation is the one thing about a credential that legitimately changes after issue. */
  void setActive(boolean active) {
    this.active = active;
  }
}
