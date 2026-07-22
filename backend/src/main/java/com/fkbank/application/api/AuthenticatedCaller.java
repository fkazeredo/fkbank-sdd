package com.fkbank.application.api;

import com.fkbank.domain.customer.CustomerId;
import com.fkbank.domain.identity.CredentialRepository;
import com.fkbank.domain.identity.Username;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Works out which customer a validated bearer token belongs to.
 *
 * <p>The token's subject is the name someone signed in with, so the credential on record is what
 * links it to a person. Matching the subject against registration data directly would put that
 * link in two places, and a later change to what a username may be would quietly repoint whose
 * account or statement a token opens.
 */
@Component
class AuthenticatedCaller {

  private final CredentialRepository credentials;

  AuthenticatedCaller(CredentialRepository credentials) {
    this.credentials = credentials;
  }

  /**
   * A validated token whose credential has since disappeared is a contradiction rather than a
   * client error: the token was issued against that credential. It fails loudly instead of being
   * answered with somebody else's account or an empty one.
   */
  CustomerId resolve(Jwt jwt) {
    Username username = Username.of(jwt.getSubject());
    return credentials
        .findByUsername(username)
        .map(credential -> CustomerId.of(credential.ownerId()))
        .orElseThrow(
            () ->
                new IllegalStateException("the signed-in principal has no credential on record"));
  }
}
