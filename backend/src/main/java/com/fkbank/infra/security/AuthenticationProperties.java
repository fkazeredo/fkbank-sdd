package com.fkbank.infra.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The demo credential that makes the PKCE journey walkable before real onboarding exists.
 *
 * <p>Scope is deliberately narrow: the Authorization Server itself (issuer, registered client,
 * signing key, endpoints) is configured through Spring Boot's own
 * {@code spring.security.oauth2.authorizationserver.*} properties, so nothing is
 * re-implemented here. This type owns only what FKBANK adds — a seeded login that exists
 * solely under the {@code dev} and {@code e2e} profiles (SPEC-0018 DL-0003), and which
 * {@link ProductionSecretsGuard} refuses to let reach production (OR-3).
 */
@ConfigurationProperties(prefix = "fkbank.security")
public class AuthenticationProperties {

  /** Values that only ever make sense on a developer machine or in the E2E stack. */
  public static final String DEV_DEFAULT_USERNAME = "e2e.user";

  public static final String DEV_DEFAULT_PASSWORD = "e2e-password";

  private final SeededLogin seededLogin = new SeededLogin();

  public SeededLogin getSeededLogin() {
    return seededLogin;
  }

  /** The demo credential, active only under the {@code dev} and {@code e2e} profiles. */
  public static class SeededLogin {
    private String username = DEV_DEFAULT_USERNAME;
    private String password = DEV_DEFAULT_PASSWORD;

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }
  }
}
