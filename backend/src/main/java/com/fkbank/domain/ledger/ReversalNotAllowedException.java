package com.fkbank.domain.ledger;

/**
 * The posting cannot be reversed: it has been reversed already, or it is itself a reversal.
 *
 * <p>Both cases are refused because either one lets a second contra-posting land on an account
 * that tolerates a negative balance, inventing money that balances perfectly and so passes every
 * consistency check the ledger can run on itself.
 */
public class ReversalNotAllowedException extends LedgerException {

  public static final String CODE = "REVERSAL_NOT_ALLOWED";

  private final PostingId postingId;

  private ReversalNotAllowedException(PostingId postingId, String message) {
    super(CODE, message);
    this.postingId = postingId;
  }

  public static ReversalNotAllowedException alreadyReversed(PostingId postingId) {
    return new ReversalNotAllowedException(
        postingId, "posting %s has already been reversed".formatted(postingId));
  }

  public static ReversalNotAllowedException isItselfAReversal(PostingId postingId) {
    return new ReversalNotAllowedException(
        postingId, "posting %s is a reversal and cannot be reversed".formatted(postingId));
  }

  public PostingId postingId() {
    return postingId;
  }
}
