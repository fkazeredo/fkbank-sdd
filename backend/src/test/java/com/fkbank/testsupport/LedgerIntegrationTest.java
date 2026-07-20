package com.fkbank.testsupport;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for the ledger integration tests, against the real PostgreSQL.
 *
 * <p>The datasource registration lives here rather than being repeated in each test class on
 * purpose. Spring keys its context cache partly on the declared property sources, so the same
 * registration written out in five classes produces five application contexts and five
 * connection pools for what is one configuration. Inheriting it means the whole ledger suite
 * boots one context and shares it.
 */
@SpringBootTest
@ActiveProfiles("dev")
public abstract class LedgerIntegrationTest {

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    PostgresContainer.registerDataSource(registry);
  }
}
