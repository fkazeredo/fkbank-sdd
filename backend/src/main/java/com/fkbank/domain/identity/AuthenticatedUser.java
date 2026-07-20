package com.fkbank.domain.identity;

import java.util.Objects;

/**
 * A person who has proven who they are and is currently signed in.
 *
 * <p>Deliberately a class and not a record (CLAUDE.md invariant 9): this is the
 * lifecycle-bearing identity concept that later slices grow — transaction PIN verification
 * (SPEC-0006), failure counters and lockout, and authorization policy (SPEC-0013). Its state
 * is private, there is no setter and no all-arguments constructor; instances come from the
 * {@link #signedIn(Username)} factory, which is named in the ubiquitous language of the
 * identity context.
 *
 * <p>The model is intentionally <em>thin by scope</em>, not anemic by neglect: this slice's
 * only requirement is to describe the signed-in principal, and no acceptance criterion asks
 * the domain to decide a permission. Default-deny lives in the security configuration until a
 * spec introduces a real policy, so inventing one here would be speculative.
 */
public final class AuthenticatedUser {

  private final Username username;

  private AuthenticatedUser(Username username) {
    this.username = Objects.requireNonNull(username, "username must not be null");
  }

  /**
   * Records that the given person is signed in.
   *
   * @throws NullPointerException if {@code username} is null
   */
  public static AuthenticatedUser signedIn(Username username) {
    return new AuthenticatedUser(username);
  }

  public Username username() {
    return username;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof AuthenticatedUser that && username.equals(that.username);
  }

  @Override
  public int hashCode() {
    return username.hashCode();
  }

  @Override
  public String toString() {
    return "AuthenticatedUser[" + username + "]";
  }
}
