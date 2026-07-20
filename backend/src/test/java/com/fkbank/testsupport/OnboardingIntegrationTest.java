package com.fkbank.testsupport;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for the sign-up and account-opening integration tests, against the real PostgreSQL.
 *
 * <p>The datasource registration and the controllable bureau live here rather than being
 * repeated per class. Spring keys its context cache partly on the declared property sources and
 * imported configuration, so the same two lines written out in five classes produce five
 * application contexts and five connection pools for what is one configuration.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Import(ControllableBureau.Configuration.class)
public abstract class OnboardingIntegrationTest {

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    PostgresContainer.registerDataSource(registry);
  }
}
