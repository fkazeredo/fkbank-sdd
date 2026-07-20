package com.fkbank.infra.persistence.ledger;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface AccountJpaRepository extends JpaRepository<AccountEntity, Long> {

  Optional<AccountEntity> findByCode(String code);
}
