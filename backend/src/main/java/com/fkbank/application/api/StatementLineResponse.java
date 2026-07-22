package com.fkbank.application.api;

import com.fkbank.domain.ledger.Direction;
import com.fkbank.domain.ledger.PostingLine;

/**
 * One statement line. {@code amount} and {@code runningBalance} travel as decimal strings, like
 * every other amount this API sends — see {@link AccountResponse}.
 */
public record StatementLineResponse(
    String postingId,
    String occurredAt,
    String amount,
    String currency,
    String direction,
    String runningBalance) {

  static StatementLineResponse of(PostingLine line) {
    return new StatementLineResponse(
        line.posting().id().toString(),
        line.posting().occurredAt().toString(),
        line.posting().amount().atEdge().toPlainString(),
        line.posting().amount().currency().getCurrencyCode(),
        line.direction() == Direction.CREDIT ? "IN" : "OUT",
        line.runningBalance().atEdge().toPlainString());
  }
}
