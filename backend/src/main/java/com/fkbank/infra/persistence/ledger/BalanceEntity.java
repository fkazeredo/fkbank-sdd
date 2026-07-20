package com.fkbank.infra.persistence.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** How a balance is stored: one row per account, keyed by the account itself. */
@Entity
@Table(name = "balance")
class BalanceEntity {

  @Id
  @Column(name = "account_id")
  private Long accountId;

  @Column(nullable = false)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  protected BalanceEntity() {
    // for the persistence provider
  }

  BalanceEntity(Long accountId, BigDecimal amount, String currency) {
    this.accountId = accountId;
    this.amount = amount;
    this.currency = currency;
  }

  Long getAccountId() {
    return accountId;
  }

  BigDecimal getAmount() {
    return amount;
  }

  String getCurrency() {
    return currency;
  }

  void setAmount(BigDecimal amount) {
    this.amount = amount;
  }
}
