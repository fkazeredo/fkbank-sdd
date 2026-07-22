package com.fkbank.domain.account;

/**
 * No receipt exists for the id asked about.
 *
 * <p>Also thrown when the movement exists but the caller was not one of its two parties: a party
 * who genuinely lost the id and a stranger guessing one see the same refusal, so which one is
 * true is never learnable from the response.
 */
public class UnknownReceiptException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public static final String CODE = "UNKNOWN_RECEIPT";

  public UnknownReceiptException(String postingId) {
    super("no receipt exists for movement " + postingId);
  }

  public String code() {
    return CODE;
  }
}
