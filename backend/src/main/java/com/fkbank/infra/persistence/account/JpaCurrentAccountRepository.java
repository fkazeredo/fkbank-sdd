package com.fkbank.infra.persistence.account;

import com.fkbank.domain.account.AccountNumber;
import com.fkbank.domain.account.CurrentAccount;
import com.fkbank.domain.account.CurrentAccountId;
import com.fkbank.domain.account.CurrentAccountRepository;
import com.fkbank.domain.customer.CustomerId;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Stores customers' accounts. */
@Repository
class JpaCurrentAccountRepository implements CurrentAccountRepository {

  private final CurrentAccountJpaRepository accounts;

  JpaCurrentAccountRepository(CurrentAccountJpaRepository accounts) {
    this.accounts = accounts;
  }

  @Override
  public Optional<CurrentAccount> findByCustomerId(CustomerId customerId) {
    return accounts
        .findByCustomerId(customerId.value())
        .map(JpaCurrentAccountRepository::toDomain);
  }

  @Override
  public boolean existsForCustomer(CustomerId customerId) {
    return accounts.existsByCustomerId(customerId.value());
  }

  @Override
  public CurrentAccount save(CurrentAccount account) {
    accounts.save(
        new CurrentAccountEntity(
            account.id().value(),
            account.customerId().value(),
            account.number().branch(),
            account.number().number(),
            account.openedAt()));
    return account;
  }

  private static CurrentAccount toDomain(CurrentAccountEntity entity) {
    return CurrentAccount.existing(
        CurrentAccountId.of(entity.getId()),
        CustomerId.of(entity.getCustomerId()),
        AccountNumber.of(entity.getBranch(), entity.getNumber()),
        entity.getOpenedAt());
  }
}
