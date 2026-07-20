import { test, expect } from '@playwright/test';

/**
 * SPEC-0018 walking-skeleton smoke journey.
 *
 * Runs against the ephemeral `compose.e2e.yaml` stack published on the single IPv4 origin
 * `http://127.0.0.1:8090` (see `playwright.config.ts`). There is no seeded credential any more -
 * an account only exists once someone opens it - so this test opens one itself, through the real
 * `POST /api/signup` route rather than by re-driving the sign-up form: the form itself, and what
 * an approved applicant sees on it, is covered by `signup-account.spec.ts`; what this test proves
 * is that the resulting credential actually works for the OIDC sign-in the authenticated shell
 * sits behind. Exercises the acceptance criterion literally: the unauthenticated shell redirects
 * to `/signin`, the **Sign in** action (`data-testid="login-signin"`) starts the PKCE redirect
 * against the embedded Authorization Server, the authenticated shell renders all six navigation
 * entries (as router links in the shell header - the six feature routes are lazily loaded, so
 * only the active route's placeholder is mounted at a time), and the username the SPA displays is
 * the one the applicant signed up with. Deliberately no retries (`playwright.config.ts`) - the
 * criterion is green on the first run.
 */
const PASSWORD = 'Passw0rd1';

const NAV_HREFS = ['/account', '/pix', '/pay', '/boxes', '/card', '/credit'];

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

test('a freshly opened account signs in via PKCE and the authenticated shell renders', async ({ page, request }) => {
  // No scenario is assigned here: the bureau container's own default (`compose.e2e.yaml`,
  // `BUREAU_DEFAULT_SCENARIO: approve`) already answers `approve` for a CPF nobody has assigned
  // one to, and mutating the emulator's shared default from this test - rather than scoping an
  // assignment to this test's own CPF - would race every other test that also touches it, since
  // this suite runs `fullyParallel` (`playwright.config.ts`).
  const username = `e2e.shell.${Date.now()}@example.com`;
  const signUpResponse = await request.post('/api/signup', {
    data: {
      fullName: 'Shell Journey Applicant',
      cpf: randomCpf(),
      email: username,
      password: PASSWORD,
      birthDate: '1990-05-15',
      monthlyIncome: '3500.00',
    },
  });
  expect(signUpResponse.status(), await signUpResponse.text()).toBe(201);

  await page.goto('/');

  // The unauthenticated shell redirects to its own sign-in landing page, not straight to the
  // Authorization Server: the **Sign in** button there is what starts the PKCE redirect.
  await page.waitForURL(/\/signin/, { timeout: 15_000 });
  await page.locator('[data-testid="login-signin"]').click();

  await page.waitForURL(/\/login(\?.*)?$/, { timeout: 15_000 });
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(PASSWORD);
  await page.locator('button[type="submit"]').click();

  // After a successful PKCE token exchange, the SPA lands back on the authenticated shell
  // (default route redirect to /account).
  await page.waitForURL((url) => !url.pathname.startsWith('/login') && !url.pathname.startsWith('/oauth2'), {
    timeout: 15_000,
  });

  // All six navigation entries render as links in the shell header, regardless of which one is
  // currently active.
  const navLinks = page.locator('nav a');
  await expect(navLinks).toHaveCount(NAV_HREFS.length);
  for (const href of NAV_HREFS) {
    await expect(page.locator(`nav a[href="${href}"]`)).toBeVisible();
  }

  // The default route (Account) is the one actually mounted right after login.
  await expect(page.locator('[data-testid="feature-account"]')).toBeVisible();

  await expect(page.locator('[data-testid="current-username"]')).toHaveText(new RegExp(username));
});
