package com.fkbank.domain.customer;

import java.util.Optional;

/** Stores and retrieves registered people. */
public interface CustomerRepository {

  Optional<Customer> findById(CustomerId id);

  Optional<Customer> findByCpf(Cpf cpf);

  boolean existsByCpf(Cpf cpf);

  boolean existsByEmail(Email email);

  /**
   * Persists a newly registered customer.
   *
   * @throws DuplicateCustomerException if the store already holds this CPF or e-mail — the
   *     check callers make beforehand cannot see a registration committing at the same instant,
   *     so the store has the final say
   */
  Customer save(Customer customer);
}
