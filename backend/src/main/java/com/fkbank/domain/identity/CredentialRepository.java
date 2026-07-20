package com.fkbank.domain.identity;

import java.util.Optional;

/** Stores and retrieves sign-in details. */
public interface CredentialRepository {

  Optional<Credential> findByUsername(Username username);

  boolean existsByUsername(Username username);

  Credential save(Credential credential);
}
