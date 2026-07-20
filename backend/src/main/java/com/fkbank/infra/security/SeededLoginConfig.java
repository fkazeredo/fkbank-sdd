package com.fkbank.infra.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * The single demo credential that makes the login journey walkable before real onboarding
 * exists.
 *
 * <p>Restricted to the {@code dev} and {@code e2e} profiles, so no seeded principal can exist
 * in production even if someone sets the properties. {@link ProductionSecretsGuard} closes the
 * other half of that door by refusing to boot {@code prod} with the dev defaults in place.
 */
@Configuration(proxyBeanMethods = false)
@Profile({"dev", "e2e"})
public class SeededLoginConfig {

  /**
   * Hashes the seeded credential.
   *
   * <p>The delegating encoder stores the algorithm alongside the hash ({@code {bcrypt}...}),
   * so the day a stronger algorithm is adopted, existing hashes keep verifying instead of
   * locking everyone out.
   */
  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  UserDetailsService seededUserDetailsService(
      AuthenticationProperties properties, PasswordEncoder passwordEncoder) {
    return new InMemoryUserDetailsManager(
        User.withUsername(properties.getSeededLogin().getUsername())
            .password(passwordEncoder.encode(properties.getSeededLogin().getPassword()))
            .roles("USER")
            .build());
  }
}
