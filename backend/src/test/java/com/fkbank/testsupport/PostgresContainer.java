package com.fkbank.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The real PostgreSQL 16 the integration tests run against.
 *
 * <p>Pinned to the same major version as {@code compose.dev.yaml} and {@code compose.e2e.yaml}:
 * a migration that applies against a different engine version has proven nothing about the one
 * that actually runs.
 *
 * <p>One container is shared across the suite (started once, never stopped — Ryuk reaps it),
 * because booting a database per test class costs minutes and buys no isolation the schema does
 * not already give.
 */
public final class PostgresContainer {

  private static final PostgreSQLContainer INSTANCE =
      new PostgreSQLContainer("postgres:16")
          .withDatabaseName("app")
          .withUsername("app")
          .withPassword("app");

  static {
    INSTANCE.start();
  }

  private PostgresContainer() {}

  /** Points the application at the container. */
  public static void registerDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", INSTANCE::getJdbcUrl);
    registry.add("spring.datasource.username", INSTANCE::getUsername);
    registry.add("spring.datasource.password", INSTANCE::getPassword);
  }
}
