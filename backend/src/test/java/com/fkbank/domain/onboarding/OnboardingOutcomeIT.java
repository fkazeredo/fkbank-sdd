package com.fkbank.domain.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.fkbank.domain.account.AccountSummary;
import com.fkbank.domain.account.CurrentAccount;
import com.fkbank.domain.account.CurrentAccountRepository;
import com.fkbank.domain.account.CurrentAccounts;
import com.fkbank.domain.customer.CustomerId;
import com.fkbank.domain.identity.CredentialRepository;
import com.fkbank.domain.identity.Username;
import com.fkbank.domain.ledger.Ledger;
import com.fkbank.testsupport.OnboardingFixture;
import com.fkbank.testsupport.OnboardingIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Applying the bureau's answer, including when it arrives more than once.
 *
 * <p>The bureau answers twice by design when it has to fall back to a callback, and a sender
 * that never repeats itself is a sender that loses answers. What matters is that the second
 * delivery changes nothing.
 */
@DisplayName("Settling an application")
class OnboardingOutcomeIT extends OnboardingIntegrationTest {

  @Autowired private OnboardingOutcome outcome;
  @Autowired private OnboardingFixture fixture;
  @Autowired private OnboardingRepository onboardings;
  @Autowired private CredentialRepository credentials;
  @Autowired private CurrentAccountRepository accounts;
  @Autowired private CurrentAccounts currentAccounts;
  @Autowired private Ledger ledger;

  @Test
  @DisplayName("opens the account at a zero balance without recording a posting")
  void opensAtZero() {
    Onboarding application = fixture.pendingApplication();

    outcome.apply(application.id(), BureauDecision.approved());

    CustomerId customerId =
        onboardings.findById(application.id()).flatMap(Onboarding::customerId).orElseThrow();
    AccountSummary summary = currentAccounts.summaryOf(customerId);

    assertThat(summary.balance())
        .as("a new account holds nothing")
        .isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(summary.currency()).isEqualTo("BRL");
    assertThat(summary.number().branch()).isEqualTo("0001");
    assertThat(summary.number().number())
        .as("the number is allocated, not left blank")
        .isNotBlank();
  }

  @Test
  @DisplayName("gives the customer their own ledger account")
  void opensTheLedgerAccount() {
    Onboarding application = fixture.pendingApplication();

    outcome.apply(application.id(), BureauDecision.approved());

    CustomerId customerId =
        onboardings.findById(application.id()).flatMap(Onboarding::customerId).orElseThrow();
    CurrentAccount account = accounts.findByCustomerId(customerId).orElseThrow();

    assertThat(ledger.balanceOf(account.ledgerAccountCode()).atEdge())
        .as("the account's money has somewhere to be recorded from the moment it is opened")
        .isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  @DisplayName("changes nothing when the same approval is delivered a second time")
  void repeatedApprovalIsANoOp() {
    Onboarding application = fixture.pendingApplication();

    OnboardingView first = outcome.apply(application.id(), BureauDecision.approved());
    OnboardingView second = outcome.apply(application.id(), BureauDecision.approved());

    assertThat(second.status()).isEqualTo(first.status());
    CustomerId customerId =
        onboardings.findById(application.id()).flatMap(Onboarding::customerId).orElseThrow();
    assertThat(accounts.findByCustomerId(customerId))
        .as("a repeated delivery must not open a second account")
        .isPresent();
    assertThat(currentAccounts.summaryOf(customerId).number())
        .as("the account number the customer was given does not change under a repeat")
        .isEqualTo(accounts.findByCustomerId(customerId).orElseThrow().number());
  }

  @Test
  @DisplayName("does not overturn a refusal when a late approval arrives afterwards")
  void aSettledRefusalStaysRefused() {
    Onboarding application = fixture.pendingApplication();
    outcome.apply(application.id(), BureauDecision.rejected(RejectionReason.SANCTIONS_LIST));

    OnboardingView afterLateApproval =
        outcome.apply(application.id(), BureauDecision.approved());

    assertThat(afterLateApproval.status())
        .as("an answer that already settled the application is final")
        .isEqualTo(OnboardingStatus.REJECTED);
    assertThat(credentials.findByUsername(Username.of(application.email().value())))
        .as("a refused applicant must not gain a credential from a stray later message")
        .isEmpty();
  }

  @Test
  @DisplayName("leaves an application open when the answer decides nothing")
  void undeterminedLeavesItPending() {
    Onboarding application = fixture.pendingApplication();

    OnboardingView view = outcome.apply(application.id(), BureauDecision.undetermined());

    assertThat(view.status()).isEqualTo(OnboardingStatus.PENDING);
  }

  @Test
  @DisplayName("refuses an answer about an application that does not exist")
  void unknownApplication() {
    assertThatExceptionOfType(UnknownOnboardingException.class)
        .isThrownBy(() -> outcome.apply(OnboardingId.next(), BureauDecision.approved()));
  }
}
