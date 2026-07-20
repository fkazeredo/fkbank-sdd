package com.fkbank.infra.persistence.ledger;

import com.fkbank.domain.ledger.AccountId;
import com.fkbank.domain.ledger.AccountKind;
import com.fkbank.domain.ledger.Balance;
import com.fkbank.domain.ledger.BalanceRepository;
import com.fkbank.domain.ledger.Money;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** Stores balances and serializes access to the accounts a posting touches. */
@Repository
class JpaBalanceRepository implements BalanceRepository {

  private final BalanceJpaRepository balances;
  private final AccountJpaRepository accounts;

  JpaBalanceRepository(BalanceJpaRepository balances, AccountJpaRepository accounts) {
    this.balances = balances;
    this.accounts = accounts;
  }

  @Override
  public Balance lockForUpdate(AccountId accountId) {
    BalanceEntity entity =
        balances
            .lockByAccountId(accountId.value())
            .orElseThrow(() -> new NoSuchElementException("no balance for account " + accountId));
    return Balance.existing(accountId, kindOf(accountId), Money.of(entity.getAmount()));
  }

  @Override
  public Optional<Balance> find(AccountId accountId) {
    return balances
        .findById(accountId.value())
        .map(
            entity ->
                Balance.existing(accountId, kindOf(accountId), Money.of(entity.getAmount())));
  }

  @Override
  public void save(Balance balance) {
    BalanceEntity entity =
        balances
            .findById(balance.accountId().value())
            .orElseThrow(
                () ->
                    new NoSuchElementException(
                        "no balance for account " + balance.accountId()));
    entity.setAmount(balance.amount().amount());
    balances.save(entity);
  }

  @Override
  public void create(Balance balance) {
    balances.save(
        new BalanceEntity(
            balance.accountId().value(),
            balance.amount().amount(),
            balance.amount().currency().getCurrencyCode()));
  }

  @Override
  public List<Balance> findAll() {
    Map<Long, AccountKind> kinds =
        accounts.findAll().stream()
            .collect(Collectors.toMap(AccountEntity::getId, AccountEntity::getKind));
    return balances.findAll().stream()
        .map(
            entity ->
                Balance.existing(
                    AccountId.of(entity.getAccountId()),
                    kinds.get(entity.getAccountId()),
                    Money.of(entity.getAmount())))
        .toList();
  }

  private AccountKind kindOf(AccountId accountId) {
    return accounts
        .findById(accountId.value())
        .map(AccountEntity::getKind)
        .orElseThrow(() -> new NoSuchElementException("no account " + accountId));
  }
}
