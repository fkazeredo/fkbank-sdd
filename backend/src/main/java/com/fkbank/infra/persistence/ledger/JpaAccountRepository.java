package com.fkbank.infra.persistence.ledger;

import com.fkbank.domain.ledger.Account;
import com.fkbank.domain.ledger.AccountId;
import com.fkbank.domain.ledger.AccountKind;
import com.fkbank.domain.ledger.AccountRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Stores the chart of accounts. */
@Repository
class JpaAccountRepository implements AccountRepository {

  private final AccountJpaRepository accounts;

  JpaAccountRepository(AccountJpaRepository accounts) {
    this.accounts = accounts;
  }

  @Override
  public Optional<Account> findById(AccountId id) {
    return accounts.findById(id.value()).map(JpaAccountRepository::toDomain);
  }

  @Override
  public Optional<Account> findByCode(String code) {
    return accounts.findByCode(code).map(JpaAccountRepository::toDomain);
  }

  @Override
  public Account open(AccountKind kind, String code) {
    return toDomain(accounts.save(new AccountEntity(code, kind)));
  }

  private static Account toDomain(AccountEntity entity) {
    return Account.existing(AccountId.of(entity.getId()), entity.getKind(), entity.getCode());
  }
}
