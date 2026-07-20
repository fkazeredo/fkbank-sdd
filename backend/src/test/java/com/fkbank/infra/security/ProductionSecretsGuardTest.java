package com.fkbank.infra.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.fkbank.infra.security.ProductionSecretsGuard.InsecureProductionConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("ProductionSecretsGuard")
class ProductionSecretsGuardTest {

  private static final String REAL_ISSUER = "https://fkbank.example";

  @Test
  @DisplayName("refuses to start when the seeded username is still the development default")
  void rejectsDefaultUsername() {
    AuthenticationProperties properties = propertiesWith(
        AuthenticationProperties.DEV_DEFAULT_USERNAME, "a-real-secret");

    assertThatExceptionOfType(InsecureProductionConfigurationException.class)
        .isThrownBy(() -> ProductionSecretsGuard.verify(properties, REAL_ISSUER))
        .satisfies(
            exception ->
                assertThat(exception.property())
                    .isEqualTo("fkbank.security.seeded-login.username"));
  }

  @Test
  @DisplayName("refuses to start when the seeded password is still the development default")
  void rejectsDefaultPassword() {
    AuthenticationProperties properties =
        propertiesWith("a.real.operator", AuthenticationProperties.DEV_DEFAULT_PASSWORD);

    assertThatExceptionOfType(InsecureProductionConfigurationException.class)
        .isThrownBy(() -> ProductionSecretsGuard.verify(properties, REAL_ISSUER))
        .satisfies(
            exception ->
                assertThat(exception.property())
                    .isEqualTo("fkbank.security.seeded-login.password"));
  }

  @ParameterizedTest(name = "rejects the loopback issuer [{0}]")
  @ValueSource(strings = {"http://127.0.0.1:8090", "http://localhost:8080", "HTTP://LOCALHOST"})
  @DisplayName("refuses to start when the issuer was never configured for production")
  void rejectsLoopbackIssuer(String issuer) {
    AuthenticationProperties properties = propertiesWith("a.real.operator", "a-real-secret");

    assertThatExceptionOfType(InsecureProductionConfigurationException.class)
        .isThrownBy(() -> ProductionSecretsGuard.verify(properties, issuer))
        .satisfies(
            exception ->
                assertThat(exception.property())
                    .isEqualTo(ProductionSecretsGuard.ISSUER_PROPERTY));
  }

  @ParameterizedTest(name = "rejects a missing issuer [{0}]")
  @ValueSource(strings = {"", "   "})
  @DisplayName("treats an absent issuer as unconfigured rather than acceptable")
  void rejectsMissingIssuer(String issuer) {
    AuthenticationProperties properties = propertiesWith("a.real.operator", "a-real-secret");

    assertThatExceptionOfType(InsecureProductionConfigurationException.class)
        .isThrownBy(() -> ProductionSecretsGuard.verify(properties, issuer));
  }

  @Test
  @DisplayName("treats a null issuer as unconfigured rather than acceptable")
  void rejectsNullIssuer() {
    AuthenticationProperties properties = propertiesWith("a.real.operator", "a-real-secret");

    assertThatExceptionOfType(InsecureProductionConfigurationException.class)
        .isThrownBy(() -> ProductionSecretsGuard.verify(properties, null));
  }

  @Test
  @DisplayName("never echoes the offending value into the failure message")
  void messageNamesThePropertyButNotItsValue() {
    AuthenticationProperties properties =
        propertiesWith("a.real.operator", AuthenticationProperties.DEV_DEFAULT_PASSWORD);

    assertThatExceptionOfType(InsecureProductionConfigurationException.class)
        .isThrownBy(() -> ProductionSecretsGuard.verify(properties, REAL_ISSUER))
        .satisfies(
            exception -> {
              assertThat(exception).hasMessageContaining("fkbank.security.seeded-login.password");
              assertThat(exception.getMessage())
                  .doesNotContain(AuthenticationProperties.DEV_DEFAULT_PASSWORD);
            });
  }

  @Test
  @DisplayName("starts when every development default has been replaced")
  void acceptsAFullyConfiguredProduction() {
    AuthenticationProperties properties = propertiesWith("a.real.operator", "a-real-secret");

    assertThatCode(() -> ProductionSecretsGuard.verify(properties, REAL_ISSUER))
        .doesNotThrowAnyException();
  }

  private static AuthenticationProperties propertiesWith(String username, String password) {
    AuthenticationProperties properties = new AuthenticationProperties();
    properties.getSeededLogin().setUsername(username);
    properties.getSeededLogin().setPassword(password);
    return properties;
  }
}
