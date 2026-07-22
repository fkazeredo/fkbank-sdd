package com.fkbank.domain.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.fkbank.domain.customer.CustomerId;
import com.fkbank.domain.ledger.AccountId;
import com.fkbank.domain.ledger.Ledger;
import com.fkbank.domain.ledger.Money;
import com.fkbank.domain.ledger.PostingId;
import com.fkbank.testsupport.LedgerIntegrationTest;
import com.fkbank.testsupport.OnboardingFixture;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * BR-3's own concurrency guarantee, run against real PostgreSQL: paging through a statement while
 * new movements are being recorded shows no duplicated and no skipped line.
 *
 * <p>This is the Roadmap's own watchlist risk for this slice. Offset pagination fails exactly
 * this test — a row inserted ahead of a later page's offset shifts everything after it, so a line
 * is shown twice or not at all. Keyset pagination survives it because each page's cursor anchors
 * to a row already returned; nothing a concurrent insert does can move what that row already was.
 *
 * <p>A small page size is used deliberately, so the reader makes many round trips and gives the
 * concurrent writer real opportunities to land between them, rather than reading everything in
 * one query where no interleaving is possible at all.
 */
class StatementPaginationRaceIT extends LedgerIntegrationTest {

  private static final int RACE_TIMEOUT_SECONDS = 30;
  private static final int PRE_EXISTING_POSTINGS = 20;
  private static final int CONCURRENT_INSERTS = 15;
  private static final int PAGE_SIZE = 3;

  @Autowired private Statements statements;
  @Autowired private Ledger ledger;
  @Autowired private OnboardingFixture onboarding;

  @RepeatedTest(3)
  @DisplayName("paging while new postings are recorded visits every pre-existing line exactly once")
  void pagesWithNoDuplicateOrGapUnderConcurrentInserts() throws Exception {
    CustomerId customerId = onboarding.approvedCustomer().customerId();
    AccountId customerAccount = ledger.accountIdOf(CurrentAccount.ledgerAccountCodeFor(customerId));
    AccountId settlement = ledger.accountIdOf("internal:settlement:boleto");

    Set<PostingId> preExisting = new HashSet<>();
    for (int i = 0; i < PRE_EXISTING_POSTINGS; i++) {
      preExisting.add(ledger.record(settlement, customerAccount, Money.of("1.00")).id());
    }

    CountDownLatch startGate = new CountDownLatch(1);
    CountDownLatch ready = new CountDownLatch(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<List<PostingId>> reader =
          pool.submit(
              () -> {
                ready.countDown();
                startGate.await();
                return pageThroughEverything(customerId);
              });
      Future<Void> writer =
          pool.submit(
              () -> {
                ready.countDown();
                startGate.await();
                for (int i = 0; i < CONCURRENT_INSERTS; i++) {
                  ledger.record(settlement, customerAccount, Money.of("2.00"));
                }
                return null;
              });

      assertThat(ready.await(RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
      startGate.countDown();

      List<PostingId> visited = reader.get(RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      writer.get(RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

      assertThat(visited).as("no line was returned twice").doesNotHaveDuplicates();
      assertThat(new HashSet<>(visited))
          .as("every pre-existing line was visited; none skipped by a concurrent insert")
          .containsAll(preExisting);
    } finally {
      pool.shutdownNow();
    }
  }

  private List<PostingId> pageThroughEverything(CustomerId customerId) {
    List<PostingId> visited = new ArrayList<>();
    Optional<StatementCursor> cursor = Optional.empty();
    StatementFilter filter =
        StatementFilter.of(Instant.EPOCH, Instant.parse("9999-01-01T00:00:00Z"), null);
    StatementPage page;
    do {
      page = statements.statementOf(customerId, filter, cursor, PAGE_SIZE);
      page.lines().forEach(line -> visited.add(line.posting().id()));
      cursor = page.nextCursor();
    } while (cursor.isPresent());
    return visited;
  }
}
