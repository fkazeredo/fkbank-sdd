package com.fkbank.infra.persistence.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fkbank.testsupport.LedgerIntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The guarantees the database makes on its own, tested by going around the application entirely.
 *
 * <p>Every assertion here uses raw JDBC. The point is what happens when someone bypasses the
 * ledger — a support script, a migration, a future mistake — because that is exactly the case the
 * Java-side checks cannot cover.
 */
@DisplayName("Ledger schema")
class LedgerSchemaIT extends LedgerIntegrationTest {

  @Autowired private DataSource dataSource;

  @Test
  @DisplayName("seeds the internal side of the chart of accounts, each with a zero balance")
  void seedsTheChartOfAccounts() throws Exception {
    // Restricted to the seeded codes on purpose: other tests in this suite open accounts of
    // their own, and a bare count would pass or fail depending on what ran first.
    List<String> codes = new ArrayList<>();
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rows =
            statement.executeQuery(
                """
                SELECT a.code FROM account a JOIN balance b ON b.account_id = a.id
                WHERE b.amount = 0 AND a.code IN (
                    'internal:settlement:boleto',
                    'internal:settlement:pix',
                    'internal:settlement:card',
                    'internal:expense:yield',
                    'internal:credit:disbursement')
                ORDER BY a.code
                """)) {
      while (rows.next()) {
        codes.add(rows.getString("code"));
      }
    }

    assertThat(codes)
        .containsExactly(
            "internal:credit:disbursement",
            "internal:expense:yield",
            "internal:settlement:boleto",
            "internal:settlement:card",
            "internal:settlement:pix");
  }

  @Test
  @DisplayName("refuses to update a posting - history is not editable")
  void refusesToUpdateAPosting() throws Exception {
    UUID posting = insertPosting("10.0000", null);

    assertThatThrownBy(() -> execute("UPDATE posting SET amount = 999 WHERE id = '" + posting + "'"))
        .hasMessageContaining("append-only");

    assertThat(amountOf(posting)).isEqualByComparingTo("10.0000");
  }

  @Test
  @DisplayName("refuses to delete a posting")
  void refusesToDeleteAPosting() throws Exception {
    UUID posting = insertPosting("10.0000", null);

    assertThatThrownBy(() -> execute("DELETE FROM posting WHERE id = '" + posting + "'"))
        .hasMessageContaining("append-only");

    assertThat(amountOf(posting)).isEqualByComparingTo("10.0000");
  }

  @Test
  @DisplayName("refuses a second contra-posting against the same original")
  void refusesASecondReversal() throws Exception {
    UUID original = insertPosting("10.0000", null);
    insertPosting("10.0000", original);

    assertThatThrownBy(() -> insertPosting("10.0000", original))
        .hasMessageContaining("posting_reverses_at_most_once_idx");
  }

  @Test
  @DisplayName("refuses a posting whose amount is not positive")
  void refusesNonPositiveAmounts() {
    assertThatThrownBy(() -> insertPosting("0.0000", null))
        .hasMessageContaining("posting_amount_positive");
    assertThatThrownBy(() -> insertPosting("-1.0000", null))
        .hasMessageContaining("posting_amount_positive");
  }

  @Test
  @DisplayName("refuses a posting from an account to itself")
  void refusesSelfPosting() {
    assertThatThrownBy(
            () ->
                execute(
                    """
                    INSERT INTO posting
                        (id, debit_account_id, credit_account_id, amount, currency, occurred_at)
                    SELECT gen_random_uuid(), a.id, a.id, 1, 'BRL', now()
                    FROM account a WHERE a.code = 'internal:settlement:pix'
                    """))
        .hasMessageContaining("posting_between_two_accounts");
  }

  @Test
  @DisplayName("owns the event publication table rather than creating it at startup")
  void ownsTheEventPublicationTable() throws Exception {
    List<String> columns = new ArrayList<>();
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rows =
            statement.executeQuery(
                "SELECT column_name FROM information_schema.columns"
                    + " WHERE table_name = 'event_publication' ORDER BY column_name")) {
      while (rows.next()) {
        columns.add(rows.getString("column_name"));
      }
    }

    assertThat(columns)
        .containsExactly(
            "completion_attempts",
            "completion_date",
            "event_type",
            "id",
            "last_resubmission_date",
            "listener_id",
            "publication_date",
            "serialized_event",
            "status");
  }

  private UUID insertPosting(String amount, UUID reverses) throws Exception {
    UUID id = UUID.randomUUID();
    String reversesValue = reverses == null ? "NULL" : "'" + reverses + "'";
    execute(
        """
        INSERT INTO posting
            (id, debit_account_id, credit_account_id, amount, currency, occurred_at,
             reverses_posting_id)
        SELECT '%s',
               (SELECT id FROM account WHERE code = 'internal:settlement:pix'),
               (SELECT id FROM account WHERE code = 'internal:settlement:boleto'),
               %s, 'BRL', now(), %s
        """
            .formatted(id, amount, reversesValue));
    return id;
  }

  private java.math.BigDecimal amountOf(UUID posting) throws Exception {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rows =
            statement.executeQuery("SELECT amount FROM posting WHERE id = '" + posting + "'")) {
      assertThat(rows.next()).isTrue();
      return rows.getBigDecimal("amount");
    }
  }

  private void execute(String sql) throws Exception {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }
}
