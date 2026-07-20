package com.fkbank.domain.identity;

/** The submitted password does not meet the strength rule. */
public class WeakPasswordException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** The stable code carried to the edge. */
  public static final String CODE = "WEAK_PASSWORD";

  public WeakPasswordException(String message) {
    super(message);
  }

  public String code() {
    return CODE;
  }
}
