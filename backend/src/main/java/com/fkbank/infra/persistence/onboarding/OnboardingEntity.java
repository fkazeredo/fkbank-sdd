package com.fkbank.infra.persistence.onboarding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** How an application to open an account is stored. */
@Entity
@Table(name = "onboarding")
class OnboardingEntity {

  @Id private UUID id;

  @Column(nullable = false, length = 11)
  private String cpf;

  @Column(name = "full_name", nullable = false)
  private String fullName;

  @Column(nullable = false)
  private String email;

  @Column(name = "birth_date", nullable = false)
  private LocalDate birthDate;

  @Column(name = "monthly_income", nullable = false, precision = 19, scale = 2)
  private BigDecimal monthlyIncome;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "bureau_reference", nullable = false, unique = true)
  private UUID bureauReference;

  @Column(nullable = false)
  private String status;

  @Column(name = "reason_category")
  private String reasonCategory;

  @Column(name = "customer_id")
  private UUID customerId;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected OnboardingEntity() {
    // for the persistence provider
  }

  OnboardingEntity(
      UUID id,
      String cpf,
      String fullName,
      String email,
      LocalDate birthDate,
      BigDecimal monthlyIncome,
      String passwordHash,
      UUID bureauReference,
      String status,
      Instant updatedAt) {
    this.id = id;
    this.bureauReference = bureauReference;
    this.cpf = cpf;
    this.fullName = fullName;
    this.email = email;
    this.birthDate = birthDate;
    this.monthlyIncome = monthlyIncome;
    this.passwordHash = passwordHash;
    this.status = status;
    this.updatedAt = updatedAt;
  }

  UUID getId() {
    return id;
  }

  String getCpf() {
    return cpf;
  }

  String getFullName() {
    return fullName;
  }

  String getEmail() {
    return email;
  }

  LocalDate getBirthDate() {
    return birthDate;
  }

  BigDecimal getMonthlyIncome() {
    return monthlyIncome;
  }

  String getPasswordHash() {
    return passwordHash;
  }

  UUID getBureauReference() {
    return bureauReference;
  }

  String getStatus() {
    return status;
  }

  String getReasonCategory() {
    return reasonCategory;
  }

  UUID getCustomerId() {
    return customerId;
  }

  /**
   * Records the outcome of the bureau check.
   *
   * <p>The three fields move together because they describe one thing: a refusal carries a
   * reason and no customer, an approval carries a customer and no reason. Setting them
   * separately is what would let a row claim an outcome it did not reach, which the table's own
   * constraints then refuse.
   */
  void settle(String status, String reasonCategory, UUID customerId, Instant updatedAt) {
    this.status = status;
    this.reasonCategory = reasonCategory;
    this.customerId = customerId;
    this.updatedAt = updatedAt;
  }
}
