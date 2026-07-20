package com.fkbank.domain.identity;

import java.util.Objects;

/**
 * A person who has proven who they are and is currently signed in.
 *
 * <p>A class rather than a record: signed-in identity is a lifecycle-bearing concept expected
 * to grow behavior over time — verification state, failure tracking, authorization decisions —
 * beyond the single username it carries today. Its state is private, there is no setter and no
 * all-arguments constructor; instances come from the {@link #signedIn(Username)} factory, named
 * in the ubiquitous language of the identity context.
 *
 * <p>Thin by scope, not anemic by neglect: this type only describes the signed-in principal and
 * makes no permission decision. Access control is default-deny in the security configuration
 * until this domain owns a real authorization policy.
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
