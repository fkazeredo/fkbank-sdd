package com.fkbank.domain.account;

import com.fkbank.domain.customer.CustomerId;

/** The customer asked about does not hold an account. */
public class UnknownAccountException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** The stable code carried to the edge. */
  public static final String CODE = "UNKNOWN_ACCOUNT";

  public UnknownAccountException(CustomerId customerId) {
    super("customer " + customerId + " holds no account");
  }

  public String code() {
    return CODE;
  }
}
