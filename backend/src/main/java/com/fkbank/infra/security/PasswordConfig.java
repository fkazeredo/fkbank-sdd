package com.fkbank.infra.security;

import com.fkbank.domain.identity.PasswordHash;
import com.fkbank.domain.identity.PasswordHasher;
import com.fkbank.domain.identity.RawPassword;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * How passwords are hashed, for every profile.
 *
 * <p>Not tied to any profile: real people hold credentials in production, so the thing that
 * hashes them cannot live in configuration that only exists on a developer machine.
 */
@Configuration(proxyBeanMethods = false)
public class PasswordConfig {

  /**
   * The delegating encoder stores the algorithm alongside the hash, so the day a stronger one is
   * adopted, existing hashes keep verifying instead of locking everyone out.
   */
  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  PasswordHasher passwordHasher(PasswordEncoder passwordEncoder) {
    return new EncoderPasswordHasher(passwordEncoder);
  }

  /** Adapts the framework's encoder to the port the identity context declares. */
  private record EncoderPasswordHasher(PasswordEncoder encoder) implements PasswordHasher {

    @Override
    public PasswordHash hash(RawPassword password) {
      return PasswordHash.of(encoder.encode(password.value()));
    }
  }
}
