package com.fkbank.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.fkbank.testsupport.LedgerFixture;
import com.fkbank.testsupport.LedgerIntegrationTest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The trial balance, checked against a ledger that has actually been corrupted.
 *
 * <p>Corruption is applied with raw SQL, going around the ledger entirely — the only way to reach
 * the state the routine exists to detect, since the domain refuses to produce it.
 */
@DisplayName("Ledger - trial balance")
class LedgerVerificationIT extends LedgerIntegrationTest {

  @Autowired private Ledger ledger;
  @Autowired private LedgerFixture fixture;
  @Autowired private DataSource dataSource;

  @Test
  @DisplayName("flags an account whose saved balance no longer matches its postings")
  void flagsADriftedAccount() throws Exception {
    Account from = fixture.customerAccountHolding("100.00");
    Account to = fixture.emptyCustomerAccount();
    ledger.record(from.id(), to.id(), Money.of("25.00"));

    assertThat(ledger.trialBalance().driftedAccounts())
        .as("nothing has been tampered with yet")
        .doesNotContain(to.id());

    BigDecimal honest = ledger.balanceOf(to.id()).amount();
    try {
      overwriteBalance(to, new BigDecimal("999.0000"));

      TrialBalance trial = ledger.trialBalance();

      assertThat(trial.driftedAccounts())
          .as("the saved balance says 999 while the postings say 25")
          .contains(to.id());
      assertThat(trial.isConsistent()).isFalse();
      assertThat(trial.isBalanced())
          .as("debits still equal credits - a drifted balance is invisible to that check alone,"
              + " which is exactly why the per-account comparison exists")
          .isTrue();
    } finally {
      overwriteBalance(to, honest);
    }

    assertThat(ledger.trialBalance().driftedAccounts()).doesNotContain(to.id());
  }

  @Test
  @DisplayName("flags nothing on a ledger nobody has tampered with")
  void flagsNothingOnACleanLedger() {
    Account from = fixture.customerAccountHolding("80.00");
    Account to = fixture.emptyCustomerAccount();
    ledger.record(from.id(), to.id(), Money.of("15.00"));
    ledger.record(to.id(), from.id(), Money.of("5.00"));

    TrialBalance trial = ledger.trialBalance();

    assertThat(trial.driftedAccounts()).isEmpty();
    assertThat(trial.isBalanced()).isTrue();
    assertThat(trial.isConsistent()).isTrue();
  }

  @Test
  @DisplayName("reports equal debit and credit totals - every leg has its counterpart")
  void debitsEqualCredits() {
    Account from = fixture.customerAccountHolding("60.00");
    Account to = fixture.emptyCustomerAccount();
    ledger.record(from.id(), to.id(), Money.of("20.00"));

    TrialBalance trial = ledger.trialBalance();

    assertThat(trial.totalDebits()).isEqualTo(trial.totalCredits());
    assertThat(trial.totalDebits().isPositive()).isTrue();
  }

  private void overwriteBalance(Account account, BigDecimal amount) throws Exception {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement("UPDATE balance SET amount = ? WHERE account_id = ?")) {
      statement.setBigDecimal(1, amount);
      statement.setLong(2, account.id().value());
      statement.executeUpdate();
    }
  }
}
