package com.fkbank.infra.persistence.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** How a customer's account is stored. */
@Entity
@Table(name = "current_account")
class CurrentAccountEntity {

  @Id private UUID id;

  @Column(name = "customer_id", nullable = false, unique = true)
  private UUID customerId;

  @Column(nullable = false, length = 4)
  private String branch;

  @Column(nullable = false, length = 10)
  private String number;

  @Column(name = "opened_at", nullable = false)
  private Instant openedAt;

  protected CurrentAccountEntity() {
    // for the persistence provider
  }

  CurrentAccountEntity(UUID id, UUID customerId, String branch, String number, Instant openedAt) {
    this.id = id;
    this.customerId = customerId;
    this.branch = branch;
    this.number = number;
    this.openedAt = openedAt;
  }

  UUID getId() {
    return id;
  }

  UUID getCustomerId() {
    return customerId;
  }

  String getBranch() {
    return branch;
  }

  String getNumber() {
    return number;
  }

  Instant getOpenedAt() {
    return openedAt;
  }
}
