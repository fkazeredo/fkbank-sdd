package com.fkbank.domain.ledger;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * The books.
 *
 * <p>The single entry point for moving money. Nothing else in the product may read or write a
 * balance, which is what makes "can this account afford it" a question with exactly one answer
 * and one place to look when the answer is wrong.
 *
 * <p>Each operation runs inside one transaction, applied from the outside so that this class
 * stays free of framework concerns. Every method that touches balances locks the accounts it
 * needs in ascending id order before reading them, so concurrent postings queue up instead of
 * racing, and a caller can trust that a refusal left the ledger untouched.
 */
public class Ledger {

  private final AccountRepository accounts;
  private final PostingRepository postings;
  private final BalanceRepository balances;
  private final LedgerEventPublisher events;
  private final Clock clock;

  public Ledger(
      AccountRepository accounts,
      PostingRepository postings,
      BalanceRepository balances,
      LedgerEventPublisher events,
      Clock clock) {
    this.accounts = Objects.requireNonNull(accounts);
    this.postings = Objects.requireNonNull(postings);
    this.balances = Objects.requireNonNull(balances);
    this.events = Objects.requireNonNull(events);
    this.clock = Objects.requireNonNull(clock);
  }

  /** Opens an account at zero and returns it. */
  public Account openAccount(AccountKind kind, String code) {
    Account account = accounts.open(kind, code);
    balances.create(Balance.opening(account));
    return account;
  }

  /**
   * Moves {@code amount} out of one account and into another.
   *
   * @throws InsufficientFundsException if the debited account cannot cover it; nothing is written
   */
  public Posting record(AccountId debit, AccountId credit, Money amount) {
    requireExists(debit);
    requireExists(credit);
    Posting posting = Posting.of(PostingId.next(), debit, credit, amount, clock.instant());
    return write(posting);
  }

  /**
   * Undoes a posting by recording its contra-posting, leaving the original row untouched.
   *
   * <p>Refused when the posting has already been reversed or is itself a reversal. The contra is
   * an ordinary posting, so it obeys the never-below-zero rule like any other and may be refused
   * for insufficient funds.
   *
   * @throws ReversalNotAllowedException if the posting cannot be reversed; nothing is written
   */
  public Posting reverse(PostingId original) {
    Posting source =
        postings.findById(original).orElseThrow(() -> new UnknownPostingException(original));
    if (source.isReversal()) {
      throw ReversalNotAllowedException.isItselfAReversal(original);
    }

    Posting contra = Posting.reverseOf(PostingId.next(), source, clock.instant());
    Map<AccountId, Balance> locked = lockBalancesOf(contra);

    // Asked only once the accounts are held. Both reversals of one posting move the same two
    // balances, so whichever arrives second waits here and then sees the contra the first one
    // committed. Asking before the lock would let both read "not yet reversed": the loser would
    // go on to be refused by the unique index for running out of money, reporting a cause that
    // has nothing to do with why it was actually rejected.
    if (postings.existsReversalOf(original)) {
      throw ReversalNotAllowedException.alreadyReversed(original);
    }
    return write(contra, locked);
  }

  /**
   * What an account currently holds.
   *
   * <p>Reads without locking, so the answer is a snapshot rather than a reservation. Deciding
   * whether a movement is affordable is {@link #record}'s job, and it locks.
   */
  public Money balanceOf(AccountId accountId) {
    return balances
        .find(accountId)
        .map(Balance::amount)
        .orElseThrow(() -> new UnknownAccountException(accountId));
  }

  /** Looks up a movement that was recorded. */
  public Optional<Posting> findPosting(PostingId id) {
    return postings.findById(id);
  }

  /**
   * Audits the ledger against itself: recomputes every account's position from its postings and
   * compares it with the saved balance, and checks that total debits equal total credits.
   *
   * <p>Deliberately recomputes from the postings rather than from the balances it is checking —
   * a verification that reads the same figure it is verifying always agrees with itself.
   */
  public TrialBalance trialBalance() {
    Map<AccountId, Money> netByAccount = postings.netAmountByAccount();
    Map<AccountId, Money> savedByAccount = new LinkedHashMap<>();
    balances.findAll().forEach(balance -> savedByAccount.put(balance.accountId(), balance.amount()));

    // Every account either side knows about, not just the ones with a balance row. Walking the
    // balances alone means an account whose row has been deleted is never looked at: its postings
    // still say it holds money, nothing contradicts them, and the audit reports a clean ledger
    // over the exact damage it exists to find.
    Set<AccountId> everyAccount = new TreeSet<>();
    everyAccount.addAll(savedByAccount.keySet());
    everyAccount.addAll(netByAccount.keySet());

    List<AccountId> drifted =
        everyAccount.stream()
            .filter(account -> hasDrifted(account, savedByAccount, netByAccount))
            .toList();
    return new TrialBalance(postings.totalDebits(), postings.totalCredits(), drifted);
  }

  private static boolean hasDrifted(
      AccountId account, Map<AccountId, Money> savedByAccount, Map<AccountId, Money> netByAccount) {
    Money saved = savedByAccount.get(account);
    if (saved == null) {
      // Postings reference it, so the account exists and is owed a balance row. Missing is drift:
      // an unknown position is not a matching one.
      return true;
    }
    return saved.compareTo(netByAccount.getOrDefault(account, Money.zero())) != 0;
  }

  private Posting write(Posting posting) {
    return write(posting, lockBalancesOf(posting));
  }

  private Posting write(Posting posting, Map<AccountId, Balance> locked) {
    applyToBalances(posting, locked);
    Posting saved = postings.save(posting);
    events.publish(PostingRecorded.from(saved));
    return saved;
  }

  /**
   * Holds the two accounts a posting touches, in ascending id order.
   *
   * <p>Taking the locks in a fixed order is what keeps two movements between the same pair of
   * accounts from each waiting on what the other holds. Locking is separate from applying so a
   * caller can ask a question that must be answered under the lock before deciding to write.
   */
  private Map<AccountId, Balance> lockBalancesOf(Posting posting) {
    Map<AccountId, Balance> locked = new LinkedHashMap<>();
    Stream.of(posting.debitAccountId(), posting.creditAccountId())
        .sorted()
        .forEach(id -> locked.put(id, balances.lockForUpdate(id)));
    return locked;
  }

  /**
   * Applies a posting to the balances already held.
   *
   * <p>The balances were read after their locks were taken: a balance read before the lock is a
   * balance another transaction may already have spent. The debit is applied before the credit so
   * an account that cannot cover the movement refuses it before anything has been changed.
   */
  private void applyToBalances(Posting posting, Map<AccountId, Balance> locked) {
    locked.get(posting.debitAccountId()).debit(posting.amount());
    locked.get(posting.creditAccountId()).credit(posting.amount());
    locked.values().forEach(balances::save);
  }

  private void requireExists(AccountId id) {
    accounts.findById(id).orElseThrow(() -> new UnknownAccountException(id));
  }
}
