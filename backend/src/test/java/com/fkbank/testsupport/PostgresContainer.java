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

  /**
   * How many connections one test context may hold.
   *
   * <p>Spring caches a separate application context per distinct test configuration, and every
   * one of them opens its own pool against this single database. At the default pool size a
   * dozen contexts exhaust PostgreSQL's connection limit and later test classes fail to start
   * with "too many clients already" — a failure that points at whichever test happened to run
   * last rather than at the cause. Small pools keep the total well inside the limit while still
   * leaving room for the concurrency tests, which need more than one connection at a time.
   */
  private static final int MAX_POOL_SIZE_PER_CONTEXT = 5;

  /** Points the application at the container. */
  public static void registerDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", INSTANCE::getJdbcUrl);
    registry.add("spring.datasource.username", INSTANCE::getUsername);
    registry.add("spring.datasource.password", INSTANCE::getPassword);
    registry.add("spring.datasource.hikari.maximum-pool-size", () -> MAX_POOL_SIZE_PER_CONTEXT);
  }
}
