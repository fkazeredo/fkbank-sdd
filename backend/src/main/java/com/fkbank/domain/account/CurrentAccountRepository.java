package com.fkbank.domain.account;

import com.fkbank.domain.customer.CustomerId;
import java.util.Optional;

/** Stores and retrieves current accounts. */
public interface CurrentAccountRepository {

  Optional<CurrentAccount> findByCustomerId(CustomerId customerId);

  boolean existsForCustomer(CustomerId customerId);

  CurrentAccount save(CurrentAccount account);
}
