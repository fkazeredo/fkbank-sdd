package com.fkbank.domain.customer;

/**
 * Someone already holds an account with this CPF or e-mail address.
 *
 * <p>The message names which of the two collided, because that is what tells the person whether
 * to sign in or to use a different address. It never echoes the value itself, which would
 * confirm to an unauthenticated caller that a particular person banks here.
 */
public class DuplicateCustomerException extends CustomerException {

  private static final long serialVersionUID = 1L;

  /** The stable code carried to the edge. */
  public static final String CODE = "DUPLICATE_CUSTOMER";

  private DuplicateCustomerException(String message) {
    super(CODE, message);
  }

  public static DuplicateCustomerException forCpf() {
    return new DuplicateCustomerException("a customer with this cpf already exists");
  }

  public static DuplicateCustomerException forEmail() {
    return new DuplicateCustomerException("a customer with this email already exists");
  }
}
