package com.fkbank.acceptance.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fkbank.domain.ledger.Account;
import com.fkbank.domain.ledger.AccountKind;
import com.fkbank.domain.ledger.Ledger;
import com.fkbank.domain.ledger.Money;
import com.fkbank.testsupport.LedgerIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What the posting counter reports when a movement does not survive its transaction.
 *
 * <p>The counter exists so an operator can compare a dashboard against what customers actually
 * experienced. That comparison only works if a movement counted is a movement that committed.
 * A caller wrapping the ledger in its own transaction — which every money rail will do, since a
 * transfer is a PIN check and a limit check and then a posting — is the ordinary way a recorded
 * posting ends up rolled back.
 */
class LedgerMetricAcceptanceIT extends LedgerIntegrationTest {

  @Autowired private Ledger ledger;
  @Autowired private MeterRegistry meters;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;

  private double postingsCounted() {
    return Search.in(meters).name("fkbank.ledger.postings").tag("kind", "posting").counters()
        .stream()
        .mapToDouble(counter -> counter.count())
        .sum();
  }

  private long postingRows() {
    return jdbc.queryForObject("select count(*) from posting", Long.class);
  }

  @Test
  @DisplayName("a posting rolled back by the caller must not stay counted")
  void aRolledBackPostingIsNotCounted() {
    Account source =
        ledger.openAccount(AccountKind.INTERNAL_SETTLEMENT, "qa:internal:" + UUID.randomUUID());
    Account destination =
        ledger.openAccount(AccountKind.CUSTOMER_AVAILABLE, "qa:customer:" + UUID.randomUUID());

    double countedBefore = postingsCounted();
    long rowsBefore = postingRows();

    TransactionTemplate outerTransaction = new TransactionTemplate(transactionManager);
    assertThatThrownBy(
            () ->
                outerTransaction.executeWithoutResult(
                    status -> {
                      ledger.record(source.id(), destination.id(), Money.of("10.00"));
                      throw new IllegalStateException("the caller abandons the transaction");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(postingRows())
        .as("the rollback must leave no posting behind")
        .isEqualTo(rowsBefore);
    assertThat(ledger.balanceOf(destination.id()))
        .as("the rollback must leave no money behind")
        .isEqualTo(Money.zero());

    assertThat(postingsCounted())
        .as("a movement that never committed must not be reported as one that did")
        .isEqualTo(countedBefore);
  }

  @Test
  @DisplayName("a committed posting is counted exactly once")
  void aCommittedPostingIsCountedOnce() {
    Account source =
        ledger.openAccount(AccountKind.INTERNAL_SETTLEMENT, "qa:internal:" + UUID.randomUUID());
    Account destination =
        ledger.openAccount(AccountKind.CUSTOMER_AVAILABLE, "qa:customer:" + UUID.randomUUID());

    double countedBefore = postingsCounted();
    ledger.record(source.id(), destination.id(), Money.of("10.00"));

    assertThat(postingsCounted()).isEqualTo(countedBefore + 1);
  }
}
