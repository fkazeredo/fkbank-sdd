package com.fkbank.domain.account;

/** Whether the movement a receipt proves still stands, or was corrected by a contra-posting. */
public enum ReceiptStatus {
  COMPLETED,
  REVERSED
}
