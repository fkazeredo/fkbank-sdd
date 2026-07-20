package com.fkbank.acceptance.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fkbank.domain.ledger.Account;
import com.fkbank.domain.ledger.Ledger;
import com.fkbank.domain.ledger.Money;
import com.fkbank.domain.ledger.TrialBalance;
import com.fkbank.testsupport.LedgerFixture;
import com.fkbank.testsupport.LedgerIntegrationTest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * What the audit does when a balance row is missing rather than wrong.
 *
 * <p>Every guard the schema places around {@code balance} is justified by the same sentence: the
 * trial balance would report the damage. That claim is only ever exercised against a balance that
 * holds the wrong number. A balance row that is gone is a different shape of damage, and the
 * account it belonged to still has postings behind it, so the two figures the audit compares no
 * longer both exist.
 */
@DisplayName("Ledger audit - a balance row that is missing rather than wrong")
class LedgerAuditBlindSpotIT extends LedgerIntegrationTest {

  @Autowired private Ledger ledger;
  @Autowired private LedgerFixture fixture;
  @Autowired private DataSource dataSource;

  @Test
  @DisplayName("flags an account whose balance row was deleted while its postings remain")
  void detectsADeletedBalanceRow() throws Exception {
    Account from = fixture.customerAccountHolding("80.00");
    Account to = fixture.emptyCustomerAccount();
    ledger.record(from.id(), to.id(), Money.of("25.00"));

    BigDecimal removed = balanceAmountOf(to.id().value());
    deleteBalanceRow(to.id().value());
    try {
      TrialBalance audit = ledger.trialBalance();

      assertThat(audit.driftedAccounts())
          .as(
              "the account still has 25.00 of postings behind it and no balance at all; an audit"
                  + " that misses that is an audit no balance guard can lean on")
          .contains(to.id());
      assertThat(audit.isConsistent())
          .as("the books cannot be consistent while an account's position has been erased")
          .isFalse();
    } finally {
      restoreBalanceRow(to.id().value(), removed);
    }
  }

  private BigDecimal balanceAmountOf(long accountId) throws Exception {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement("SELECT amount FROM balance WHERE account_id = ?")) {
      statement.setLong(1, accountId);
      try (ResultSet rows = statement.executeQuery()) {
        assertThat(rows.next()).as("the account must have a balance row to begin with").isTrue();
        return rows.getBigDecimal(1);
      }
    }
  }

  /**
   * Removes a balance row the only way it can still be removed: with the guard switched off.
   *
   * <p>The schema now refuses a plain {@code DELETE}, which is the right answer to how this state
   * is reached in practice. It is not an answer to whether the audit would notice the state if it
   * arrived by some other route — a restore, a superuser, a future migration, a guard disabled
   * exactly like this. Turning the trigger off is what lets this test ask that second question,
   * which is the one the audit exists to answer.
   */
  private void deleteBalanceRow(long accountId) throws Exception {
    try (Connection connection = dataSource.getConnection();
        Statement guard = connection.createStatement();
        PreparedStatement statement =
            connection.prepareStatement("DELETE FROM balance WHERE account_id = ?")) {
      guard.execute("ALTER TABLE balance DISABLE TRIGGER balance_no_delete");
      try {
        statement.setLong(1, accountId);
        assertThat(statement.executeUpdate()).isEqualTo(1);
      } finally {
        guard.execute("ALTER TABLE balance ENABLE TRIGGER balance_no_delete");
      }
    }
  }

  @Test
  @DisplayName("the schema refuses a plain DELETE on a balance row")
  void refusesToDeleteABalanceRow() throws Exception {
    Account from = fixture.customerAccountHolding("50.00");

    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement("DELETE FROM balance WHERE account_id = ?")) {
      statement.setLong(1, from.id().value());
      assertThatThrownBy(statement::executeUpdate).hasMessageContaining("append-only");
    }

    assertThat(ledger.balanceOf(from.id())).isEqualTo(Money.of("50.00"));
  }

  private void restoreBalanceRow(long accountId, BigDecimal amount) throws Exception {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                "INSERT INTO balance (account_id, amount, currency) VALUES (?, ?, 'BRL')")) {
      statement.setLong(1, accountId);
      statement.setBigDecimal(2, amount);
      statement.executeUpdate();
    }
  }
}
