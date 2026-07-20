package com.fkbank.domain.onboarding;

import com.fkbank.domain.customer.Cpf;
import com.fkbank.domain.customer.Customer;
import com.fkbank.domain.customer.CustomerRepository;
import com.fkbank.domain.customer.DuplicateCustomerException;
import com.fkbank.domain.customer.Email;
import com.fkbank.domain.customer.FullName;
import com.fkbank.domain.customer.MonthlyIncome;
import com.fkbank.domain.customer.UnderageCustomerException;
import com.fkbank.domain.identity.PasswordHasher;
import com.fkbank.domain.identity.RawPassword;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/**
 * Opening an account, from the form to an answer.
 *
 * <p>Deliberately holds no transaction. Asking the bureau means waiting on another system, and a
 * database transaction held open across that wait keeps a connection busy for as long as a
 * stranger's server feels like taking. The application is written first and committed, the
 * bureau is asked, and the answer is applied in a transaction of its own.
 *
 * <p>That ordering is also what makes the wait survivable. Once the application is written, it
 * exists whatever happens next: if the bureau never answers, the applicant has something to come
 * back to, and the answer that arrives later by callback has something to attach itself to.
 */
public class SignUp {

  private final OnboardingRepository onboardings;
  private final CustomerRepository customers;
  private final PasswordHasher passwordHasher;
  private final BureauCheck bureau;
  private final OnboardingOutcome outcome;
  private final Clock clock;

  public SignUp(
      OnboardingRepository onboardings,
      CustomerRepository customers,
      PasswordHasher passwordHasher,
      BureauCheck bureau,
      OnboardingOutcome outcome,
      Clock clock) {
    this.onboardings = Objects.requireNonNull(onboardings);
    this.customers = Objects.requireNonNull(customers);
    this.passwordHasher = Objects.requireNonNull(passwordHasher);
    this.bureau = Objects.requireNonNull(bureau);
    this.outcome = Objects.requireNonNull(outcome);
    this.clock = Objects.requireNonNull(clock);
  }

  /**
   * Submits an application to open an account and returns where it got to.
   *
   * <p>Submitting a CPF that is already being checked returns that application rather than
   * starting a second one, so a person who presses the button twice, or comes back to the form
   * after a timeout, ends up with one application either way.
   *
   * @throws IllegalArgumentException if the submitted values cannot be read
   * @throws com.fkbank.domain.identity.WeakPasswordException if the password is too weak
   * @throws UnderageCustomerException if the applicant is not yet an adult
   * @throws DuplicateCustomerException if someone already banks here with this CPF or e-mail
   */
  public SignUpResult submit(SignUpRequest request) {
    Objects.requireNonNull(request, "sign-up request must not be null");

    FullName fullName = FullName.of(request.fullName());
    Cpf cpf = Cpf.of(request.cpf());
    Email email = Email.of(request.email());
    LocalDate birthDate = readBirthDate(request.birthDate());
    MonthlyIncome monthlyIncome = MonthlyIncome.of(request.monthlyIncome());
    RawPassword password = RawPassword.of(request.password());

    LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    if (!Customer.isAdult(birthDate, today)) {
      throw new UnderageCustomerException(Customer.MINIMUM_AGE);
    }
    requireNobodyAlreadyBanksHere(cpf, email);

    Onboarding pending = onboardings.findPendingByCpf(cpf).orElse(null);
    if (pending != null) {
      return SignUpResult.alreadyUnderWay(pending);
    }

    Onboarding submitted;
    try {
      submitted =
          onboardings.save(
              Onboarding.submit(
                  OnboardingId.next(),
                  fullName,
                  cpf,
                  email,
                  birthDate,
                  monthlyIncome,
                  passwordHasher.hash(password)));
    } catch (OnboardingAlreadyPendingException lostTheRace) {
      // Another submission for this CPF committed between the check above and this insert. The
      // store refused the duplicate, which is exactly what was wanted; the person is shown the
      // application that won rather than an error about one that was never created.
      return onboardings
          .findPendingByCpf(cpf)
          .map(SignUpResult::alreadyUnderWay)
          .orElseThrow(() -> lostTheRace);
    }

    BureauDecision decision = bureau.decide(submitted);
    if (!decision.isDetermined()) {
      // The bureau did not answer in time. The application stays open and the answer it
      // eventually sends will settle it.
      return SignUpResult.started(submitted);
    }
    return SignUpResult.started(outcome.apply(submitted.id(), decision));
  }

  /** Where an application got to, for someone holding its identifier. */
  public OnboardingView statusOf(OnboardingId id) {
    return onboardings
        .findById(id)
        .map(OnboardingView::of)
        .orElseThrow(() -> new UnknownOnboardingException(id));
  }

  /**
   * Refuses an application from someone who already banks here.
   *
   * <p>Checked before anything is written so the common case is answered without a failed
   * insert. It cannot be the only check — two submissions can pass it at the same instant — so
   * the store enforces the same rule and has the final say.
   */
  private void requireNobodyAlreadyBanksHere(Cpf cpf, Email email) {
    if (customers.existsByCpf(cpf)) {
      throw DuplicateCustomerException.forCpf();
    }
    if (customers.existsByEmail(email)) {
      throw DuplicateCustomerException.forEmail();
    }
  }

  private static LocalDate readBirthDate(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("birth date is required");
    }
    try {
      return LocalDate.parse(value.trim());
    } catch (DateTimeParseException malformed) {
      throw new IllegalArgumentException("birth date is not a valid date", malformed);
    }
  }
}
