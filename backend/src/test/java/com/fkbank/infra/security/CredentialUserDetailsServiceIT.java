package com.fkbank.infra.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.fkbank.domain.customer.Cpf;
import com.fkbank.domain.customer.Customer;
import com.fkbank.domain.customer.CustomerId;
import com.fkbank.domain.customer.CustomerRepository;
import com.fkbank.domain.customer.FullName;
import com.fkbank.domain.customer.MonthlyIncome;
import com.fkbank.domain.identity.Credential;
import com.fkbank.domain.identity.CredentialId;
import com.fkbank.domain.identity.CredentialRepository;
import com.fkbank.domain.identity.PasswordHash;
import com.fkbank.domain.identity.PasswordHasher;
import com.fkbank.domain.identity.RawPassword;
import com.fkbank.domain.identity.Username;
import com.fkbank.testsupport.Cpfs;
import com.fkbank.testsupport.OnboardingFixture;
import com.fkbank.testsupport.OnboardingIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Who the Authorization Server is willing to sign in.
 *
 * <p>The interesting cases are the refusals. An applicant who was turned down, or whose check is
 * still running, holds a credential row that must not work — and must not be distinguishable
 * from a username that was never issued at all, because telling an unauthenticated caller which
 * addresses exist here is telling them who banks here.
 */
@DisplayName("Signing in against issued credentials")
class CredentialUserDetailsServiceIT extends OnboardingIntegrationTest {

  @Autowired private UserDetailsService userDetails;
  @Autowired private CredentialRepository credentials;
  @Autowired private CustomerRepository customers;
  @Autowired private PasswordHasher passwordHasher;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private OnboardingFixture onboarding;

  @Test
  @DisplayName("accepts a credential that opening an account activated")
  void activatedCredentialIsAccepted() {
    OnboardingFixture.SignedUpCustomer customer = onboarding.approvedCustomer();

    UserDetails found = userDetails.loadUserByUsername(customer.username());

    assertThat(found.getUsername()).isEqualTo(customer.username());
    assertThat(passwordEncoder.matches(customer.password(), found.getPassword()))
        .as("the stored hash must verify the password the applicant actually chose")
        .isTrue();
    assertThat(found.getPassword())
        .as("a password is never stored as it was typed")
        .isNotEqualTo(customer.password());
  }

  @Test
  @DisplayName("refuses a credential that was issued but never activated")
  void inactiveCredentialIsRefused() {
    // Built directly rather than through the sign-up flow, because that flow issues and
    // activates in one transaction and so cannot produce this state. The guard still has to
    // hold: it is what stops a credential from working before whatever created it succeeded,
    // and a later flow that separates the two steps must not silently open a way in.
    Customer customer =
        customers.save(
            Customer.register(
                CustomerId.next(),
                FullName.of("Grace Hopper"),
                Cpf.of(Cpfs.random()),
                OnboardingFixture.uniqueEmail(),
                OnboardingFixture.birthDate(),
                MonthlyIncome.of("3000.00"),
                LocalDate.now()));

    Username username = Username.of(customer.email().value());
    credentials.save(
        Credential.issue(
            CredentialId.next(),
            customer.id().value(),
            username,
            passwordHasher.hash(RawPassword.of("secret123"))));

    assertThatExceptionOfType(UsernameNotFoundException.class)
        .as("sign-in details must not work before the application that created them succeeded")
        .isThrownBy(() -> userDetails.loadUserByUsername(username.value()));
  }

  @Test
  @DisplayName("refuses an address nobody ever registered")
  void unknownUsernameIsRefused() {
    assertThatExceptionOfType(UsernameNotFoundException.class)
        .isThrownBy(
            () -> userDetails.loadUserByUsername(OnboardingFixture.uniqueEmail().value()));
  }

  @Test
  @DisplayName("refuses a username that could not be a username, without failing differently")
  void malformedUsernameIsRefusedTheSameWay() {
    assertThatExceptionOfType(UsernameNotFoundException.class)
        .as("a caller probing with nonsense learns exactly what a caller guessing learns")
        .isThrownBy(() -> userDetails.loadUserByUsername("   "));
  }

  @Test
  @DisplayName("stores a hash that is not the password")
  void hashIsNotThePassword() {
    PasswordHash hash = passwordHasher.hash(RawPassword.of("secret123"));

    assertThat(hash.value()).doesNotContain("secret123");
    assertThat(hash.toString())
        .as("printing a hash must not print what it protects")
        .doesNotContain(hash.value());
  }
}
