package com.fkbank.domain.account;

import com.fkbank.domain.customer.CustomerId;
import com.fkbank.domain.ledger.AccountKind;
import com.fkbank.domain.ledger.Ledger;
import com.fkbank.domain.ledger.Money;
import java.time.Clock;
import java.util.Objects;

/**
 * The accounts customers hold.
 *
 * <p>Opening an account is two facts that have to become true together: the customer has an
 * account number, and the chart of accounts has somewhere to record their money. Doing one
 * without the other leaves either an account nobody can pay into or a balance nobody owns, so
 * both happen here, inside one transaction applied from the outside.
 *
 * <p>The accounting core is reached only through its own entry point. Nothing here reads or
 * writes a balance directly, which is what keeps "what does this account hold" a question with
 * a single answer.
 */
public class CurrentAccounts {

  private final CurrentAccountRepository accounts;
  private final AccountNumbers numbers;
  private final AccountEventPublisher events;
  private final Ledger ledger;
  private final Clock clock;

  public CurrentAccounts(
      CurrentAccountRepository accounts,
      AccountNumbers numbers,
      AccountEventPublisher events,
      Ledger ledger,
      Clock clock) {
    this.accounts = Objects.requireNonNull(accounts);
    this.numbers = Objects.requireNonNull(numbers);
    this.events = Objects.requireNonNull(events);
    this.ledger = Objects.requireNonNull(ledger);
    this.clock = Objects.requireNonNull(clock);
  }

  /**
   * Opens an account for a customer who does not yet have one, at a zero balance.
   *
   * <p>Opening is idempotent: a customer who already has an account gets that account back
   * rather than a second one. The work that follows an approval can therefore be retried — by a
   * repeated callback, or by a retry after a failure partway through — without the retry being
   * the thing that creates a duplicate.
   *
   * <p>No posting is recorded. A posting moves a positive amount between two accounts, and
   * there is nothing yet to move; the account starts at zero because that is what an account
   * with no postings holds.
   */
  public CurrentAccount openFor(CustomerId customerId) {
    Objects.requireNonNull(customerId, "customer id must not be null");
    return accounts
        .findByCustomerId(customerId)
        .orElseGet(() -> open(customerId));
  }

  /**
   * The account and what it holds.
   *
   * @throws UnknownAccountException if the customer holds no account
   */
  public AccountSummary summaryOf(CustomerId customerId) {
    CurrentAccount account =
        accounts
            .findByCustomerId(customerId)
            .orElseThrow(() -> new UnknownAccountException(customerId));

    Money balance = ledger.balanceOf(account.ledgerAccountCode());
    return new AccountSummary(
        account.number(), balance.atEdge(), balance.currency().getCurrencyCode());
  }

  private CurrentAccount open(CustomerId customerId) {
    CurrentAccount account =
        CurrentAccount.openFor(
            CurrentAccountId.next(),
            customerId,
            AccountNumber.of(numbers.next()),
            clock.instant());

    ledger.openAccount(AccountKind.CUSTOMER_AVAILABLE, account.ledgerAccountCode());
    CurrentAccount saved = accounts.save(account);
    events.publish(AccountOpened.from(saved));
    return saved;
  }
}
