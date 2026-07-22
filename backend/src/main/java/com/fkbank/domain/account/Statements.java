package com.fkbank.domain.account;

import com.fkbank.domain.customer.Cpf;
import com.fkbank.domain.customer.Customer;
import com.fkbank.domain.customer.CustomerId;
import com.fkbank.domain.customer.CustomerRepository;
import com.fkbank.domain.ledger.AccountId;
import com.fkbank.domain.ledger.Direction;
import com.fkbank.domain.ledger.Ledger;
import com.fkbank.domain.ledger.Posting;
import com.fkbank.domain.ledger.PostingId;
import com.fkbank.domain.ledger.PostingLine;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A customer's statement and the receipts each of its lines proves.
 *
 * <p>Both are computed on request from the ledger's own postings and the chart of accounts that
 * already names what each account is for — never from a table of their own (M3). A statement or
 * a receipt that disagreed with the ledger would be a second truth about money moved, which is
 * exactly what the accounting core exists to make impossible.
 *
 * <p>Reaches the ledger only through {@link Ledger}, its single entry point, the same rule
 * {@link CurrentAccounts} follows for balances.
 */
public class Statements {

  private static final String CUSTOMER_AVAILABLE_PREFIX = "customer:available:";

  private final CurrentAccountRepository accounts;
  private final CustomerRepository customers;
  private final Ledger ledger;

  public Statements(
      CurrentAccountRepository accounts, CustomerRepository customers, Ledger ledger) {
    this.accounts = Objects.requireNonNull(accounts);
    this.customers = Objects.requireNonNull(customers);
    this.ledger = Objects.requireNonNull(ledger);
  }

  /**
   * One page of {@code customerId}'s statement.
   *
   * @throws UnknownAccountException if the customer holds no account
   */
  public StatementPage statementOf(
      CustomerId customerId, StatementFilter filter, Optional<StatementCursor> cursor, int pageSize) {
    AccountId accountId = ledgerAccountIdOf(customerId);

    // One extra row is asked for so a page landing exactly on the last line can tell "no more
    // lines" from "the next page happens to be empty" without a second round trip.
    List<PostingLine> fetched =
        ledger.statementOf(
            accountId,
            filter.from(),
            filter.to(),
            filter.direction(),
            cursor.map(StatementCursor::occurredAt).orElse(null),
            cursor.map(StatementCursor::postingId).orElse(null),
            pageSize + 1);

    boolean hasMore = fetched.size() > pageSize;
    List<PostingLine> page = hasMore ? fetched.subList(0, pageSize) : fetched;
    Optional<StatementCursor> next =
        hasMore
            ? Optional.of(cursorAfter(page.get(page.size() - 1)))
            : Optional.empty();

    return new StatementPage(page, next);
  }

  /**
   * The receipt for one movement, from {@code customerId}'s own perspective.
   *
   * @throws UnknownReceiptException if no such movement exists, or {@code customerId} was not a
   *     party to it
   */
  public Receipt receiptOf(CustomerId customerId, PostingId postingId) {
    AccountId accountId = ledgerAccountIdOf(customerId);
    Posting posting =
        ledger
            .findPosting(postingId)
            .orElseThrow(() -> new UnknownReceiptException(postingId.toString()));

    Direction direction;
    AccountId counterpartyAccountId;
    if (posting.debitAccountId().equals(accountId)) {
      direction = Direction.DEBIT;
      counterpartyAccountId = posting.creditAccountId();
    } else if (posting.creditAccountId().equals(accountId)) {
      direction = Direction.CREDIT;
      counterpartyAccountId = posting.debitAccountId();
    } else {
      throw new UnknownReceiptException(postingId.toString());
    }

    Rail rail =
        Rail.of(codeOf(posting.debitAccountId()), codeOf(posting.creditAccountId()));
    // A reversal is never itself reversed (M1), so only an original posting can carry the
    // REVERSED status - and only once a contra-posting for it actually exists.
    boolean wasReversed = !posting.isReversal() && ledger.isReversed(postingId);
    ReceiptStatus status = wasReversed ? ReceiptStatus.REVERSED : ReceiptStatus.COMPLETED;

    return new Receipt(
        postingId,
        posting.occurredAt(),
        posting.amount(),
        direction,
        rail,
        status,
        counterpartyCpfOf(counterpartyAccountId));
  }

  private AccountId ledgerAccountIdOf(CustomerId customerId) {
    CurrentAccount account =
        accounts.findByCustomerId(customerId).orElseThrow(() -> new UnknownAccountException(customerId));
    return ledger.accountIdOf(account.ledgerAccountCode());
  }

  private String codeOf(AccountId accountId) {
    return ledger
        .codeOf(accountId)
        .orElseThrow(() -> new IllegalStateException("posting references unknown account " + accountId));
  }

  private Optional<String> counterpartyCpfOf(AccountId counterpartyAccountId) {
    String code = codeOf(counterpartyAccountId);
    if (!code.startsWith(CUSTOMER_AVAILABLE_PREFIX)) {
      return Optional.empty();
    }
    UUID counterpartyCustomerId = UUID.fromString(code.substring(CUSTOMER_AVAILABLE_PREFIX.length()));
    return customers.findById(CustomerId.of(counterpartyCustomerId)).map(Customer::cpf).map(Cpf::masked);
  }

  private static StatementCursor cursorAfter(PostingLine line) {
    return new StatementCursor(line.posting().occurredAt(), line.posting().id());
  }
}
