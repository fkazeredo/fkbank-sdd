package com.fkbank.domain.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AuthenticatedUser")
class AuthenticatedUserTest {

  @Test
  @DisplayName("is created through the signedIn factory and exposes who is signed in")
  void signedInExposesTheUsername() {
    AuthenticatedUser user = AuthenticatedUser.signedIn(Username.of("ada.lovelace"));

    assertThat(user.username()).isEqualTo(Username.of("ada.lovelace"));
  }

  @Test
  @DisplayName("cannot exist without a username")
  void rejectsAMissingUsername() {
    assertThatNullPointerException()
        .isThrownBy(() -> AuthenticatedUser.signedIn(null))
        .withMessageContaining("username");
  }

  @Test
  @DisplayName("two sessions of the same person are the same principal")
  void equalityIsByUsername() {
    AuthenticatedUser first = AuthenticatedUser.signedIn(Username.of("ada.lovelace"));
    AuthenticatedUser second = AuthenticatedUser.signedIn(Username.of("ADA.LOVELACE"));

    assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
  }

  @Test
  @DisplayName("describes itself without leaking anything beyond the username")
  void toStringCarriesOnlyTheUsername() {
    AuthenticatedUser user = AuthenticatedUser.signedIn(Username.of("ada.lovelace"));

    assertThat(user.toString()).isEqualTo("AuthenticatedUser[ada.lovelace]");
  }
}
