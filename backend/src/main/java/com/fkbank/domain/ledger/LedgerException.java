package com.fkbank.domain.ledger;

/**
 * A refusal the ledger makes on business grounds, as opposed to a bug or an outage.
 *
 * <p>Each carries a stable code so callers and clients can react to the reason without parsing a
 * message that translation or rewording may change.
 */
public abstract class LedgerException extends RuntimeException {

  private final String code;

  protected LedgerException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
