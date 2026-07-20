package com.fkbank.infra.persistence.onboarding;

import com.fkbank.domain.customer.Cpf;
import com.fkbank.domain.customer.CustomerId;
import com.fkbank.domain.customer.Email;
import com.fkbank.domain.customer.FullName;
import com.fkbank.domain.customer.MonthlyIncome;
import com.fkbank.domain.identity.PasswordHash;
import com.fkbank.domain.onboarding.Onboarding;
import com.fkbank.domain.onboarding.OnboardingAlreadyPendingException;
import com.fkbank.domain.onboarding.OnboardingId;
import com.fkbank.domain.onboarding.OnboardingRepository;
import com.fkbank.domain.onboarding.OnboardingStatus;
import com.fkbank.domain.onboarding.RejectionReason;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/** Stores applications to open an account. */
@Repository
class JpaOnboardingRepository implements OnboardingRepository {

  private final OnboardingJpaRepository onboardings;
  private final Clock clock;

  JpaOnboardingRepository(OnboardingJpaRepository onboardings, Clock clock) {
    this.onboardings = onboardings;
    this.clock = clock;
  }

  @Override
  public Optional<Onboarding> findById(OnboardingId id) {
    return onboardings.findById(id.value()).map(JpaOnboardingRepository::toDomain);
  }

  @Override
  public Optional<Onboarding> findPendingByCpf(Cpf cpf) {
    return onboardings
        .findByCpfAndStatus(cpf.value(), OnboardingStatus.PENDING.name())
        .map(JpaOnboardingRepository::toDomain);
  }

  @Override
  public Onboarding save(Onboarding onboarding) {
    try {
      onboardings.saveAndFlush(
          new OnboardingEntity(
              onboarding.id().value(),
              onboarding.cpf().value(),
              onboarding.fullName().value(),
              onboarding.email().value(),
              onboarding.birthDate(),
              onboarding.monthlyIncome().value(),
              onboarding.passwordHash().value(),
              onboarding.status().name(),
              clock.instant()));
      return onboarding;
    } catch (DataIntegrityViolationException collision) {
      // An application for this CPF was already waiting, or became so between the caller's
      // check and this insert. The index that refused it is the only thing that can settle
      // that race, and the caller answers by fetching whichever application won.
      throw new OnboardingAlreadyPendingException(
          "an application for this cpf is already pending", collision);
    }
  }

  @Override
  public Onboarding update(Onboarding onboarding) {
    OnboardingEntity stored =
        onboardings
            .findById(onboarding.id().value())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "onboarding " + onboarding.id() + " disappeared while being settled"));

    stored.settle(
        onboarding.status().name(),
        onboarding.reason().map(Enum::name).orElse(null),
        onboarding.customerId().map(CustomerId::value).orElse(null),
        clock.instant());
    onboardings.saveAndFlush(stored);
    return onboarding;
  }

  private static Onboarding toDomain(OnboardingEntity entity) {
    return Onboarding.existing(
        OnboardingId.of(entity.getId()),
        FullName.of(entity.getFullName()),
        Cpf.of(entity.getCpf()),
        Email.of(entity.getEmail()),
        entity.getBirthDate(),
        MonthlyIncome.of(entity.getMonthlyIncome()),
        PasswordHash.of(entity.getPasswordHash()),
        OnboardingStatus.valueOf(entity.getStatus()),
        reasonOf(entity.getReasonCategory()),
        customerIdOf(entity.getCustomerId()));
  }

  private static RejectionReason reasonOf(String stored) {
    return stored == null ? null : RejectionReason.valueOf(stored);
  }

  private static CustomerId customerIdOf(UUID stored) {
    return stored == null ? null : CustomerId.of(stored);
  }
}
