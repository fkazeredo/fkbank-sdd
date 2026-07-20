import { test, expect, type APIRequestContext } from '@playwright/test';

/**
 * Confirms the observability surface this slice adds is actually reachable through the single
 * origin the whole app is published on (the same shape `compose.prod.yaml` uses), not just
 * directly against the backend container. Uses Playwright's HTTP request context rather than a
 * browser page: nothing here needs a rendered page, only the raw response the edge returns.
 *
 * There is no seeded credential any more - an account only exists once someone opens one - so
 * obtaining a bearer token first opens an account through the real `POST /api/signup` route
 * under the bureau emulator's `approve` scenario, then drives the same PKCE exchange a browser
 * would.
 */

const ORIGIN = 'http://127.0.0.1:8090';
const CLIENT_ID = 'fkbank-spa';
const REDIRECT_URI = `${ORIGIN}/auth/callback`;
const PASSWORD = 'Passw0rd1';

function randomCpf(): string {
  const digits: number[] = [];
  for (let i = 0; i < 9; i += 1) {
    digits.push(Math.floor(Math.random() * 10));
  }
  if (digits.every((d) => d === digits[0])) {
    digits[0] = (digits[0] + 1) % 10;
  }
  const checkDigit = (upTo: number): number => {
    let sum = 0;
    for (let i = 0; i < upTo; i += 1) {
      sum += digits[i] * (upTo + 1 - i);
    }
    const remainder = sum % 11;
    return remainder < 2 ? 0 : 11 - remainder;
  };
  digits.push(checkDigit(9));
  digits.push(checkDigit(10));
  return digits.join('');
}

/**
 * Opens an account through the real sign-up route and returns the e-mail address it can then
 * sign in with. No scenario is assigned here: the bureau container's own default
 * (`compose.e2e.yaml`, `BUREAU_DEFAULT_SCENARIO: approve`) already answers `approve` for a CPF
 * nobody has assigned one to, so the application settles synchronously and the credential is
 * active by the time this returns. Mutating the emulator's shared default would race every other
 * test that also touches it, since this suite runs `fullyParallel` (`playwright.config.ts`).
 */
async function signUpApprovedApplicant(request: APIRequestContext): Promise<string> {
  const username = `e2e.observability.${Date.now()}@example.com`;
  const response = await request.post(`${ORIGIN}/api/signup`, {
    data: {
      fullName: 'Observability Edge Applicant',
      cpf: randomCpf(),
      email: username,
      password: PASSWORD,
      birthDate: '1990-05-15',
      monthlyIncome: '3500.00',
    },
  });
  if (response.status() !== 201) {
    throw new Error(`sign-up did not settle as approved: ${response.status()} ${await response.text()}`);
  }
  return username;
}

function base64Url(bytes: Uint8Array): string {
  return Buffer.from(bytes).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

async function sha256(input: string): Promise<Uint8Array> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(input));
  return new Uint8Array(digest);
}

function randomBytes(length: number): Uint8Array {
  const bytes = new Uint8Array(length);
  crypto.getRandomValues(bytes);
  return bytes;
}

/**
 * Drives a real credential through the real PKCE journey against the embedded Authorization
 * Server, entirely through the published edge origin, and returns a bearer access token.
 */
async function obtainAccessToken(request: APIRequestContext, username: string): Promise<string> {
  const verifier = base64Url(randomBytes(32));
  const challenge = base64Url(await sha256(verifier));
  const state = base64Url(randomBytes(8));

  const authorizeParams = new URLSearchParams({
    response_type: 'code',
    client_id: CLIENT_ID,
    scope: 'openid profile',
    redirect_uri: REDIRECT_URI,
    state,
    code_challenge: challenge,
    code_challenge_method: 'S256',
  });
  const authorizeResponse = await request.get(`${ORIGIN}/oauth2/authorize?${authorizeParams}`, {
    maxRedirects: 0,
  });
  const loginUrl = authorizeResponse.headers()['location'];
  if (!loginUrl) throw new Error('authorize step did not redirect to /login');

  const loginPage = await request.get(loginUrl);
  const html = await loginPage.text();
  const csrfMatch = html.match(/name="_csrf"[^>]*value="([^"]+)"/);
  if (!csrfMatch) throw new Error('login page did not contain a CSRF token');

  const loginSubmit = await request.post(`${ORIGIN}/login`, {
    form: { username, password: PASSWORD, _csrf: csrfMatch[1] },
    maxRedirects: 0,
  });
  const reauthorizeUrl = loginSubmit.headers()['location'];
  if (!reauthorizeUrl) throw new Error('login submission did not redirect back to /oauth2/authorize');

  const reauthorizeResponse = await request.get(reauthorizeUrl, { maxRedirects: 0 });
  const callbackUrl = reauthorizeResponse.headers()['location'];
  if (!callbackUrl) throw new Error('re-authorize step did not redirect to the client callback');

  const code = new URL(callbackUrl).searchParams.get('code');
  if (!code) throw new Error('callback redirect carried no authorization code');

  const tokenResponse = await request.post(`${ORIGIN}/oauth2/token`, {
    form: {
      grant_type: 'authorization_code',
      code,
      redirect_uri: REDIRECT_URI,
      client_id: CLIENT_ID,
      code_verifier: verifier,
    },
  });
  const tokenBody = await tokenResponse.json();
  if (!tokenBody.access_token) throw new Error('token exchange did not return an access_token');
  return tokenBody.access_token as string;
}

test.describe('observability surface reachability through the published edge', () => {
  test('GET /actuator/health is reachable, public, and is not the SPA shell', async ({ request }) => {
    const response = await request.get(`${ORIGIN}/actuator/health`);

    expect(response.status()).toBe(200);
    expect(response.headers()['content-type']).not.toContain('text/html');
    const body = await response.json();
    expect(body.status).toBe('UP');
  });

  test('GET /actuator/prometheus with a valid bearer token returns Prometheus exposition text, not the SPA shell', async ({
    request,
  }) => {
    const username = await signUpApprovedApplicant(request);
    const token = await obtainAccessToken(request, username);

    const response = await request.get(`${ORIGIN}/actuator/prometheus`, {
      headers: { Authorization: `Bearer ${token}` },
    });

    expect(response.status()).toBe(200);
    expect(response.headers()['content-type']).not.toContain('text/html');
    const body = await response.text();
    expect(body).toContain('fkbank_authorization_failures_total');
  });

  test('GET /actuator/prometheus without a token is a real 401, not a silently-served SPA shell', async ({
    request,
  }) => {
    const response = await request.get(`${ORIGIN}/actuator/prometheus`);

    // The defect this test guards against returns 200 text/html (the Angular shell) because
    // nginx never proxies the path at all, rather than letting the backend answer 401.
    expect(response.status()).toBe(401);
  });

  test('GET /v3/api-docs is reachable, public, and is not the SPA shell', async ({ request }) => {
    const response = await request.get(`${ORIGIN}/v3/api-docs`);

    expect(response.status()).toBe(200);
    expect(response.headers()['content-type']).not.toContain('text/html');
    const body = await response.json();
    expect(body.paths).toHaveProperty('/api/version');
    expect(body.paths).toHaveProperty('/api/me');
  });

  test('GET /swagger-ui/index.html is reachable, public, and is not the SPA shell', async ({ request }) => {
    const response = await request.get(`${ORIGIN}/swagger-ui/index.html`);

    expect(response.status()).toBe(200);
    const body = await response.text();
    expect(body).toContain('Swagger UI');
    // The SPA shell also answers 200 for this path today (the defect this test guards
    // against): distinguish it from the real Swagger UI page by content, not just status.
    expect(body).not.toContain('<app-root>');
  });
});
