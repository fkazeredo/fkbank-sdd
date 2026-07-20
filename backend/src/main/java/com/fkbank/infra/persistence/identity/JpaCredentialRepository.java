package com.fkbank.infra.persistence.identity;

import com.fkbank.domain.identity.Credential;
import com.fkbank.domain.identity.CredentialId;
import com.fkbank.domain.identity.CredentialRepository;
import com.fkbank.domain.identity.PasswordHash;
import com.fkbank.domain.identity.Username;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Stores sign-in details. */
@Repository
class JpaCredentialRepository implements CredentialRepository {

  private final CredentialJpaRepository credentials;

  JpaCredentialRepository(CredentialJpaRepository credentials) {
    this.credentials = credentials;
  }

  @Override
  public Optional<Credential> findByUsername(Username username) {
    return credentials.findByUsername(username.value()).map(JpaCredentialRepository::toDomain);
  }

  @Override
  public boolean existsByUsername(Username username) {
    return credentials.existsByUsername(username.value());
  }

  @Override
  public Credential save(Credential credential) {
    credentials
        .findById(credential.id().value())
        .ifPresentOrElse(
            stored -> stored.setActive(credential.isActive()),
            () ->
                credentials.save(
                    new CredentialEntity(
                        credential.id().value(),
                        credential.ownerId(),
                        credential.username().value(),
                        credential.passwordHash().value(),
                        credential.isActive())));
    return credential;
  }

  private static Credential toDomain(CredentialEntity entity) {
    return Credential.existing(
        CredentialId.of(entity.getId()),
        entity.getCustomerId(),
        Username.of(entity.getUsername()),
        PasswordHash.of(entity.getPasswordHash()),
        entity.isActive());
  }
}
