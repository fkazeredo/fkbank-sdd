package com.fkbank.domain.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Credential")
class CredentialTest {

  private static final UUID OWNER = UUID.randomUUID();

  private static Credential issue(CredentialId id) {
    return Credential.issue(
        id, OWNER, Username.of("ana@example.com"), PasswordHash.of("{bcrypt}$2a$10$hashed"));
  }

  @Nested
  @DisplayName("activation")
  class Activation {

    @Test
    @DisplayName("is issued unusable, so a refused applicant never holds working sign-in details")
    void isIssuedInactive() {
      Credential credential = issue(CredentialId.next());

      assertThat(credential.isActive())
          .as("sign-in details exist from sign-up but must not work until the account is opened")
          .isFalse();
    }

    @Test
    @DisplayName("becomes usable once activated")
    void activateMakesItUsable() {
      Credential credential = issue(CredentialId.next());

      credential.activate();

      assertThat(credential.isActive()).isTrue();
    }

    @Test
    @DisplayName("activating an already active credential is not an error")
    void activatingTwiceIsHarmless() {
      Credential credential = issue(CredentialId.next());

      credential.activate();
      credential.activate();

      assertThat(credential.isActive())
          .as("a repeated approval must not fail; the second one simply finds nothing to do")
          .isTrue();
    }

    @Test
    @DisplayName("rebuilds an existing credential in whichever state it was stored")
    void existingKeepsTheStoredState() {
      Credential active = Credential.existing(
          CredentialId.next(),
          OWNER,
          Username.of("ana@example.com"),
          PasswordHash.of("{bcrypt}$2a$10$hashed"),
          true);

      assertThat(active.isActive()).isTrue();
    }
  }

  @Nested
  @DisplayName("what it carries")
  class WhatItCarries {

    @Test
    @DisplayName("keeps the owner, the username and the hash it was issued with")
    void keepsWhatItWasGiven() {
      CredentialId id = CredentialId.next();

      Credential credential = issue(id);

      assertThat(credential.id()).isEqualTo(id);
      assertThat(credential.ownerId()).isEqualTo(OWNER);
      assertThat(credential.username()).isEqualTo(Username.of("ANA@EXAMPLE.COM"));
      assertThat(credential.passwordHash()).isEqualTo(PasswordHash.of("{bcrypt}$2a$10$hashed"));
    }

    @Test
    @DisplayName("prints its state without printing the stored hash")
    void printsStateWithoutTheHash() {
      Credential credential = issue(CredentialId.next());

      assertThat(credential.toString()).contains("inactive").doesNotContain("$2a$10$hashed");

      credential.activate();

      assertThat(credential.toString()).contains("active");
    }
  }

  @Nested
  @DisplayName("identity")
  class Identity {

    @Test
    @DisplayName("two credentials are the same when their identifiers are")
    void identityIsTheIdentifier() {
      CredentialId id = CredentialId.next();
      Credential one = issue(id);
      Credential sameIdDifferentDetails = Credential.existing(
          id,
          UUID.randomUUID(),
          Username.of("someone.else@example.com"),
          PasswordHash.of("{bcrypt}$2a$10$other"),
          true);

      assertThat(one)
          .as("a rotated password or a changed state does not make it a different credential")
          .isEqualTo(sameIdDifferentDetails)
          .hasSameHashCodeAs(sameIdDifferentDetails);
    }

    @Test
    @DisplayName("two credentials with different identifiers are different")
    void differentIdentifiersAreDifferent() {
      assertThat(issue(CredentialId.next())).isNotEqualTo(issue(CredentialId.next()));
    }

    @Test
    @DisplayName("is not equal to an unrelated object")
    void isNotEqualToSomethingElse() {
      assertThat(issue(CredentialId.next())).isNotEqualTo("a credential");
    }
  }
}
