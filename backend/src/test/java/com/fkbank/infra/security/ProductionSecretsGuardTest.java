package com.fkbank.infra.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.fkbank.infra.integration.bureau.BureauProperties;
import com.fkbank.infra.security.ProductionSecretsGuard.InsecureProductionConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("ProductionSecretsGuard")
class ProductionSecretsGuardTest {

  private static final String REAL_ISSUER = "https://fkbank.example";

  private static final String REAL_SECRET = "a-real-secret";

  @Test
  @DisplayName("refuses to start when the bureau signing secret is still the development default")
  void rejectsDefaultBureauSecret() {
    BureauProperties bureau = bureauSigningWith(BureauProperties.DEV_DEFAULT_HMAC_SECRET);

    assertThatExceptionOfType(InsecureProductionConfigurationException.class)
        .isThrownBy(() -> ProductionSecretsGuard.verify(bureau, REAL_ISSUER))
        .satisfies(
            exception ->
                assertThat(exception.property())
                    .as("a secret published in the repository lets anyone forge a callback")
                    .isEqualTo(ProductionSecretsGuard.BUREAU_SECRET_PROPERTY));
  }

  @ParameterizedTest(name = "rejects an unset bureau secret [{0}]")
  @ValueSource(strings = {"", "   "})
  @DisplayName("treats an absent bureau signing secret as unconfigured rather than acceptable")
  void rejectsMissingBureauSecret(String secret) {
    BureauProperties bureau = bureauSigningWith(secret);

    assertThatExceptionOfType(InsecureProductionConfigurationException.class)
        .isThrownBy(() -> ProductionSecretsGuard.verify(bureau, REAL_ISSUER))
        .satisfies(
            exception ->
                assertThat(exception.property())
                    .isEqualTo(ProductionSecretsGuard.BUREAU_SECRET_PROPERTY));
  }

  @ParameterizedTest(name = "rejects the loopback issuer [{0}]")
  @ValueSource(strings = {"http://127.0.0.1:8090", "http://localhost:8080", "HTTP://LOCALHOST"})
  @DisplayName("refuses to start when the issuer was never configured for production")
  void rejectsLoopbackIssuer(String issuer) {
    assertThatExceptionOfType(InsecureProductionConfigurationException.class)
        .isThrownBy(() -> ProductionSecretsGuard.verify(bureauSigningWith(REAL_SECRET), issuer))
        .satisfies(
            exception ->
                assertThat(exception.property())
                    .isEqualTo(ProductionSecretsGuard.ISSUER_PROPERTY));
  }

  @ParameterizedTest(name = "rejects a missing issuer [{0}]")
  @ValueSource(strings = {"", "   "})
  @DisplayName("treats an absent issuer as unconfigured rather than acceptable")
  void rejectsMissingIssuer(String issuer) {
    assertThatExceptionOfType(InsecureProductionConfigurationException.class)
        .isThrownBy(() -> ProductionSecretsGuard.verify(bureauSigningWith(REAL_SECRET), issuer));
  }

  @Test
  @DisplayName("treats a null issuer as unconfigured rather than acceptable")
  void rejectsNullIssuer() {
    assertThatExceptionOfType(InsecureProductionConfigurationException.class)
        .isThrownBy(() -> ProductionSecretsGuard.verify(bureauSigningWith(REAL_SECRET), null));
  }

  @Test
  @DisplayName("never echoes the offending value into the failure message")
  void messageNamesThePropertyButNotItsValue() {
    BureauProperties bureau = bureauSigningWith(BureauProperties.DEV_DEFAULT_HMAC_SECRET);

    assertThatExceptionOfType(InsecureProductionConfigurationException.class)
        .isThrownBy(() -> ProductionSecretsGuard.verify(bureau, REAL_ISSUER))
        .satisfies(
            exception -> {
              assertThat(exception)
                  .hasMessageContaining(ProductionSecretsGuard.BUREAU_SECRET_PROPERTY);
              assertThat(exception.getMessage())
                  .as("the boot that protects a secret must not log it")
                  .doesNotContain(BureauProperties.DEV_DEFAULT_HMAC_SECRET);
            });
  }

  @Test
  @DisplayName("starts when every development default has been replaced")
  void acceptsAFullyConfiguredProduction() {
    assertThatCode(
            () -> ProductionSecretsGuard.verify(bureauSigningWith(REAL_SECRET), REAL_ISSUER))
        .doesNotThrowAnyException();
  }

  private static BureauProperties bureauSigningWith(String secret) {
    BureauProperties properties = new BureauProperties();
    properties.setHmacSecret(secret);
    return properties;
  }
}
