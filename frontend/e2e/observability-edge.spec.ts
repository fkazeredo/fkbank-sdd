import { test, expect, type APIRequestContext } from '@playwright/test';

/**
 * Confirms the observability surface this slice adds is actually reachable through the single
 * origin the whole app is published on (the same shape `compose.prod.yaml` uses), not just
 * directly against the backend container. Uses Playwright's HTTP request context rather than a
 * browser page: nothing here needs a rendered page, only the raw response the edge returns.
 */

const ORIGIN = 'http://127.0.0.1:8090';
const CLIENT_ID = 'fkbank-spa';
const REDIRECT_URI = `${ORIGIN}/auth/callback`;
const USERNAME = 'e2e.user';
const PASSWORD = 'e2e-password';

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
 * Drives the seeded credential through the real PKCE journey against the embedded Authorization
 * Server, entirely through the published edge origin, and returns a bearer access token.
 */
async function obtainAccessToken(request: APIRequestContext): Promise<string> {
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
    form: { username: USERNAME, password: PASSWORD, _csrf: csrfMatch[1] },
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
    const token = await obtainAccessToken(request);

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
