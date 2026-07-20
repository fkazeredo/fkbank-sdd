package com.fkbank.domain.customer;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A person registered with the bank.
 *
 * <p>A class rather than a record because a customer has a lifecycle ahead of it — consents,
 * correction, the anonymization that data-protection erasure performs — and because the rule
 * that only an adult may hold an account belongs on the type that would otherwise be created
 * without it.
 *
 * <p>Age is checked against a date supplied by the caller rather than read from the system
 * clock. A rule about someone turning eighteen today has to be testable on the day it matters,
 * not only on the day the test happens to run.
 */
public final class Customer {

  /** The age at which a person may hold an account in their own name. */
  public static final int MINIMUM_AGE = 18;

  private final CustomerId id;
  private final FullName fullName;
  private final Cpf cpf;
  private final Email email;
  private final LocalDate birthDate;
  private final MonthlyIncome monthlyIncome;

  private Customer(
      CustomerId id,
      FullName fullName,
      Cpf cpf,
      Email email,
      LocalDate birthDate,
      MonthlyIncome monthlyIncome) {
    this.id = Objects.requireNonNull(id, "customer id must not be null");
    this.fullName = Objects.requireNonNull(fullName, "full name must not be null");
    this.cpf = Objects.requireNonNull(cpf, "cpf must not be null");
    this.email = Objects.requireNonNull(email, "email must not be null");
    this.birthDate = Objects.requireNonNull(birthDate, "birth date must not be null");
    this.monthlyIncome = Objects.requireNonNull(monthlyIncome, "monthly income must not be null");
  }

  /**
   * Registers a person, refusing anyone under age on the given day.
   *
   * @param today the day the registration is being judged against
   * @throws UnderageCustomerException if the person is not yet an adult on that day
   */
  public static Customer register(
      CustomerId id,
      FullName fullName,
      Cpf cpf,
      Email email,
      LocalDate birthDate,
      MonthlyIncome monthlyIncome,
      LocalDate today) {

    Objects.requireNonNull(birthDate, "birth date must not be null");
    Objects.requireNonNull(today, "today must not be null");
    if (birthDate.isAfter(today)) {
      throw new IllegalArgumentException("birth date must not be in the future");
    }
    if (!isAdult(birthDate, today)) {
      throw new UnderageCustomerException(MINIMUM_AGE);
    }
    return new Customer(id, fullName, cpf, email, birthDate, monthlyIncome);
  }

  /** Rebuilds a customer that already exists. */
  public static Customer existing(
      CustomerId id,
      FullName fullName,
      Cpf cpf,
      Email email,
      LocalDate birthDate,
      MonthlyIncome monthlyIncome) {
    return new Customer(id, fullName, cpf, email, birthDate, monthlyIncome);
  }

  /**
   * Whether someone born on {@code birthDate} is an adult on {@code today}.
   *
   * <p>Whole years elapsed, so the boundary falls the way people expect: someone turning eighteen
   * today is eighteen today, not tomorrow.
   *
   * <p>Someone born on 29 February has no anniversary in three years out of four. Counting whole
   * elapsed years alone would make them wait until 1 March, a day later than everyone born in
   * February; the convention followed here is that the anniversary falls on the last day of the
   * month instead, so they come of age on 28 February. One day, once every four years — but it is
   * a real person being told they may not open an account on a day they are legally an adult.
   */
  public static boolean isAdult(LocalDate birthDate, LocalDate today) {
    // Adding the years, rather than counting the years between, is what produces that: adding
    // eighteen years to 29 February lands on 28 February in a non-leap year.
    return !today.isBefore(birthDate.plusYears(MINIMUM_AGE));
  }

  public CustomerId id() {
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

  @Override
  public boolean equals(Object other) {
    return other instanceof Customer customer && id.equals(customer.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  /** Identifying data is masked so printing a customer cannot leak it. */
  @Override
  public String toString() {
    return "Customer[" + id + " " + cpf.masked() + "]";
  }
}
