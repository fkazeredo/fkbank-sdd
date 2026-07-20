package com.fkbank.infra.persistence.customer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CustomerJpaRepository extends JpaRepository<CustomerEntity, UUID> {

  Optional<CustomerEntity> findByCpf(String cpf);

  boolean existsByCpf(String cpf);

  boolean existsByEmail(String email);
}
