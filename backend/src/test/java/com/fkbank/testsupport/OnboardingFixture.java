package com.fkbank.testsupport;

import com.fkbank.domain.customer.Cpf;
import com.fkbank.domain.customer.CustomerId;
import com.fkbank.domain.customer.Email;
import com.fkbank.domain.customer.FullName;
import com.fkbank.domain.customer.MonthlyIncome;
import com.fkbank.domain.identity.PasswordHasher;
import com.fkbank.domain.identity.RawPassword;
import com.fkbank.domain.onboarding.BureauDecision;
import com.fkbank.domain.onboarding.BureauReference;
import com.fkbank.domain.onboarding.Onboarding;
import com.fkbank.domain.onboarding.OnboardingId;
import com.fkbank.domain.onboarding.OnboardingOutcome;
import com.fkbank.domain.onboarding.OnboardingRepository;
import com.fkbank.domain.onboarding.OnboardingView;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Puts customers into the world the way the product does.
 *
 * <p>An approved customer is produced by settling a real application, not by inserting rows: a
 * test that builds its world by hand asserts against a state the product may never actually
 * reach, and would keep passing after the real path stopped producing it.
 *
 * <p>The only thing skipped is the network call to the bureau, which is replaced by handing the
 * approval straight to the code that applies one.
 */
@Component
public class OnboardingFixture {

  /** Old enough to hold an account by a wide margin, so the age rule never decides a test. */
  private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 5, 20);

  private static final String PASSWORD = "secret123";

  private final OnboardingRepository onboardings;
  private final OnboardingOutcome outcome;
  private final PasswordHasher passwordHasher;

  public OnboardingFixture(
      OnboardingRepository onboardings, OnboardingOutcome outcome, PasswordHasher passwordHasher) {
    this.onboardings = onboardings;
    this.outcome = outcome;
    this.passwordHasher = passwordHasher;
  }

  /** Somebody who signed up, was approved, and can sign in. */
  public record SignedUpCustomer(
      OnboardingId onboardingId, CustomerId customerId, String username, String password) {}

  /** Signs somebody up and approves them. */
  public SignedUpCustomer approvedCustomer() {
    Onboarding application = pendingApplication();
    OnboardingView settled = outcome.apply(application.id(), BureauDecision.approved());
    return new SignedUpCustomer(
        settled.id(),
        onboardings
            .findById(settled.id())
            .flatMap(Onboarding::customerId)
            .orElseThrow(() -> new IllegalStateException("an approved application names a customer")),
        application.email().value(),
        PASSWORD);
  }

  /** An application that has been submitted and is still waiting on the bureau. */
  public Onboarding pendingApplication() {
    return pendingApplicationFor(Cpf.of(Cpfs.random()), uniqueEmail());
  }

  /** An application for a particular person, so a test can submit the same CPF twice. */
  public Onboarding pendingApplicationFor(Cpf cpf, Email email) {
    return onboardings.save(
        Onboarding.submit(
            OnboardingId.next(),
            FullName.of("Ada Lovelace"),
            cpf,
            email,
            BIRTH_DATE,
            MonthlyIncome.of("4500.00"),
            passwordHasher.hash(RawPassword.of(PASSWORD)),
            BureauReference.next()));
  }

  public static Email uniqueEmail() {
    return Email.of("applicant-" + UUID.randomUUID() + "@example.com");
  }

  public static String password() {
    return PASSWORD;
  }

  public static LocalDate birthDate() {
    return BIRTH_DATE;
  }
}
