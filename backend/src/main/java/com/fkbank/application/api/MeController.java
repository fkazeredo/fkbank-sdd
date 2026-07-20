package com.fkbank.application.api;

import com.fkbank.domain.identity.AuthenticatedUser;
import com.fkbank.domain.identity.Username;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Describes the person currently signed in.
 *
 * <p>This is the walking skeleton's single protected route: it proves the whole thread —
 * OIDC/PKCE login, token validation by the resource server, and a domain type reaching the
 * edge as a DTO. Reaching it without a valid bearer token yields {@code 401} through the
 * default-deny configuration, never a partially-populated body.
 */
@RestController
@RequestMapping("/api")
public class MeController {

  /**
   * Returns the signed-in person's username.
   *
   * @param jwt the validated access token; never null, because the route is not on the
   *     (currently empty) public allowlist
   * @return {@code 200} with the username
   */
  @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
  public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
    AuthenticatedUser user = AuthenticatedUser.signedIn(Username.of(jwt.getSubject()));
    return MeResponse.of(user);
  }
}
