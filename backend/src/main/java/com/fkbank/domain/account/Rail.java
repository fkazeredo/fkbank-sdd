package com.fkbank.domain.account;

/**
 * The money channel a movement travelled: pix, boleto, card, transfer, yield or credit (the
 * product's own ubiquitous language).
 *
 * <p>Never stored on a posting. The chart of accounts already names the channel a movement used —
 * {@code internal:settlement:pix}, {@code internal:expense:yield}, and so on — so a posting
 * carrying its own copy of that fact would be one more place it could drift from what the two
 * accounts it touched actually are.
 */
public enum Rail {
  PIX,
  BOLETO,
  CARD,
  TRANSFER,
  YIELD,
  CREDIT;

  private static final String SETTLEMENT_BOLETO = "internal:settlement:boleto";
  private static final String SETTLEMENT_PIX = "internal:settlement:pix";
  private static final String SETTLEMENT_CARD = "internal:settlement:card";
  private static final String EXPENSE_YIELD = "internal:expense:yield";
  private static final String CREDIT_PREFIX = "internal:credit:";

  /**
   * Classifies a posting by the two chart-of-accounts codes its legs carry.
   *
   * <p>Falls back to {@code TRANSFER} whenever neither leg is one of the named internal
   * accounts — true both for a movement between two customers' available balances and for a
   * movement between one customer's available balance and their own box, since neither is a
   * named external rail.
   */
  public static Rail of(String debitAccountCode, String creditAccountCode) {
    Rail fromDebit = fromCode(debitAccountCode);
    if (fromDebit != null) {
      return fromDebit;
    }
    Rail fromCredit = fromCode(creditAccountCode);
    return fromCredit != null ? fromCredit : TRANSFER;
  }

  private static Rail fromCode(String code) {
    return switch (code) {
      case SETTLEMENT_BOLETO -> BOLETO;
      case SETTLEMENT_PIX -> PIX;
      case SETTLEMENT_CARD -> CARD;
      case EXPENSE_YIELD -> YIELD;
      default -> code.startsWith(CREDIT_PREFIX) ? CREDIT : null;
    };
  }
}
