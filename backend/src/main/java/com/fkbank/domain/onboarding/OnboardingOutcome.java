package com.fkbank.domain.onboarding;

import com.fkbank.domain.account.CurrentAccounts;
import com.fkbank.domain.customer.Customer;
import com.fkbank.domain.customer.CustomerId;
import com.fkbank.domain.customer.CustomerRepository;
import com.fkbank.domain.identity.Credential;
import com.fkbank.domain.identity.CredentialId;
import com.fkbank.domain.identity.CredentialRepository;
import com.fkbank.domain.identity.Username;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Applies the bureau's answer to an application.
 *
 * <p>Separate from submitting, for two reasons. The answer can arrive twice — once in the reply
 * to the question and again in a callback the bureau sends later — and both paths must do the
 * same thing, so there is one place that does it. And everything an approval creates has to
 * become true together: a customer, sign-in details that work, an account, and somewhere in the
 * chart of accounts for that account's money. Half of that is worse than none of it, so it runs
 * inside a single transaction applied from the outside.
 *
 * <p>Applying an answer to an application that has already been settled does nothing and
 * reports what it found. That is what makes a repeated callback harmless rather than a case
 * anyone has to remember to handle.
 */
public class OnboardingOutcome {

  private final OnboardingRepository onboardings;
  private final CustomerRepository customers;
  private final CredentialRepository credentials;
  private final CurrentAccounts accounts;
  private final OnboardingEventPublisher events;
  private final Clock clock;

  public OnboardingOutcome(
      OnboardingRepository onboardings,
      CustomerRepository customers,
      CredentialRepository credentials,
      CurrentAccounts accounts,
      OnboardingEventPublisher events,
      Clock clock) {
    this.onboardings = Objects.requireNonNull(onboardings);
    this.customers = Objects.requireNonNull(customers);
    this.credentials = Objects.requireNonNull(credentials);
    this.accounts = Objects.requireNonNull(accounts);
    this.events = Objects.requireNonNull(events);
    this.clock = Objects.requireNonNull(clock);
  }

  /**
   * Settles an application according to what the bureau decided.
   *
   * <p>An answer that decides nothing — the bureau did not respond in time — leaves the
   * application waiting, and so does an answer for an application that was already settled.
   *
   * @throws UnknownOnboardingException if no such application exists
   */
  public OnboardingView apply(OnboardingId id, BureauDecision decision) {
    Objects.requireNonNull(id, "onboarding id must not be null");

    return settle(
        onboardings.findById(id).orElseThrow(() -> new UnknownOnboardingException(id)), decision);
  }

  /**
   * Settles the application the bureau names in a callback.
   *
   * <p>The lookup is by the reference the bank generated and disclosed only to the bureau. Going
   * through the onboarding's own identifier here would mean a caller holding a signing key could
   * decide any application whose identifier they had seen — and applicants are given theirs.
   *
   * @throws UnknownBureauReferenceException if no application carries that reference
   */
  public OnboardingView applyTo(BureauReference reference, BureauDecision decision) {
    Objects.requireNonNull(reference, "bureau reference must not be null");

    return settle(
        onboardings
            .findByBureauReference(reference)
            .orElseThrow(UnknownBureauReferenceException::new),
        decision);
  }

  private OnboardingView settle(Onboarding onboarding, BureauDecision decision) {
    Objects.requireNonNull(decision, "bureau decision must not be null");

    if (onboarding.isSettled() || !decision.isDetermined()) {
      return OnboardingView.of(onboarding);
    }
    if (decision.isRejected()) {
      return refuse(onboarding, decision);
    }
    return accept(onboarding);
  }

  private OnboardingView refuse(Onboarding onboarding, BureauDecision decision) {
    onboarding.reject(decision.reason());
    return OnboardingView.of(onboardings.update(onboarding));
  }

  /**
   * Turns an approved application into a customer who can sign in and hold money.
   *
   * <p>The credential is issued and activated here rather than at submission, so that sign-in
   * details never work for someone whose application was refused or is still being checked.
   */
  private OnboardingView accept(Onboarding onboarding) {
    LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);

    Customer customer =
        customers.save(
            Customer.register(
                CustomerId.next(),
                onboarding.fullName(),
                onboarding.cpf(),
                onboarding.email(),
                onboarding.birthDate(),
                onboarding.monthlyIncome(),
                today));

    Credential credential =
        Credential.issue(
            CredentialId.next(),
            customer.id().value(),
            Username.of(customer.email().value()),
            onboarding.passwordHash());
    credential.activate();
    credentials.save(credential);

    accounts.openFor(customer.id());

    onboarding.approve(customer.id());
    Onboarding approved = onboardings.update(onboarding);
    events.publish(OnboardingApproved.from(approved));
    return OnboardingView.of(approved);
  }
}
