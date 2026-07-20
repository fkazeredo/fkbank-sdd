package com.fkbank.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The ledger's own decisions, with the database replaced by fakes.
 *
 * <p>The integration tests prove the ledger against real PostgreSQL, which is where locking and
 * immutability actually live. What they cannot do is isolate the reasoning: the order it takes
 * locks in, the order it applies a debit and a credit, what it refuses and in which precedence.
 * Those are decisions this class makes on its own, and a test that has to boot Spring to observe
 * them cannot say which mutation of that logic would go unnoticed.
 */
@DisplayName("Ledger")
class LedgerTest {

  private static final Instant NOW = Instant.parse("2026-07-20T12:00:00Z");

  private FakeAccounts accounts;
  private FakePostings postings;
  private FakeBalances balances;
  private RecordingPublisher events;
  private Ledger ledger;

  @BeforeEach
  void setUp() {
    accounts = new FakeAccounts();
    postings = new FakePostings();
    balances = new FakeBalances();
    events = new RecordingPublisher();
    // The posting fake records whether each duplicate-reversal probe happened while the balance
    // locks were held, which is the whole point of one of the assertions below.
    postings.seeBalances(balances);
    ledger = new Ledger(accounts, postings, balances, events, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /**
   * Opens a customer account and funds it through the ledger.
   *
   * <p>Funding by writing the balance directly would be quicker and wrong: it produces a balance
   * with no posting behind it, which is exactly the corruption the trial balance exists to catch,
   * so every test that audits the books would fail on the fixture rather than on the code.
   */
  private Account customerHolding(String amount) {
    Account account = emptyCustomer();
    ledger.record(fundingSource().id(), account.id(), Money.of(amount));
    return account;
  }

  private Account fundingSource() {
    return accounts
        .findByCode("internal:funding")
        .orElseGet(() -> ledger.openAccount(AccountKind.INTERNAL_SETTLEMENT, "internal:funding"));
  }

  private Account emptyCustomer() {
    return ledger.openAccount(AccountKind.CUSTOMER_AVAILABLE, "customer:empty:" + accounts.size());
  }

  private Account settlement() {
    return ledger.openAccount(AccountKind.INTERNAL_SETTLEMENT, "internal:" + accounts.size());
  }

  @Nested
  @DisplayName("recording")
  class Recording {

    @Test
    @DisplayName("moves the amount and stamps the movement with the current instant")
    void movesTheAmount() {
      Account from = customerHolding("100.00");
      Account to = emptyCustomer();

      Posting posting = ledger.record(from.id(), to.id(), Money.of("30.00"));

      assertThat(ledger.balanceOf(from.id())).isEqualTo(Money.of("70.00"));
      assertThat(ledger.balanceOf(to.id())).isEqualTo(Money.of("30.00"));
      assertThat(posting.occurredAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("locks both accounts in ascending id order, whichever way the money flows")
    void locksInAscendingIdOrder() {
      Account first = customerHolding("100.00");
      Account second = customerHolding("100.00");
      balances.forgetLockOrder();

      ledger.record(second.id(), first.id(), Money.of("10.00"));

      assertThat(balances.lockOrder())
          .as("a fixed order is what keeps two movements between the same pair from deadlocking")
          .containsExactly(first.id(), second.id())
          .isSorted();
    }

    @Test
    @DisplayName("reads each balance only after taking its lock")
    void readsOnlyUnderTheLock() {
      Account from = customerHolding("100.00");
      Account to = emptyCustomer();

      ledger.record(from.id(), to.id(), Money.of("10.00"));

      assertThat(balances.unlockedReads())
          .as("a balance read before its lock is a balance another transaction may have spent")
          .isEmpty();
    }

    @Test
    @DisplayName("refuses an unknown account with a stable code, before touching anything")
    void refusesUnknownAccounts() {
      Account real = customerHolding("10.00");
      AccountId ghost = AccountId.of(9999);
      int writtenSoFar = postings.saved().size();
      int announcedSoFar = events.published().size();

      assertThatThrownBy(() -> ledger.record(ghost, real.id(), Money.of("1.00")))
          .isInstanceOfSatisfying(
              UnknownAccountException.class,
              thrown -> assertThat(thrown.code()).isEqualTo("UNKNOWN_ACCOUNT"));
      assertThatThrownBy(() -> ledger.record(real.id(), ghost, Money.of("1.00")))
          .isInstanceOf(UnknownAccountException.class);

      assertThat(postings.saved()).hasSize(writtenSoFar);
      assertThat(events.published()).hasSize(announcedSoFar);
    }

    @Test
    @DisplayName("refuses to overdraw a customer account and writes nothing at all")
    void refusesToOverdraw() {
      Account from = customerHolding("10.00");
      Account to = emptyCustomer();
      int writtenSoFar = postings.saved().size();
      int announcedSoFar = events.published().size();

      assertThatThrownBy(() -> ledger.record(from.id(), to.id(), Money.of("10.01")))
          .isInstanceOf(InsufficientFundsException.class);

      assertThat(ledger.balanceOf(from.id())).isEqualTo(Money.of("10.00"));
      assertThat(ledger.balanceOf(to.id())).isEqualTo(Money.zero());
      assertThat(postings.saved())
          .as("a refusal must not leave a posting behind for the transaction to roll back")
          .hasSize(writtenSoFar);
      assertThat(events.published())
          .as("nor announce money that did not move")
          .hasSize(announcedSoFar);
    }

    @Test
    @DisplayName("applies the debit before the credit, so a refusal cannot half-apply")
    void debitsBeforeCrediting() {
      Account from = customerHolding("10.00");
      Account to = emptyCustomer();

      assertThatThrownBy(() -> ledger.record(from.id(), to.id(), Money.of("50.00")))
          .isInstanceOf(InsufficientFundsException.class);

      assertThat(ledger.balanceOf(to.id()))
          .as("the credited side must not have been touched when the debit is refused")
          .isEqualTo(Money.zero());
    }

    @Test
    @DisplayName("lets an internal account go negative")
    void internalAccountsMayGoNegative() {
      Account internal = settlement();
      Account customer = emptyCustomer();

      ledger.record(internal.id(), customer.id(), Money.of("500.00"));

      assertThat(ledger.balanceOf(internal.id())).isEqualTo(Money.of("-500.00"));
    }

    @Test
    @DisplayName("announces the movement it recorded, once")
    void announcesTheMovement() {
      Account from = customerHolding("100.00");
      Account to = emptyCustomer();
      int announcedSoFar = events.published().size();

      Posting posting = ledger.record(from.id(), to.id(), Money.of("30.00"));

      assertThat(events.published()).hasSize(announcedSoFar + 1);
      assertThat(events.published()).last().satisfies(event -> {
        assertThat(event.postingId()).isEqualTo(posting.id().value());
        assertThat(event.debitAccountId()).isEqualTo(from.id().value());
        assertThat(event.creditAccountId()).isEqualTo(to.id().value());
        assertThat(event.amount()).isEqualByComparingTo("30.0000");
        assertThat(event.currency()).isEqualTo("BRL");
        assertThat(event.isReversal()).isFalse();
      });
    }
  }

  @Nested
  @DisplayName("reversing")
  class Reversing {

    @Test
    @DisplayName("writes a contra that returns both balances and marks it as a reversal")
    void reversalRestoresBalances() {
      Account from = customerHolding("100.00");
      Account to = emptyCustomer();
      Posting original = ledger.record(from.id(), to.id(), Money.of("30.00"));

      Posting contra = ledger.reverse(original.id());

      assertThat(ledger.balanceOf(from.id())).isEqualTo(Money.of("100.00"));
      assertThat(ledger.balanceOf(to.id())).isEqualTo(Money.zero());
      assertThat(contra.reverses()).contains(original.id());
      assertThat(events.published()).last().satisfies(
          event -> assertThat(event.isReversal()).isTrue());
    }

    @Test
    @DisplayName("asks whether it was already reversed only after holding both balances")
    void checksForADuplicateUnderTheLock() {
      Account from = customerHolding("100.00");
      Account to = emptyCustomer();
      Posting original = ledger.record(from.id(), to.id(), Money.of("30.00"));
      postings.forgetProbeOrder();
      balances.forgetLockOrder();

      ledger.reverse(original.id());

      assertThat(postings.reversalProbedWhileHoldingLocks())
          .as("asked before the lock, two concurrent reversals both read \"not yet reversed\""
              + " and the loser is refused for the wrong reason")
          .isTrue();
    }

    @Test
    @DisplayName("refuses a second reversal with a stable code, writing nothing")
    void refusesASecondReversal() {
      Account from = customerHolding("100.00");
      Account to = emptyCustomer();
      Posting original = ledger.record(from.id(), to.id(), Money.of("30.00"));
      ledger.reverse(original.id());
      int writtenSoFar = postings.saved().size();

      assertThatThrownBy(() -> ledger.reverse(original.id()))
          .isInstanceOfSatisfying(
              ReversalNotAllowedException.class,
              thrown -> assertThat(thrown.code()).isEqualTo("REVERSAL_NOT_ALLOWED"));

      assertThat(postings.saved()).hasSize(writtenSoFar);
      assertThat(ledger.balanceOf(from.id())).isEqualTo(Money.of("100.00"));
    }

    @Test
    @DisplayName("refuses to reverse a reversal")
    void refusesToReverseAReversal() {
      Account from = customerHolding("100.00");
      Account to = emptyCustomer();
      Posting original = ledger.record(from.id(), to.id(), Money.of("30.00"));
      Posting contra = ledger.reverse(original.id());

      assertThatThrownBy(() -> ledger.reverse(contra.id()))
          .isInstanceOf(ReversalNotAllowedException.class);
    }

    @Test
    @DisplayName("prefers the duplicate refusal over the funding one")
    void duplicateBeatsInsufficientFunds() {
      Account settlement = settlement();
      Account customer = emptyCustomer();
      Posting deposit = ledger.record(settlement.id(), customer.id(), Money.of("100.00"));
      ledger.reverse(deposit.id());

      assertThatThrownBy(() -> ledger.reverse(deposit.id()))
          .as("the account is empty, but being empty is not why this is refused")
          .isInstanceOf(ReversalNotAllowedException.class);
    }

    @Test
    @DisplayName("a reversal still obeys the never-below-zero rule")
    void reversalObeysTheSignRule() {
      Account settlement = settlement();
      Account customer = emptyCustomer();
      Posting deposit = ledger.record(settlement.id(), customer.id(), Money.of("100.00"));
      ledger.record(customer.id(), emptyCustomer().id(), Money.of("100.00"));

      assertThatThrownBy(() -> ledger.reverse(deposit.id()))
          .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    @DisplayName("refuses an unknown posting with a stable code")
    void refusesAnUnknownPosting() {
      PostingId ghost = PostingId.next();

      assertThatThrownBy(() -> ledger.reverse(ghost))
          .isInstanceOfSatisfying(
              UnknownPostingException.class,
              thrown -> assertThat(thrown.code()).isEqualTo("UNKNOWN_POSTING"));
    }
  }

  @Nested
  @DisplayName("trial balance")
  class TrialBalanceOf {

    @Test
    @DisplayName("flags an account whose saved balance no longer matches its postings")
    void flagsDrift() {
      Account from = customerHolding("100.00");
      Account to = emptyCustomer();
      ledger.record(from.id(), to.id(), Money.of("25.00"));

      balances.tamperWith(to.id(), Money.of("999.00"));

      assertThat(ledger.trialBalance().driftedAccounts()).containsExactly(to.id());
    }

    @Test
    @DisplayName("flags nothing when every balance agrees with its postings")
    void flagsNothingWhenClean() {
      Account from = customerHolding("100.00");
      Account to = emptyCustomer();
      ledger.record(from.id(), to.id(), Money.of("25.00"));

      TrialBalance trial = ledger.trialBalance();

      assertThat(trial.driftedAccounts()).isEmpty();
      assertThat(trial.isConsistent()).isTrue();
    }

    @Test
    @DisplayName("derives from the postings, never from the balances it is checking")
    void derivesFromPostingsOnly() {
      Account from = customerHolding("100.00");
      Account to = emptyCustomer();
      ledger.record(from.id(), to.id(), Money.of("25.00"));
      balances.tamperWith(to.id(), Money.of("999.00"));

      ledger.trialBalance();

      assertThat(postings.netAmountQueries())
          .as("a check that read the balances it verifies would always agree with itself")
          .isEqualTo(1);
    }

    @Test
    @DisplayName("reports an account with no postings at all as drifted when it holds money")
    void flagsAFabricatedBalance() {
      Account orphan = emptyCustomer();

      balances.tamperWith(orphan.id(), Money.of("50.00"));

      assertThat(ledger.trialBalance().driftedAccounts()).contains(orphan.id());
    }
  }

  // ---------------------------------------------------------------------------------------
  // Fakes. Deliberately hand-written rather than mocked: they record the ordering facts the
  // assertions above depend on, which a mock's verification syntax states less clearly.
  // ---------------------------------------------------------------------------------------

  private static final class FakeAccounts implements AccountRepository {
    private final Map<AccountId, Account> byId = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public Optional<Account> findById(AccountId id) {
      return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Account> findByCode(String code) {
      return byId.values().stream().filter(account -> account.code().equals(code)).findFirst();
    }

    @Override
    public Account open(AccountKind kind, String code) {
      Account account = Account.existing(AccountId.of(nextId++), kind, code);
      byId.put(account.id(), account);
      return account;
    }

    int size() {
      return byId.size();
    }
  }

  private static final class FakePostings implements PostingRepository {
    private final List<Posting> saved = new ArrayList<>();
    private final List<Boolean> reversalProbes = new ArrayList<>();
    private FakeBalances balances;
    private int netAmountQueries;

    void seeBalances(FakeBalances balances) {
      this.balances = balances;
    }

    @Override
    public Posting save(Posting posting) {
      saved.add(posting);
      return posting;
    }

    @Override
    public Optional<Posting> findById(PostingId id) {
      return saved.stream().filter(posting -> posting.id().equals(id)).findFirst();
    }

    @Override
    public boolean existsReversalOf(PostingId id) {
      reversalProbes.add(balances != null && balances.holdsLocks());
      return saved.stream().anyMatch(posting -> posting.reverses().filter(id::equals).isPresent());
    }

    @Override
    public Map<AccountId, Money> netAmountByAccount() {
      netAmountQueries++;
      Map<AccountId, Money> net = new HashMap<>();
      for (Posting posting : saved) {
        net.merge(posting.creditAccountId(), posting.amount(), Money::add);
        net.merge(posting.debitAccountId(), posting.amount().negate(), Money::add);
      }
      return net;
    }

    @Override
    public Money totalDebits() {
      return saved.stream().map(Posting::amount).reduce(Money.zero(), Money::add);
    }

    @Override
    public Money totalCredits() {
      return saved.stream().map(Posting::amount).reduce(Money.zero(), Money::add);
    }

    List<Posting> saved() {
      return List.copyOf(saved);
    }

    int netAmountQueries() {
      return netAmountQueries;
    }

    void forgetProbeOrder() {
      reversalProbes.clear();
    }

    boolean reversalProbedWhileHoldingLocks() {
      return !reversalProbes.isEmpty() && reversalProbes.stream().allMatch(Boolean::booleanValue);
    }
  }

  private static final class FakeBalances implements BalanceRepository {
    private final Map<AccountId, Balance> byAccount = new LinkedHashMap<>();
    private final List<AccountId> lockOrder = new ArrayList<>();
    private final List<AccountId> unlockedReads = new ArrayList<>();
    private final List<AccountId> held = new ArrayList<>();

    @Override
    public Balance lockForUpdate(AccountId accountId) {
      lockOrder.add(accountId);
      held.add(accountId);
      return of(accountId);
    }

    @Override
    public Optional<Balance> find(AccountId accountId) {
      if (!held.contains(accountId)) {
        unlockedReads.add(accountId);
      }
      return Optional.ofNullable(byAccount.get(accountId));
    }

    @Override
    public void save(Balance balance) {
      byAccount.put(balance.accountId(), balance);
    }

    @Override
    public void create(Balance balance) {
      byAccount.put(balance.accountId(), balance);
    }

    @Override
    public List<Balance> findAll() {
      return List.copyOf(byAccount.values());
    }

    Balance of(AccountId accountId) {
      return byAccount.get(accountId);
    }

    void tamperWith(AccountId accountId, Money amount) {
      Balance current = byAccount.get(accountId);
      byAccount.put(accountId, Balance.existing(accountId, current.kind(), amount));
    }

    boolean holdsLocks() {
      return !held.isEmpty();
    }

    void forgetLockOrder() {
      lockOrder.clear();
      held.clear();
    }

    List<AccountId> lockOrder() {
      return List.copyOf(lockOrder);
    }

    /**
     * Balances read through {@link #find} without a lock held.
     *
     * <p>Reporting reads is only meaningful outside a movement; the assertion that uses this
     * checks the recording path, where every read must be preceded by its lock.
     */
    List<AccountId> unlockedReads() {
      return List.copyOf(unlockedReads);
    }
  }

  private static final class RecordingPublisher implements LedgerEventPublisher {
    private final List<PostingRecorded> published = new ArrayList<>();

    @Override
    public void publish(PostingRecorded event) {
      published.add(event);
    }

    List<PostingRecorded> published() {
      return List.copyOf(published);
    }
  }
}
