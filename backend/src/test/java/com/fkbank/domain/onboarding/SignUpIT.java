package com.fkbank.domain.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.fkbank.domain.account.CurrentAccountRepository;
import com.fkbank.domain.customer.Cpf;
import com.fkbank.domain.customer.CustomerRepository;
import com.fkbank.domain.customer.DuplicateCustomerException;
import com.fkbank.domain.customer.UnderageCustomerException;
import com.fkbank.domain.identity.CredentialRepository;
import com.fkbank.domain.identity.Username;
import com.fkbank.domain.identity.WeakPasswordException;
import com.fkbank.testsupport.ControllableBureau;
import com.fkbank.testsupport.Cpfs;
import com.fkbank.testsupport.OnboardingFixture;
import com.fkbank.testsupport.OnboardingIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("Signing up")
class SignUpIT extends OnboardingIntegrationTest {

  @Autowired private SignUp signUp;
  @Autowired private OnboardingOutcome outcome;
  @Autowired private ControllableBureau bureau;
  @Autowired private OnboardingRepository onboardings;
  @Autowired private CustomerRepository customers;
  @Autowired private CredentialRepository credentials;
  @Autowired private CurrentAccountRepository accounts;

  @BeforeEach
  void resetTheBureau() {
    bureau.reset();
  }

  @Nested
  @DisplayName("when the bureau approves")
  class WhenApproved {

    @Test
    @DisplayName("registers the person, issues a working credential and opens their account")
    void createsEverythingTheApprovalImplies() {
      String cpf = Cpfs.random();
      String email = OnboardingFixture.uniqueEmail().value();
      bureau.willAnswer(BureauDecision.approved());

      SignUpResult result = signUp.submit(request(cpf, email));

      assertThat(result.started()).isTrue();
      assertThat(result.application().status()).isEqualTo(OnboardingStatus.APPROVED);

      var customer = customers.findByCpf(Cpf.of(cpf));
      assertThat(customer).as("an approved application registers the person").isPresent();
      assertThat(credentials.findByUsername(Username.of(email)))
          .as("the credential must be active, or the person cannot sign in")
          .hasValueSatisfying(credential -> assertThat(credential.isActive()).isTrue());
      assertThat(accounts.findByCustomerId(customer.orElseThrow().id()))
          .as("an approved application opens an account")
          .isPresent();
    }
  }

  @Nested
  @DisplayName("when the bureau refuses")
  class WhenRefused {

    @Test
    @DisplayName("records the reason category and creates nothing")
    void createsNothing() {
      String cpf = Cpfs.random();
      String email = OnboardingFixture.uniqueEmail().value();
      bureau.willAnswer(BureauDecision.rejected(RejectionReason.SANCTIONS_LIST));

      SignUpResult result = signUp.submit(request(cpf, email));

      assertThat(result.application().status()).isEqualTo(OnboardingStatus.REJECTED);
      assertThat(result.application().reason()).isEqualTo(RejectionReason.SANCTIONS_LIST);
      assertThat(customers.findByCpf(Cpf.of(cpf)))
          .as("a refused applicant is not a customer")
          .isEmpty();
      assertThat(credentials.findByUsername(Username.of(email)))
          .as("a refused applicant must hold no credential at all, active or otherwise")
          .isEmpty();
    }
  }

  @Nested
  @DisplayName("when the bureau does not answer in time")
  class WhenUndetermined {

    @Test
    @DisplayName("leaves the application open and resubmitting returns that same application")
    void staysPendingAndIsIdempotent() {
      String cpf = Cpfs.random();
      bureau.willAnswer(BureauDecision.undetermined());

      SignUpResult first = signUp.submit(request(cpf, OnboardingFixture.uniqueEmail().value()));

      assertThat(first.started()).isTrue();
      assertThat(first.application().status()).isEqualTo(OnboardingStatus.PENDING);

      SignUpResult second = signUp.submit(request(cpf, OnboardingFixture.uniqueEmail().value()));

      assertThat(second.started())
          .as("a resubmission must not start a second application")
          .isFalse();
      assertThat(second.application().id())
          .as("the applicant gets back the application they already have")
          .isEqualTo(first.application().id());
      assertThat(bureau.enquiries())
          .as("the bureau is not asked again for an application already waiting on it")
          .isEqualTo(1);
    }

    @Test
    @DisplayName("can still be settled later by the answer the bureau sends afterwards")
    void theLateAnswerStillLands() {
      String cpf = Cpfs.random();
      String email = OnboardingFixture.uniqueEmail().value();
      bureau.willAnswer(BureauDecision.undetermined());

      SignUpResult pending = signUp.submit(request(cpf, email));
      OnboardingView settled = outcomeOf(pending);

      assertThat(settled.status())
          .as("an application left open must be reachable, or the applicant is stranded")
          .isEqualTo(OnboardingStatus.APPROVED);
      assertThat(customers.findByCpf(Cpf.of(cpf))).isPresent();
    }
  }

  @Nested
  @DisplayName("refuses a submission")
  class Refusals {

    @Test
    @DisplayName("from someone who already banks here under this CPF")
    void duplicateCpf() {
      String cpf = Cpfs.random();
      signUp.submit(request(cpf, OnboardingFixture.uniqueEmail().value()));

      assertThatExceptionOfType(DuplicateCustomerException.class)
          .isThrownBy(() -> signUp.submit(request(cpf, OnboardingFixture.uniqueEmail().value())))
          .satisfies(
              refusal ->
                  assertThat(refusal.code()).isEqualTo(DuplicateCustomerException.CODE));
    }

    @Test
    @DisplayName("from someone whose e-mail address already signs somebody in")
    void duplicateEmail() {
      String email = OnboardingFixture.uniqueEmail().value();
      signUp.submit(request(Cpfs.random(), email));

      assertThatExceptionOfType(DuplicateCustomerException.class)
          .isThrownBy(() -> signUp.submit(request(Cpfs.random(), email)));
    }

    @Test
    @DisplayName("from someone who is not yet an adult, without asking the bureau")
    void underage() {
      LocalDate seventeenToday = LocalDate.now().minusYears(17);

      assertThatExceptionOfType(UnderageCustomerException.class)
          .isThrownBy(
              () ->
                  signUp.submit(
                      new SignUpRequest(
                          "Ada Lovelace",
                          Cpfs.random(),
                          OnboardingFixture.uniqueEmail().value(),
                          "secret123",
                          seventeenToday.toString(),
                          "4500.00")));

      assertThat(bureau.enquiries())
          .as("someone the bank cannot accept is not sent to a third party for checking")
          .isZero();
    }

    @Test
    @DisplayName("whose password would not protect the account, before anything is written")
    void weakPassword() {
      String cpf = Cpfs.random();

      assertThatExceptionOfType(WeakPasswordException.class)
          .isThrownBy(
              () ->
                  signUp.submit(
                      new SignUpRequest(
                          "Ada Lovelace",
                          cpf,
                          OnboardingFixture.uniqueEmail().value(),
                          "password",
                          OnboardingFixture.birthDate().toString(),
                          "4500.00")));

      assertThat(onboardings.findPendingByCpf(Cpf.of(cpf)))
          .as("a refused submission leaves no application behind")
          .isEmpty();
    }
  }

  @Test
  @DisplayName("treats a formatted CPF and a bare one as the same person")
  void cpfFormattingDoesNotDefeatUniqueness() {
    String cpf = Cpfs.random();
    bureau.willAnswer(BureauDecision.undetermined());

    SignUpResult first = signUp.submit(request(cpf, OnboardingFixture.uniqueEmail().value()));
    SignUpResult second =
        signUp.submit(request(Cpfs.formatted(cpf), OnboardingFixture.uniqueEmail().value()));

    assertThat(second.application().id()).isEqualTo(first.application().id());
    assertThat(second.started()).isFalse();
  }

  @Test
  @DisplayName("reports where an application got to, for whoever holds its identifier")
  void reportsStatus() {
    bureau.willAnswer(BureauDecision.rejected(RejectionReason.DOCUMENT_MISMATCH));
    SignUpResult submitted =
        signUp.submit(request(Cpfs.random(), OnboardingFixture.uniqueEmail().value()));

    OnboardingView status = signUp.statusOf(submitted.application().id());

    assertThat(status.status()).isEqualTo(OnboardingStatus.REJECTED);
    assertThat(status.reason()).isEqualTo(RejectionReason.DOCUMENT_MISMATCH);
  }

  @Test
  @DisplayName("reports an identifier nobody was ever given as unknown")
  void unknownApplication() {
    assertThatExceptionOfType(UnknownOnboardingException.class)
        .isThrownBy(() -> signUp.statusOf(OnboardingId.next()));
  }

  /** Applies the answer the bureau sends after it has already timed out. */
  private OnboardingView outcomeOf(SignUpResult pending) {
    return outcome.apply(pending.application().id(), BureauDecision.approved());
  }

  private static SignUpRequest request(String cpf, String email) {
    return new SignUpRequest(
        "Ada Lovelace",
        cpf,
        email,
        OnboardingFixture.password(),
        OnboardingFixture.birthDate().toString(),
        "4500.00");
  }
}
