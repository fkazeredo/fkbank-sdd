package com.fkbank.infra.persistence.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** How a registered person is stored. */
@Entity
@Table(name = "customer")
class CustomerEntity {

  @Id private UUID id;

  @Column(name = "full_name", nullable = false)
  private String fullName;

  @Column(nullable = false, unique = true, length = 11)
  private String cpf;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "birth_date", nullable = false)
  private LocalDate birthDate;

  @Column(name = "monthly_income", nullable = false, precision = 19, scale = 2)
  private BigDecimal monthlyIncome;

  protected CustomerEntity() {
    // for the persistence provider
  }

  CustomerEntity(
      UUID id,
      String fullName,
      String cpf,
      String email,
      LocalDate birthDate,
      BigDecimal monthlyIncome) {
    this.id = id;
    this.fullName = fullName;
    this.cpf = cpf;
    this.email = email;
    this.birthDate = birthDate;
    this.monthlyIncome = monthlyIncome;
  }

  UUID getId() {
    return id;
  }

  String getFullName() {
    return fullName;
  }

  String getCpf() {
    return cpf;
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
}
