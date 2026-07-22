package com.fkbank.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fkbank.domain.customer.CustomerId;
import com.fkbank.domain.ledger.AccountId;
import com.fkbank.domain.ledger.Direction;
import com.fkbank.domain.ledger.Ledger;
import com.fkbank.domain.ledger.Money;
import com.fkbank.domain.ledger.Posting;
import com.fkbank.domain.ledger.PostingId;
import com.fkbank.domain.ledger.PostingLine;
import com.fkbank.testsupport.LedgerIntegrationTest;
import com.fkbank.testsupport.OnboardingFixture;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@link Statements} against real PostgreSQL: the running-balance derivation, keyset pagination,
 * and rail/status/counterparty classification, all through the same query the API route calls.
 *
 * <p>{@code StatementControllerIT} proves the HTTP contract; this proves the harder reasoning
 * underneath it — the Roadmap's own watchlist risk for this slice.
 */
@DisplayName("Statements")
class StatementsIT extends LedgerIntegrationTest {

  @Autowired private Statements statements;
  @Autowired private Ledger ledger;
  @Autowired private OnboardingFixture onboarding;

  @Test
  @DisplayName("BR-1: newest first, each line's running balance accumulates chronologically")
  void runningBalanceAccumulatesChronologically() {
    CustomerId customerId = onboarding.approvedCustomer().customerId();
    AccountId customerAccount = ledgerAccountOf(customerId);
    AccountId settlement = ledger.accountIdOf("internal:settlement:boleto");

    ledger.record(settlement, customerAccount, Money.of("10.00"));
    ledger.record(settlement, customerAccount, Money.of("5.00"));
    ledger.record(customerAccount, settlement, Money.of("3.00"));

    StatementPage page =
        statements.statementOf(
            customerId, StatementFilter.of(Instant.EPOCH, farFuture(), null), Optional.empty(), 10);

    assertThat(page.lines()).hasSize(3);
    assertThat(page.lines().stream().map(line -> line.runningBalance().atEdge().toPlainString()))
        .as("newest first: 12.00 (transfer out), 15.00, 10.00")
        .containsExactly("12.00", "15.00", "10.00");
    assertThat(page.lines().get(0).runningBalance())
        .as("the newest line's running balance is the account's actual balance")
        .isEqualTo(ledger.balanceOf(customerAccount));
    assertThat(page.nextCursor()).isEmpty();
  }

  @Test
  @DisplayName("pagination: paging one line at a time visits every line exactly once, in order")
  void pagesOneLineAtATimeWithNoDuplicateOrGap() {
    CustomerId customerId = onboarding.approvedCustomer().customerId();
    AccountId customerAccount = ledgerAccountOf(customerId);
    AccountId settlement = ledger.accountIdOf("internal:settlement:boleto");

    List<PostingId> written = new ArrayList<>();
    for (int i = 1; i <= 5; i++) {
      written.add(ledger.record(settlement, customerAccount, Money.of("1.00")).id());
    }

    List<PostingId> visited = new ArrayList<>();
    Optional<StatementCursor> cursor = Optional.empty();
    StatementFilter filter = StatementFilter.of(Instant.EPOCH, farFuture(), null);
    StatementPage page;
    do {
      page = statements.statementOf(customerId, filter, cursor, 1);
      page.lines().forEach(line -> visited.add(line.posting().id()));
      cursor = page.nextCursor();
    } while (cursor.isPresent());

    List<PostingId> expectedNewestFirst = new ArrayList<>(written);
    java.util.Collections.reverse(expectedNewestFirst);
    assertThat(visited).containsExactlyElementsOf(expectedNewestFirst);
  }

  @Test
  @DisplayName("direction filter: OUT shows only the account's debit legs")
  void filtersByDirection() {
    CustomerId customerId = onboarding.approvedCustomer().customerId();
    AccountId customerAccount = ledgerAccountOf(customerId);
    AccountId settlement = ledger.accountIdOf("internal:settlement:boleto");

    ledger.record(settlement, customerAccount, Money.of("20.00"));
    Posting transferOut = ledger.record(customerAccount, settlement, Money.of("4.00"));

    StatementPage page =
        statements.statementOf(
            customerId,
            StatementFilter.of(Instant.EPOCH, farFuture(), Direction.DEBIT),
            Optional.empty(),
            10);

    assertThat(page.lines()).hasSize(1);
    assertThat(page.lines().get(0).posting().id()).isEqualTo(transferOut.id());
    assertThat(page.lines().get(0).direction()).isEqualTo(Direction.DEBIT);
  }

  @Test
  @DisplayName("period filter: a posting outside [from, to) is excluded")
  void filtersByPeriod() {
    CustomerId customerId = onboarding.approvedCustomer().customerId();
    AccountId customerAccount = ledgerAccountOf(customerId);
    AccountId settlement = ledger.accountIdOf("internal:settlement:boleto");
    ledger.record(settlement, customerAccount, Money.of("7.00"));

    StatementPage page =
        statements.statementOf(
            customerId,
            StatementFilter.of(Instant.EPOCH, Instant.EPOCH.plusSeconds(1), null),
            Optional.empty(),
            10);

    assertThat(page.lines()).isEmpty();
    assertThat(page.nextCursor()).isEmpty();
  }

  @Test
  @DisplayName("reversal rendering: the original shows REVERSED, the contra shows COMPLETED")
  void showsBothTheOriginalAndItsContra() {
    CustomerId customerId = onboarding.approvedCustomer().customerId();
    AccountId customerAccount = ledgerAccountOf(customerId);
    AccountId settlement = ledger.accountIdOf("internal:settlement:boleto");
    Posting original = ledger.record(settlement, customerAccount, Money.of("30.00"));

    Posting contra = ledger.reverse(original.id());

    Receipt originalReceipt = statements.receiptOf(customerId, original.id());
    Receipt contraReceipt = statements.receiptOf(customerId, contra.id());

    assertThat(originalReceipt.status()).isEqualTo(ReceiptStatus.REVERSED);
    assertThat(contraReceipt.status()).isEqualTo(ReceiptStatus.COMPLETED);

    StatementPage page =
        statements.statementOf(
            customerId, StatementFilter.of(Instant.EPOCH, farFuture(), null), Optional.empty(), 10);
    assertThat(page.lines().stream().map(line -> line.posting().id()))
        .contains(original.id(), contra.id());
  }

  @Test
  @DisplayName("a receipt for a peer transfer shows the counterparty's masked CPF")
  void showsCounterpartyOnATransfer() {
    OnboardingFixture.SignedUpCustomer payer = onboarding.approvedCustomer();
    OnboardingFixture.SignedUpCustomer payee = onboarding.approvedCustomer();
    AccountId payerAccount = ledgerAccountOf(payer.customerId());
    AccountId payeeAccount = ledgerAccountOf(payee.customerId());
    AccountId settlement = ledger.accountIdOf("internal:settlement:boleto");
    ledger.record(settlement, payerAccount, Money.of("50.00"));

    Posting transfer = ledger.record(payerAccount, payeeAccount, Money.of("12.00"));

    Receipt receipt = statements.receiptOf(payer.customerId(), transfer.id());

    assertThat(receipt.rail()).isEqualTo(Rail.TRANSFER);
    assertThat(receipt.counterparty()).isPresent();
    assertThat(receipt.counterparty().get()).contains("***.").contains("-**");
  }

  @Test
  @DisplayName("refuses a receipt the customer was not a party to")
  void refusesAForeignReceipt() {
    CustomerId owner = onboarding.approvedCustomer().customerId();
    CustomerId stranger = onboarding.approvedCustomer().customerId();
    AccountId ownerAccount = ledgerAccountOf(owner);
    AccountId settlement = ledger.accountIdOf("internal:settlement:boleto");
    Posting posting = ledger.record(settlement, ownerAccount, Money.of("9.00"));

    assertThatThrownBy(() -> statements.receiptOf(stranger, posting.id()))
        .isInstanceOf(UnknownReceiptException.class);
  }

  @Test
  @DisplayName("an account with no postings has an empty statement")
  void emptyStatementForAFreshAccount() {
    CustomerId customerId = onboarding.approvedCustomer().customerId();

    StatementPage page =
        statements.statementOf(
            customerId, StatementFilter.of(Instant.EPOCH, farFuture(), null), Optional.empty(), 10);

    assertThat(page.lines()).isEmpty();
    assertThat(page.nextCursor()).isEmpty();
  }

  private AccountId ledgerAccountOf(CustomerId customerId) {
    return ledger.accountIdOf(CurrentAccount.ledgerAccountCodeFor(customerId));
  }

  private static Instant farFuture() {
    return Instant.parse("9999-01-01T00:00:00Z");
  }
}
