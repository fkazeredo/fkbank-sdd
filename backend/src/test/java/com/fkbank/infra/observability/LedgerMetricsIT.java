package com.fkbank.infra.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fkbank.domain.ledger.Account;
import com.fkbank.domain.ledger.InsufficientFundsException;
import com.fkbank.domain.ledger.Ledger;
import com.fkbank.domain.ledger.Money;
import com.fkbank.testsupport.LedgerFixture;
import com.fkbank.testsupport.LedgerIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** What the posting counter does and does not count. */
@DisplayName("Ledger metrics")
class LedgerMetricsIT extends LedgerIntegrationTest {

  @Autowired private Ledger ledger;
  @Autowired private LedgerFixture fixture;
  @Autowired private MeterRegistry registry;

  @Test
  @DisplayName("counts a committed posting")
  void countsACommittedPosting() {
    Account from = fixture.customerAccountHolding("50.00");
    Account to = fixture.emptyCustomerAccount();
    double before = countOf("posting");

    ledger.record(from.id(), to.id(), Money.of("10.00"));

    assertThat(countOf("posting")).isEqualTo(before + 1);
  }

  @Test
  @DisplayName("counts a reversal apart from the movement it undoes")
  void countsReversalsSeparately() {
    Account from = fixture.customerAccountHolding("50.00");
    Account to = fixture.emptyCustomerAccount();
    double postingsBefore = countOf("posting");
    double reversalsBefore = countOf("reversal");

    ledger.reverse(ledger.record(from.id(), to.id(), Money.of("10.00")).id());

    assertThat(countOf("posting")).isEqualTo(postingsBefore + 1);
    assertThat(countOf("reversal")).isEqualTo(reversalsBefore + 1);
  }

  @Test
  @DisplayName("does not count a posting that was rolled back")
  void ignoresARolledBackPosting() {
    Account from = fixture.customerAccountHolding("10.00");
    Account to = fixture.emptyCustomerAccount();
    double before = countOf("posting");

    assertThatThrownBy(() -> ledger.record(from.id(), to.id(), Money.of("10.01")))
        .isInstanceOf(InsufficientFundsException.class);

    assertThat(countOf("posting"))
        .as("a movement that never happened must not appear in the count an operator"
            + " reconciles a statement against")
        .isEqualTo(before);
  }

  private double countOf(String kind) {
    return Search.in(registry)
        .name("fkbank.ledger.postings")
        .tag("kind", kind)
        .counter()
        .count();
  }
}
