package com.fkbank.infra.persistence.account;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CurrentAccountJpaRepository extends JpaRepository<CurrentAccountEntity, UUID> {

  Optional<CurrentAccountEntity> findByCustomerId(UUID customerId);

  boolean existsByCustomerId(UUID customerId);
}
