/**
 * The cryptographic half of the authorization-code + PKCE flow.
 *
 * Kept as pure functions, separate from the service that performs the redirects, so the part
 * that must be exactly right can be asserted directly instead of through a browser journey.
 *
 * No OIDC library is used here deliberately: the SPA drives a single flow against our own
 * Authorization Server, and the real security boundary is server-side token validation, not
 * this redirect choreography.
 */

/** Bytes of entropy in a verifier. RFC 7636 allows 43-128 characters; 32 bytes gives 43. */
const VERIFIER_BYTES = 32;

/** RFC 4648 §5 base64url: URL-safe alphabet, no padding. */
function base64UrlEncode(bytes: Uint8Array): string {
  let binary = '';
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/**
 * Creates the secret this browser keeps until it exchanges the authorization code.
 *
 * It must come from a cryptographically secure source: a guessable verifier would let an
 * attacker who intercepted the code redeem it.
 */
export function createCodeVerifier(): string {
  const bytes = new Uint8Array(VERIFIER_BYTES);
  crypto.getRandomValues(bytes);
  return base64UrlEncode(bytes);
}

/**
 * Derives the challenge sent in the authorization request.
 *
 * S256 rather than `plain`: the challenge travels through the browser's address bar and server
 * logs, so it must not be the verifier itself.
 */
export async function createCodeChallenge(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  return base64UrlEncode(new Uint8Array(digest));
}

/** Opaque value that ties a callback back to the request this app started. */
export function createState(): string {
  return createCodeVerifier();
}
