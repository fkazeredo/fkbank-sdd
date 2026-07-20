package com.fkbank.infra.persistence.customer;

import com.fkbank.domain.customer.Cpf;
import com.fkbank.domain.customer.Customer;
import com.fkbank.domain.customer.CustomerId;
import com.fkbank.domain.customer.CustomerRepository;
import com.fkbank.domain.customer.DuplicateCustomerException;
import com.fkbank.domain.customer.Email;
import com.fkbank.domain.customer.FullName;
import com.fkbank.domain.customer.MonthlyIncome;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/** Stores registered people. */
@Repository
class JpaCustomerRepository implements CustomerRepository {

  private final CustomerJpaRepository customers;

  JpaCustomerRepository(CustomerJpaRepository customers) {
    this.customers = customers;
  }

  @Override
  public Optional<Customer> findById(CustomerId id) {
    return customers.findById(id.value()).map(JpaCustomerRepository::toDomain);
  }

  @Override
  public Optional<Customer> findByCpf(Cpf cpf) {
    return customers.findByCpf(cpf.value()).map(JpaCustomerRepository::toDomain);
  }

  @Override
  public boolean existsByCpf(Cpf cpf) {
    return customers.existsByCpf(cpf.value());
  }

  @Override
  public boolean existsByEmail(Email email) {
    return customers.existsByEmail(email.value());
  }

  @Override
  public Customer save(Customer customer) {
    try {
      customers.saveAndFlush(
          new CustomerEntity(
              customer.id().value(),
              customer.fullName().value(),
              customer.cpf().value(),
              customer.email().value(),
              customer.birthDate(),
              customer.monthlyIncome().value()));
      return customer;
    } catch (DataIntegrityViolationException collision) {
      // Two registrations for the same person committed at the same instant, so the check the
      // caller made beforehand was true when it ran and false by the time this insert landed.
      // The database is what actually decides, and its answer is reported as the same refusal
      // the caller would have raised itself.
      throw duplicateFrom(collision);
    }
  }

  /**
   * Works out which uniqueness rule the store enforced.
   *
   * <p>The two collisions call for different advice — sign in versus use another address — so
   * the constraint name is read rather than guessed. An unrecognized one is reported as the CPF,
   * the rule that always applies, instead of being swallowed.
   */
  private static DuplicateCustomerException duplicateFrom(DataIntegrityViolationException cause) {
    String description =
        String.valueOf(cause.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
    DuplicateCustomerException duplicate =
        description.contains("email")
            ? DuplicateCustomerException.forEmail()
            : DuplicateCustomerException.forCpf();
    duplicate.initCause(cause);
    return duplicate;
  }

  private static Customer toDomain(CustomerEntity entity) {
    return Customer.existing(
        CustomerId.of(entity.getId()),
        FullName.of(entity.getFullName()),
        Cpf.of(entity.getCpf()),
        Email.of(entity.getEmail()),
        entity.getBirthDate(),
        MonthlyIncome.of(entity.getMonthlyIncome()));
  }
}
