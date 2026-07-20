package com.fkbank.domain.ledger;

/** No posting exists with the given identity. */
public class UnknownPostingException extends LedgerException {

  public static final String CODE = "UNKNOWN_POSTING";

  private final PostingId postingId;

  public UnknownPostingException(PostingId postingId) {
    super(CODE, "no posting %s".formatted(postingId));
    this.postingId = postingId;
  }

  public PostingId postingId() {
    return postingId;
  }
}
