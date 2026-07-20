package com.fkbank.domain.onboarding;

/**
 * Decides whether a callback really came from the bureau.
 *
 * <p>The callback arrives unauthenticated — the bureau holds no account here and carries no
 * token — so the signature over the request body is the only thing separating the bureau's
 * answer from anyone else's. Without it, approving an application would be an open endpoint.
 *
 * <p>A port, because the algorithm and the shared secret are technical concerns, while the rule
 * that an unverified callback changes nothing belongs to this context.
 */
public interface BureauCallbackSignature {

  /**
   * Whether {@code signature} is the bureau's signature over exactly these bytes.
   *
   * @param body the raw request body as received, before any parsing
   * @param signature the signature the caller presented, may be null or malformed
   */
  boolean isValid(byte[] body, String signature);
}
