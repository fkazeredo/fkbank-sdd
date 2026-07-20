package com.fkbank.acceptance.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.fkbank.domain.ledger.Account;
import com.fkbank.domain.ledger.Ledger;
import com.fkbank.domain.ledger.Money;
import com.fkbank.domain.ledger.PostingRecorded;
import com.fkbank.testsupport.LedgerFixture;
import com.fkbank.testsupport.LedgerIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.ApplicationModuleListener;

/**
 * Whether a ledger fact is durable before anyone has handled it.
 *
 * <p>The suite already shows a publication being written and then completed. Completion is the
 * state that means a restart would <em>not</em> redeliver, so watching only the completed row
 * proves the round trip and not the guarantee: if the registry wrote nothing until the consumer
 * returned, the same observation would hold and a crash mid-handling would lose the fact silently.
 * This holds the consumer still and looks for the row while it is still outstanding.
 */
@DisplayName("Ledger outbox - durability before handling")
@Import(LedgerOutboxDurabilityIT.BlockingConsumer.class)
class LedgerOutboxDurabilityIT extends LedgerIntegrationTest {

  @Autowired private Ledger ledger;
  @Autowired private LedgerFixture fixture;
  @Autowired private DataSource dataSource;
  @Autowired private BlockingConsumer consumer;

  @AfterEach
  void releaseTheConsumer() {
    consumer.release();
  }

  @Test
  @DisplayName("the fact is on disk and outstanding before the consumer has returned")
  void theFactIsDurableBeforeItIsHandled() {
    Account from = fixture.customerAccountHolding("60.00");
    Account to = fixture.emptyCustomerAccount();

    ledger.record(from.id(), to.id(), Money.of("12.00"));

    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(publicationsOf(PostingRecorded.class.getName(), false))
                    .as(
                        "a fact that only reaches the table once its consumer succeeded is a fact"
                            + " a crash mid-handling loses without trace")
                    .isPositive());

    consumer.release();

    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(publicationsOf(PostingRecorded.class.getName(), true))
                    .as("and it is marked done once the consumer has actually handled it")
                    .isPositive());
  }

  private int publicationsOf(String eventType, boolean completed) throws Exception {
    String sql =
        "SELECT count(*) FROM event_publication WHERE event_type = ? AND completion_date IS "
            + (completed ? "NOT NULL" : "NULL");
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, eventType);
      try (ResultSet rows = statement.executeQuery()) {
        assertThat(rows.next()).isTrue();
        return rows.getInt(1);
      }
    }
  }

  /** A consumer that refuses to finish until the test lets it. */
  @TestConfiguration
  static class BlockingConsumer {

    private final CountDownLatch permission = new CountDownLatch(1);

    @ApplicationModuleListener
    void on(PostingRecorded event) throws InterruptedException {
      // Bounded so a mistake in the test cannot wedge the shared application context.
      permission.await(20, TimeUnit.SECONDS);
    }

    void release() {
      permission.countDown();
    }
  }
}
