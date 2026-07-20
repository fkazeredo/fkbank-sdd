package com.fkbank.domain.customer;

/**
 * A refusal this context is responsible for.
 *
 * <p>Each carries a stable code, so the reason survives translation and the edge can turn it
 * into a response without matching on message text that is free to change.
 */
public abstract class CustomerException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String code;

  protected CustomerException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
