package com.fkbank.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fkbank.testsupport.PostgresContainer;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Proves the migration pipeline against a real PostgreSQL 16.
 *
 * <p>Asserting on {@code flyway_schema_history} rather than on a mocked Flyway is the point: this
 * test fails if the database cannot be reached or if a migration does not apply cleanly.
 */
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("Flyway migrations")
class FlywayBaselineIT {

  @Autowired private DataSource dataSource;

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    PostgresContainer.registerDataSource(registry);
  }

  @Test
  @DisplayName("applies every migration in order and records each as successful")
  void appliesEveryMigrationSuccessfully() throws Exception {
    record AppliedMigration(String version, String description, boolean success) {}

    List<AppliedMigration> applied = new ArrayList<>();
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rows =
            statement.executeQuery(
                "SELECT version, description, success FROM flyway_schema_history"
                    + " WHERE version IS NOT NULL ORDER BY installed_rank")) {
      while (rows.next()) {
        applied.add(
            new AppliedMigration(
                rows.getString("version"),
                rows.getString("description"),
                rows.getBoolean("success")));
      }
    }

    assertThat(applied)
        .as("a migration that failed to apply leaves the schema in a state nothing else can trust")
        .isNotEmpty()
        .allSatisfy(migration -> assertThat(migration.success()).isTrue());

    assertThat(applied)
        .extracting(AppliedMigration::version)
        .as("migrations apply in version order, starting from the baseline")
        .startsWith("1")
        .isSorted();
  }

  @Test
  @DisplayName("creates the ledger tables the accounting core needs")
  void createsTheLedgerTables() throws Exception {
    List<String> tables = new ArrayList<>();
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rows =
            statement.executeQuery(
                "SELECT table_name FROM information_schema.tables"
                    + " WHERE table_schema = 'public'")) {
      while (rows.next()) {
        tables.add(rows.getString("table_name"));
      }
    }

    assertThat(tables).contains("account", "posting", "balance", "event_publication");
  }

  @Test
  @DisplayName("creates the tables that opening an account needs")
  void createsTheOnboardingTables() throws Exception {
    assertThat(publicTables())
        .contains("customer", "credential", "current_account", "onboarding");
  }

  @Test
  @DisplayName("allows only one pending application per CPF, and only while it is pending")
  void enforcesOnePendingApplicationPerCpf() throws Exception {
    record PartialIndex(String name, String definition) {}

    List<PartialIndex> indexes = new ArrayList<>();
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rows =
            statement.executeQuery(
                "SELECT indexname, indexdef FROM pg_indexes"
                    + " WHERE tablename = 'onboarding'")) {
      while (rows.next()) {
        indexes.add(new PartialIndex(rows.getString("indexname"), rows.getString("indexdef")));
      }
    }

    assertThat(indexes)
        .as(
            "the index is what settles two concurrent submissions for one person; without it the"
                + " application-level check loses the race silently")
        .anySatisfy(
            index -> {
              assertThat(index.definition()).contains("UNIQUE");
              assertThat(index.definition()).contains("cpf");
              assertThat(index.definition())
                  .as("a refused applicant must not be barred from applying again")
                  .contains("WHERE (status = 'PENDING'::text)");
            });
  }

  private List<String> publicTables() throws Exception {
    List<String> tables = new ArrayList<>();
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rows =
            statement.executeQuery(
                "SELECT table_name FROM information_schema.tables"
                    + " WHERE table_schema = 'public'")) {
      while (rows.next()) {
        tables.add(rows.getString("table_name"));
      }
    }
    return tables;
  }
}
