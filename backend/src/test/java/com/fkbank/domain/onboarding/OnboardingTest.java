package com.fkbank.domain.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fkbank.domain.customer.Cpf;
import com.fkbank.domain.customer.CustomerId;
import com.fkbank.domain.customer.Email;
import com.fkbank.domain.customer.FullName;
import com.fkbank.domain.customer.MonthlyIncome;
import com.fkbank.domain.identity.PasswordHash;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Onboarding")
class OnboardingTest {

  private static final Cpf CPF = Cpf.of("123.456.789-09");
  private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 5, 12);

  private static Onboarding submit() {
    return submit(OnboardingId.next());
  }

  private static Onboarding submit(OnboardingId id) {
    return Onboarding.submit(
        id,
        FullName.of("Ana Souza"),
        CPF,
        Email.of("ana@example.com"),
        BIRTH_DATE,
        MonthlyIncome.of("3500.00"),
        PasswordHash.of("{bcrypt}$2a$10$hashed"),
        BureauReference.next());
  }

  @Nested
  @DisplayName("submission")
  class Submission {

    @Test
    @DisplayName("starts waiting on the bureau, with nothing decided and nobody created")
    void startsPending() {
      Onboarding onboarding = submit();

      assertThat(onboarding.status()).isEqualTo(OnboardingStatus.PENDING);
      assertThat(onboarding.isPending()).isTrue();
      assertThat(onboarding.isSettled()).isFalse();
      assertThat(onboarding.reason())
          .as("an applicant still waiting has not been refused, so there is no reason to hold")
          .isEmpty();
      assertThat(onboarding.customerId())
          .as("a customer exists only once the bureau approves")
          .isEmpty();
    }

    @Test
    @DisplayName("keeps its own copy of what was submitted, with the password already hashed")
    void keepsWhatWasSubmitted() {
      Onboarding onboarding = submit();

      assertThat(onboarding.fullName()).isEqualTo(FullName.of("Ana Souza"));
      assertThat(onboarding.cpf()).isEqualTo(CPF);
      assertThat(onboarding.email()).isEqualTo(Email.of("ana@example.com"));
      assertThat(onboarding.birthDate()).isEqualTo(BIRTH_DATE);
      assertThat(onboarding.monthlyIncome()).isEqualTo(MonthlyIncome.of("3500.00"));
      assertThat(onboarding.passwordHash())
          .as("an application sitting and waiting must never hold a readable secret")
          .isEqualTo(PasswordHash.of("{bcrypt}$2a$10$hashed"));
    }
  }

  @Nested
  @DisplayName("approval")
  class Approval {

    @Test
    @DisplayName("records the outcome and names the customer it created")
    void approvalNamesTheCustomer() {
      Onboarding onboarding = submit();
      CustomerId customerId = CustomerId.next();

      onboarding.approve(customerId);

      assertThat(onboarding.status()).isEqualTo(OnboardingStatus.APPROVED);
      assertThat(onboarding.isSettled()).isTrue();
      assertThat(onboarding.customerId()).contains(customerId);
      assertThat(onboarding.reason())
          .as("an approval carries no refusal reason")
          .isEmpty();
    }

    @Test
    @DisplayName("refuses to approve without naming a customer")
    void refusesToApproveWithoutACustomer() {
      Onboarding onboarding = submit();

      assertThatThrownBy(() -> onboarding.approve(null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("refusal")
  class Refusal {

    @Test
    @DisplayName("records the outcome and keeps the reason the applicant is told")
    void refusalKeepsTheReason() {
      Onboarding onboarding = submit();

      onboarding.reject(RejectionReason.SANCTIONS_LIST);

      assertThat(onboarding.status()).isEqualTo(OnboardingStatus.REJECTED);
      assertThat(onboarding.isSettled()).isTrue();
      assertThat(onboarding.reason()).contains(RejectionReason.SANCTIONS_LIST);
      assertThat(onboarding.customerId())
          .as("nothing is created for a refused applicant")
          .isEmpty();
    }

    @Test
    @DisplayName("refuses to reject without a reason")
    void refusesToRejectWithoutAReason() {
      Onboarding onboarding = submit();

      assertThatThrownBy(() -> onboarding.reject(null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("settling is one-way")
  class SettlingIsOneWay {

    @Test
    @DisplayName("refuses a second approval, so a repeated bureau delivery changes nothing")
    void refusesASecondApproval() {
      Onboarding onboarding = submit();
      CustomerId first = CustomerId.next();
      onboarding.approve(first);

      assertThatThrownBy(() -> onboarding.approve(CustomerId.next()))
          .isInstanceOf(OnboardingAlreadySettledException.class);
      assertThat(onboarding.customerId())
          .as("the refused second call must leave the first outcome exactly as it was")
          .contains(first);
    }

    @Test
    @DisplayName("refuses to refuse something already approved")
    void refusesToRejectAfterApproval() {
      Onboarding onboarding = submit();
      onboarding.approve(CustomerId.next());

      assertThatThrownBy(() -> onboarding.reject(RejectionReason.DOCUMENT_MISMATCH))
          .as("an account that already exists cannot be taken back by a late refusal")
          .isInstanceOf(OnboardingAlreadySettledException.class);
      assertThat(onboarding.status()).isEqualTo(OnboardingStatus.APPROVED);
      assertThat(onboarding.reason()).isEmpty();
    }

    @Test
    @DisplayName("refuses to approve something already refused")
    void refusesToApproveAfterRefusal() {
      Onboarding onboarding = submit();
      onboarding.reject(RejectionReason.SANCTIONS_LIST);

      assertThatThrownBy(() -> onboarding.approve(CustomerId.next()))
          .isInstanceOf(OnboardingAlreadySettledException.class);
      assertThat(onboarding.status()).isEqualTo(OnboardingStatus.REJECTED);
      assertThat(onboarding.customerId()).isEmpty();
    }

    @Test
    @DisplayName("refuses a second refusal")
    void refusesASecondRefusal() {
      Onboarding onboarding = submit();
      onboarding.reject(RejectionReason.SANCTIONS_LIST);

      assertThatThrownBy(() -> onboarding.reject(RejectionReason.INCOMPLETE_RECORD))
          .isInstanceOf(OnboardingAlreadySettledException.class);
      assertThat(onboarding.reason()).contains(RejectionReason.SANCTIONS_LIST);
    }

    @Test
    @DisplayName("names the application and the state it was already in")
    void namesTheApplicationAndItsState() {
      OnboardingId id = OnboardingId.next();
      Onboarding onboarding = submit(id);
      onboarding.approve(CustomerId.next());

      assertThatThrownBy(() -> onboarding.approve(CustomerId.next()))
          .hasMessageContaining(id.toString())
          .hasMessageContaining("APPROVED")
          .isInstanceOfSatisfying(
              OnboardingAlreadySettledException.class,
              thrown ->
                  assertThat(thrown.code()).isEqualTo("ONBOARDING_ALREADY_SETTLED"));
    }
  }

  @Nested
  @DisplayName("identity and disclosure")
  class IdentityAndDisclosure {

    @Test
    @DisplayName("two applications are the same when their identifiers are")
    void identityIsTheIdentifier() {
      OnboardingId id = OnboardingId.next();
      Onboarding one = submit(id);
      Onboarding settled = submit(id);
      settled.approve(CustomerId.next());

      assertThat(one).isEqualTo(settled).hasSameHashCodeAs(settled);
    }

    @Test
    @DisplayName("prints with the tax number masked")
    void printsMasked() {
      Onboarding onboarding = submit();

      assertThat(onboarding.toString())
          .as("printing an application must not put the applicant's tax number in a log line")
          .doesNotContain("12345678909")
          .contains("***.456.789-**")
          .contains("PENDING");
    }
  }
}
