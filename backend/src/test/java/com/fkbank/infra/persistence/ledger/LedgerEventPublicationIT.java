package com.fkbank.infra.persistence.ledger;

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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.ApplicationModuleListener;

/**
 * The outbox base, exercised rather than described.
 *
 * <p>Asserting that the table has the right columns only proves the migration matches a copy of
 * the framework's DDL that was pasted into a test. It says nothing about whether the framework
 * accepts the table it was handed. This publishes a real ledger fact through a real module
 * listener and watches a row appear and then be marked complete — which is the only thing that
 * demonstrates a business fact would survive a restart.
 */
@DisplayName("Ledger event publication")
@Import(LedgerEventPublicationIT.RecordingConsumer.class)
class LedgerEventPublicationIT extends LedgerIntegrationTest {

  @Autowired private Ledger ledger;
  @Autowired private LedgerFixture fixture;
  @Autowired private DataSource dataSource;
  @Autowired private RecordingConsumer consumer;

  @Test
  @DisplayName("persists a posting fact and completes it once the consumer has handled it")
  void persistsAndCompletesAPublication() {
    Account from = fixture.customerAccountHolding("40.00");
    Account to = fixture.emptyCustomerAccount();

    ledger.record(from.id(), to.id(), Money.of("15.00"));

    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(consumer.received())
                    .as("the module listener must actually be invoked, or nothing is persisted")
                    .isNotEmpty());

    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(completedPublicationsOf(PostingRecorded.class.getName()))
                    .as("a handled publication is marked complete; one left incomplete is what a"
                        + " restart would redeliver")
                    .isPositive());
  }

  private int completedPublicationsOf(String eventType) throws Exception {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                "SELECT count(*) FROM event_publication"
                    + " WHERE event_type = ? AND completion_date IS NOT NULL")) {
      statement.setString(1, eventType);
      try (ResultSet rows = statement.executeQuery()) {
        assertThat(rows.next()).isTrue();
        return rows.getInt(1);
      }
    }
  }

  /**
   * A consumer that exists only so the registry has someone to deliver to.
   *
   * <p>The registry persists a publication per module listener; with no listener anywhere in the
   * application it writes nothing at all, and the table would look healthy while proving nothing.
   * The product's first real consumer arrives with the notifications slice.
   */
  @TestConfiguration
  static class RecordingConsumer {

    private final List<PostingRecorded> received = new CopyOnWriteArrayList<>();

    @ApplicationModuleListener
    void on(PostingRecorded event) {
      received.add(event);
    }

    List<PostingRecorded> received() {
      return List.copyOf(received);
    }
  }
}
