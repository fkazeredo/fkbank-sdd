import { test, expect } from '@playwright/test';

/**
 * SPEC-0018 walking-skeleton smoke journey.
 *
 * Runs against the ephemeral `compose.e2e.yaml` stack published on the single IPv4 origin
 * `http://127.0.0.1:8090` (see `playwright.config.ts`). Exercises the acceptance criterion
 * literally: the seeded `e2e` credential logs in via the PKCE redirect against the embedded
 * Authorization Server, the authenticated shell renders all six navigation entries (as router
 * links in the shell header — the six feature routes are lazily loaded, so only the active
 * route's placeholder is mounted at a time), and the username returned by `GET /api/me` is
 * displayed. Deliberately no retries (`playwright.config.ts`) — the criterion is green on the
 * first run.
 */
const SEEDED_USERNAME = 'e2e.user';
const SEEDED_PASSWORD = 'e2e-password';

const NAV_HREFS = ['/account', '/pix', '/pay', '/boxes', '/card', '/credit'];

test('seeded e2e credential logs in via PKCE and the authenticated shell renders', async ({ page }) => {
  await page.goto('/');

  // The unauthenticated shell redirects to the embedded Authorization Server's PKCE
  // authorization endpoint, which in turn serves its own login form.
  await page.waitForURL(/\/login(\?.*)?$/, { timeout: 15_000 });

  await page.locator('#username').fill(SEEDED_USERNAME);
  await page.locator('#password').fill(SEEDED_PASSWORD);
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

  await expect(page.locator('[data-testid="current-username"]')).toHaveText(new RegExp(SEEDED_USERNAME));
});
