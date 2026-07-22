package com.fkbank.domain.ledger;

/** Which leg of a posting a given account was on. */
public enum Direction {

  /** The account was the posting's debit leg — money left it. */
  DEBIT,

  /** The account was the posting's credit leg — money arrived. */
  CREDIT
}
