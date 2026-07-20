package com.fkbank.domain.onboarding;

import com.fkbank.domain.customer.Cpf;
import com.fkbank.domain.customer.CustomerId;
import com.fkbank.domain.customer.Email;
import com.fkbank.domain.customer.FullName;
import com.fkbank.domain.customer.MonthlyIncome;
import com.fkbank.domain.identity.PasswordHash;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * An application to open an account, from submission to outcome.
 *
 * <p>It keeps its own copy of what was submitted because there is nobody to attach it to yet: a
 * customer exists only once the bureau approves. The password is held already hashed, so an
 * application sitting and waiting never holds a readable secret.
 *
 * <p>Settling is one-way. Once approved or refused an application does not change again, which
 * is what makes a repeated bureau callback harmless — the second delivery finds an application
 * that has already made up its mind and does nothing.
 */
public final class Onboarding {

  private final OnboardingId id;
  private final FullName fullName;
  private final Cpf cpf;
  private final Email email;
  private final LocalDate birthDate;
  private final MonthlyIncome monthlyIncome;
  private final PasswordHash passwordHash;

  private OnboardingStatus status;
  private RejectionReason reason;
  private CustomerId customerId;

  private Onboarding(
      OnboardingId id,
      FullName fullName,
      Cpf cpf,
      Email email,
      LocalDate birthDate,
      MonthlyIncome monthlyIncome,
      PasswordHash passwordHash,
      OnboardingStatus status,
      RejectionReason reason,
      CustomerId customerId) {
    this.id = Objects.requireNonNull(id, "onboarding id must not be null");
    this.fullName = Objects.requireNonNull(fullName, "full name must not be null");
    this.cpf = Objects.requireNonNull(cpf, "cpf must not be null");
    this.email = Objects.requireNonNull(email, "email must not be null");
    this.birthDate = Objects.requireNonNull(birthDate, "birth date must not be null");
    this.monthlyIncome = Objects.requireNonNull(monthlyIncome, "monthly income must not be null");
    this.passwordHash = Objects.requireNonNull(passwordHash, "password hash must not be null");
    this.status = Objects.requireNonNull(status, "status must not be null");
    this.reason = reason;
    this.customerId = customerId;
  }

  /** Records a submitted application, waiting on the bureau. */
  public static Onboarding submit(
      OnboardingId id,
      FullName fullName,
      Cpf cpf,
      Email email,
      LocalDate birthDate,
      MonthlyIncome monthlyIncome,
      PasswordHash passwordHash) {
    return new Onboarding(
        id,
        fullName,
        cpf,
        email,
        birthDate,
        monthlyIncome,
        passwordHash,
        OnboardingStatus.PENDING,
        null,
        null);
  }

  /** Rebuilds an application that already exists. */
  public static Onboarding existing(
      OnboardingId id,
      FullName fullName,
      Cpf cpf,
      Email email,
      LocalDate birthDate,
      MonthlyIncome monthlyIncome,
      PasswordHash passwordHash,
      OnboardingStatus status,
      RejectionReason reason,
      CustomerId customerId) {
    return new Onboarding(
        id,
        fullName,
        cpf,
        email,
        birthDate,
        monthlyIncome,
        passwordHash,
        status,
        reason,
        customerId);
  }

  /**
   * Records that the application succeeded and names the customer it created.
   *
   * @throws OnboardingAlreadySettledException if it had already been approved or refused
   */
  public void approve(CustomerId customerId) {
    requirePending();
    this.customerId = Objects.requireNonNull(customerId, "customer id must not be null");
    this.status = OnboardingStatus.APPROVED;
  }

  /**
   * Records that the application was refused, and why, in the terms the applicant is told.
   *
   * @throws OnboardingAlreadySettledException if it had already been approved or refused
   */
  public void reject(RejectionReason reason) {
    requirePending();
    this.reason = Objects.requireNonNull(reason, "rejection reason must not be null");
    this.status = OnboardingStatus.REJECTED;
  }

  public boolean isPending() {
    return status.isPending();
  }

  public boolean isSettled() {
    return status.isSettled();
  }

  public OnboardingId id() {
    return id;
  }

  public FullName fullName() {
    return fullName;
  }

  public Cpf cpf() {
    return cpf;
  }

  public Email email() {
    return email;
  }

  public LocalDate birthDate() {
    return birthDate;
  }

  public MonthlyIncome monthlyIncome() {
    return monthlyIncome;
  }

  public PasswordHash passwordHash() {
    return passwordHash;
  }

  public OnboardingStatus status() {
    return status;
  }

  public Optional<RejectionReason> reason() {
    return Optional.ofNullable(reason);
  }

  public Optional<CustomerId> customerId() {
    return Optional.ofNullable(customerId);
  }

  private void requirePending() {
    if (status.isSettled()) {
      throw new OnboardingAlreadySettledException(id, status);
    }
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Onboarding onboarding && id.equals(onboarding.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  /** Identifying data is masked so printing an application cannot leak it. */
  @Override
  public String toString() {
    return "Onboarding[" + id + " " + cpf.masked() + " " + status + "]";
  }
}
