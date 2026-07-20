package com.fkbank.testsupport;

import com.fkbank.domain.ledger.Account;
import com.fkbank.domain.ledger.AccountKind;
import com.fkbank.domain.ledger.Ledger;
import com.fkbank.domain.ledger.Money;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Opens funded accounts for the ledger integration tests.
 *
 * <p>Funding goes through the ledger itself rather than through direct inserts: a test that sets
 * up its world by writing balances by hand would be asserting against a state the product cannot
 * actually reach.
 */
@Component
public class LedgerFixture {

  private final Ledger ledger;

  public LedgerFixture(Ledger ledger) {
    this.ledger = ledger;
  }

  /** Opens a customer account holding nothing. */
  public Account emptyCustomerAccount() {
    return ledger.openAccount(AccountKind.CUSTOMER_AVAILABLE, uniqueCode("customer:available"));
  }

  /** Opens a customer account and funds it from the settlement account. */
  public Account customerAccountHolding(String amount) {
    Account account = emptyCustomerAccount();
    ledger.record(settlementAccount().id(), account.id(), Money.of(amount));
    return account;
  }

  /** Opens an internal settlement account, which is allowed to go negative. */
  public Account settlementAccount() {
    return ledger.openAccount(AccountKind.INTERNAL_SETTLEMENT, uniqueCode("internal:settlement"));
  }

  private static String uniqueCode(String prefix) {
    return prefix + ":" + UUID.randomUUID();
  }
}
