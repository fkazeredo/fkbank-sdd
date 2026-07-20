package com.fkbank.infra.persistence.ledger;

import com.fkbank.domain.ledger.AccountKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** How an account is stored. */
@Entity
@Table(name = "account")
class AccountEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String code;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AccountKind kind;

  protected AccountEntity() {
    // for the persistence provider
  }

  AccountEntity(String code, AccountKind kind) {
    this.code = code;
    this.kind = kind;
  }

  Long getId() {
    return id;
  }

  String getCode() {
    return code;
  }

  AccountKind getKind() {
    return kind;
  }
}
