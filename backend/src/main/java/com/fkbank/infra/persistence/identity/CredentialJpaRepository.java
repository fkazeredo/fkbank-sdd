package com.fkbank.infra.persistence.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CredentialJpaRepository extends JpaRepository<CredentialEntity, UUID> {

  Optional<CredentialEntity> findByUsername(String username);

  boolean existsByUsername(String username);
}
