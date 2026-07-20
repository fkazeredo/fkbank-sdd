package com.fkbank.domain.ledger;

import java.util.List;
import java.util.Optional;

/** Stores balances and serializes the accounts a posting is about to touch. */
public interface BalanceRepository {

  /**
   * Reads a balance and holds it against concurrent modification until the surrounding
   * transaction ends.
   *
   * <p>Callers must acquire locks in ascending account id order. Two postings moving money in
   * opposite directions between the same pair of accounts would otherwise each wait on the lock
   * the other one holds.
   */
  Balance lockForUpdate(AccountId accountId);

  /** Reads a balance without holding it. For reporting, never for deciding a movement. */
  Optional<Balance> find(AccountId accountId);

  void save(Balance balance);

  void create(Balance balance);

  List<Balance> findAll();
}
