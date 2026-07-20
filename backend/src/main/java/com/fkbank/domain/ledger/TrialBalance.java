package com.fkbank.domain.ledger;

import java.util.List;

/**
 * The outcome of auditing the ledger against itself.
 *
 * <p>Answers the two questions that reveal a broken ledger: does every saved balance still equal
 * the sum of that account's own postings, and does every unit of money that left an account
 * arrive in another one.
 *
 * @param totalDebits everything taken out of accounts, across the whole ledger
 * @param totalCredits everything put into accounts, across the whole ledger
 * @param driftedAccounts accounts whose saved balance no longer matches their postings
 */
public record TrialBalance(Money totalDebits, Money totalCredits, List<AccountId> driftedAccounts) {

  public TrialBalance {
    driftedAccounts = List.copyOf(driftedAccounts);
  }

  /** Whether every unit of money debited was credited somewhere. */
  public boolean isBalanced() {
    return totalDebits.compareTo(totalCredits) == 0;
  }

  /** Whether the ledger is both balanced and free of drifted balances. */
  public boolean isConsistent() {
    return isBalanced() && driftedAccounts.isEmpty();
  }

  @Override
  public String toString() {
    return "trial balance: debits %s, credits %s, %d drifted account(s)"
        .formatted(totalDebits, totalCredits, driftedAccounts.size());
  }
}
