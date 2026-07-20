package com.fkbank.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TrialBalance")
class TrialBalanceTest {

  @Test
  @DisplayName("is consistent when the totals match and nothing drifted")
  void consistentWhenTotalsMatchAndNothingDrifted() {
    TrialBalance trial = new TrialBalance(Money.of("100.00"), Money.of("100.00"), List.of());

    assertThat(trial.isBalanced()).isTrue();
    assertThat(trial.isConsistent()).isTrue();
  }

  @Test
  @DisplayName("is not consistent when an account drifted, even though the totals still match")
  void driftedAccountBreaksConsistencyWithoutBreakingTheTotals() {
    TrialBalance trial =
        new TrialBalance(Money.of("100.00"), Money.of("100.00"), List.of(AccountId.of(7)));

    assertThat(trial.isBalanced()).isTrue();
    assertThat(trial.isConsistent()).isFalse();
  }

  @Test
  @DisplayName("is not balanced when debits and credits disagree")
  void unbalancedWhenTotalsDisagree() {
    TrialBalance trial = new TrialBalance(Money.of("100.00"), Money.of("99.00"), List.of());

    assertThat(trial.isBalanced()).isFalse();
    assertThat(trial.isConsistent()).isFalse();
  }

  @Test
  @DisplayName("compares totals by value, not by written scale")
  void comparesTotalsByValue() {
    TrialBalance trial = new TrialBalance(Money.of("100.0000"), Money.of("100.00"), List.of());

    assertThat(trial.isBalanced()).isTrue();
  }

  @Test
  @DisplayName("does not expose its drifted list to modification")
  void driftedListIsACopy() {
    List<AccountId> mutable = new java.util.ArrayList<>(List.of(AccountId.of(1)));
    TrialBalance trial = new TrialBalance(Money.zero(), Money.zero(), mutable);

    mutable.clear();

    assertThat(trial.driftedAccounts()).containsExactly(AccountId.of(1));
  }
}
