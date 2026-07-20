package com.fkbank.domain.customer;

import java.util.UUID;

/**
 * Identifies a customer.
 *
 * <p>A generated identifier rather than the CPF: the CPF is personal data subject to erasure,
 * and an identifier that other records point at must outlive the data it once described.
 */
public record CustomerId(UUID value) {

  public CustomerId {
    if (value == null) {
      throw new IllegalArgumentException("customer id must not be null");
    }
  }

  public static CustomerId next() {
    return new CustomerId(UUID.randomUUID());
  }

  public static CustomerId of(UUID value) {
    return new CustomerId(value);
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
