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
 * <p>Asserting on {@code flyway_schema_history} rather than on a mocked Flyway is the point:
 * this test fails if the database cannot be reached, if the baseline does not apply, or if a
 * second migration sneaks in without its own spec.
 */
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("Flyway baseline")
class FlywayBaselineIT {

  @Autowired private DataSource dataSource;

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    PostgresContainer.registerDataSource(registry);
  }

  @Test
  @DisplayName("applies exactly one migration - the V1 baseline - and records it as successful")
  void appliesOnlyTheBaselineMigration() throws Exception {
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
        .as("the walking skeleton owns exactly one migration; business tables arrive later,"
            + " each with its own migration")
        .singleElement()
        .satisfies(
            migration -> {
              assertThat(migration.version()).isEqualTo("1");
              assertThat(migration.success()).isTrue();
            });
  }

  @Test
  @DisplayName("creates no business tables - V1 is a marker, not a schema")
  void createsNoBusinessTables() throws Exception {
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

    assertThat(tables)
        .as("only Flyway's own bookkeeping table may exist at this point")
        .containsExactly("flyway_schema_history");
  }
}
