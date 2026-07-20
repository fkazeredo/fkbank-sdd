package com.fkbank.domain.ledger;

import java.util.Optional;

/** Stores and retrieves the chart of accounts. */
public interface AccountRepository {

  Optional<Account> findById(AccountId id);

  Optional<Account> findByCode(String code);

  /** Persists a newly opened account and returns it with the identity the store assigned. */
  Account open(AccountKind kind, String code);
}
