package com.fkbank.infra.persistence.onboarding;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OnboardingJpaRepository extends JpaRepository<OnboardingEntity, UUID> {

  Optional<OnboardingEntity> findByCpfAndStatus(String cpf, String status);
}
