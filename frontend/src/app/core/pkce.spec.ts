import { describe, expect, it } from 'vitest';
import { createCodeChallenge, createCodeVerifier, createState } from './pkce';

describe('PKCE', () => {
  it('derives the challenge with S256, matching the RFC 7636 test vector', async () => {
    // The verifier and expected challenge come straight from RFC 7636 Appendix B. If our
    // encoding drifts, this fails against the specification rather than against ourselves.
    const verifier = 'dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk';

    expect(await createCodeChallenge(verifier)).toBe('E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM');
  });

  it('produces a verifier within the length RFC 7636 allows', () => {
    const verifier = createCodeVerifier();

    expect(verifier.length).toBeGreaterThanOrEqual(43);
    expect(verifier.length).toBeLessThanOrEqual(128);
  });

  it('produces base64url output with no padding or unsafe characters', () => {
    expect(createCodeVerifier()).toMatch(/^[A-Za-z0-9\-_]+$/);
  });

  it('never repeats a verifier, so an intercepted code cannot be redeemed twice', () => {
    const verifiers = new Set(Array.from({ length: 100 }, () => createCodeVerifier()));

    expect(verifiers.size).toBe(100);
  });

  it('never repeats a state value', () => {
    const states = new Set(Array.from({ length: 100 }, () => createState()));

    expect(states.size).toBe(100);
  });
});
