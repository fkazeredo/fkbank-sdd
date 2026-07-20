package com.fkbank.application.api;

import com.fkbank.domain.identity.AuthenticatedUser;

/**
 * Transport representation of the signed-in principal.
 *
 * <p>A record is correct here: this is boundary data, not domain behavior. It exists so the
 * domain type never becomes JSON directly.
 */
public record MeResponse(String username) {

  static MeResponse of(AuthenticatedUser user) {
    return new MeResponse(user.username().value());
  }
}
