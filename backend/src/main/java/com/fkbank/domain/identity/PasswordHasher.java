package com.fkbank.domain.identity;

/**
 * Turns a typed password into the form that gets stored.
 *
 * <p>A port rather than a direct call, because choosing and configuring a hashing algorithm is
 * a technical decision that changes on its own schedule, while the rule that a password is
 * never stored as typed belongs to this context permanently.
 */
public interface PasswordHasher {

  PasswordHash hash(RawPassword password);
}
