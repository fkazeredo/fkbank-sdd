package com.fkbank.infra.security;

import com.fkbank.domain.identity.Credential;
import com.fkbank.domain.identity.CredentialRepository;
import com.fkbank.domain.identity.Username;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Signs people in against the credentials onboarding issued.
 *
 * <p>An inactive credential is reported as unknown rather than as disabled. Someone whose
 * application was refused or is still being checked learns nothing from trying to sign in, which
 * is the correct amount for an unauthenticated caller to learn about who has an account here.
 *
 * <p>A malformed username is treated the same way. The rules that make a username valid are the
 * customer context's, and a caller probing with nonsense should get the same answer as a caller
 * guessing a real address that does not exist.
 */
@Service
class CredentialUserDetailsService implements UserDetailsService {

  private final CredentialRepository credentials;

  CredentialUserDetailsService(CredentialRepository credentials) {
    this.credentials = credentials;
  }

  @Override
  public UserDetails loadUserByUsername(String username) {
    Username lookup;
    try {
      lookup = Username.of(username);
    } catch (IllegalArgumentException malformed) {
      throw new UsernameNotFoundException("no active credential");
    }

    Credential credential =
        credentials
            .findByUsername(lookup)
            .filter(Credential::isActive)
            .orElseThrow(() -> new UsernameNotFoundException("no active credential"));

    return User.withUsername(credential.username().value())
        .password(credential.passwordHash().value())
        .roles("USER")
        .build();
  }
}
