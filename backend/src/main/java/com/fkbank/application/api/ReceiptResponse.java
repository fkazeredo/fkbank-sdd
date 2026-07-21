package com.fkbank.application.api;

import com.fkbank.domain.account.Receipt;
import com.fkbank.domain.ledger.Direction;

/** Public proof of one completed movement, from the caller's own perspective. */
public record ReceiptResponse(
    String id,
    String occurredAt,
    String amount,
    String currency,
    String direction,
    String rail,
    String status,
    String counterparty) {

  static ReceiptResponse of(Receipt receipt) {
    return new ReceiptResponse(
        receipt.id().toString(),
        receipt.occurredAt().toString(),
        receipt.amount().atEdge().toPlainString(),
        receipt.amount().currency().getCurrencyCode(),
        receipt.direction() == Direction.CREDIT ? "IN" : "OUT",
        receipt.rail().name(),
        receipt.status().name(),
        receipt.counterparty().orElse(null));
  }
}
