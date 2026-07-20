package com.fkbank.infra.security;

import com.fkbank.infra.integration.bureau.BureauProperties;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start the {@code prod} profile while any development default is still configured.
 *
 * <p>The check runs during context construction, so the failure happens <em>before</em> the web
 * server starts listening: a misconfigured production boot never opens a port, rather than
 * serving traffic with a development secret in place.
 *
 * <p>The message names the offending property so the operator can fix it without reading the
 * source. It never echoes the offending <em>value</em>, which would copy a secret into the logs
 * of the very boot that was trying to protect it.
 */
@Component
@Profile("prod")
public class ProductionSecretsGuard {

  static final String ISSUER_PROPERTY = "spring.security.oauth2.authorizationserver.issuer";

  static final String BUREAU_SECRET_PROPERTY = "fkbank.bureau.hmac-secret";

  ProductionSecretsGuard(BureauProperties bureau, Environment environment) {
    verify(bureau, environment.getProperty(ISSUER_PROPERTY));
  }

  /**
   * Validates that no development default survives into production.
   *
   * @param bureau the configured credit-bureau integration
   * @param issuer the configured Authorization Server issuer, may be null
   * @throws InsecureProductionConfigurationException naming the first offending property
   */
  static void verify(BureauProperties bureau, String issuer) {
    Map<String, Boolean> offenders = new LinkedHashMap<>();
    offenders.put(ISSUER_PROPERTY, isLoopback(issuer));
    // The signature over a callback body is the whole of the bureau's authentication. A shared
    // secret published in this repository is a secret anyone can sign with, and a forged
    // callback opens an account.
    offenders.put(BUREAU_SECRET_PROPERTY, isUnsetOrDevDefault(bureau.getHmacSecret()));

    for (Map.Entry<String, Boolean> offender : offenders.entrySet()) {
      if (Boolean.TRUE.equals(offender.getValue())) {
        throw new InsecureProductionConfigurationException(offender.getKey());
      }
    }
  }

  private static boolean isUnsetOrDevDefault(String secret) {
    return secret == null
        || secret.isBlank()
        || BureauProperties.DEV_DEFAULT_HMAC_SECRET.equals(secret);
  }

  /**
   * A loopback issuer in production means the value was never configured: tokens would be minted
   * for an address no real client can reach.
   */
  private static boolean isLoopback(String issuer) {
    if (issuer == null || issuer.isBlank()) {
      return true;
    }
    String lower = issuer.toLowerCase(Locale.ROOT);
    return lower.contains("127.0.0.1") || lower.contains("localhost");
  }

  /** Thrown when the production profile is asked to boot with a development default. */
  public static class InsecureProductionConfigurationException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final transient String property;

    InsecureProductionConfigurationException(String property) {
      super(
          "Refusing to start the 'prod' profile: '"
              + property
              + "' still holds its development default. Set it through the environment before"
              + " starting production.");
      this.property = property;
    }

    public String property() {
      return property;
    }
  }
}
