package com.fkbank.domain.customer;

/** The applicant is not old enough to hold an account in their own name. */
public class UnderageCustomerException extends CustomerException {

  private static final long serialVersionUID = 1L;

  /** The stable code carried to the edge. */
  public static final String CODE = "UNDERAGE_CUSTOMER";

  public UnderageCustomerException(int minimumAge) {
    super(CODE, "a customer must be at least " + minimumAge + " years old");
  }
}
