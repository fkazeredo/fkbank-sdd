import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end configuration for the ephemeral stack.
 *
 * The base URL is the single IPv4 origin `compose.e2e.yaml` publishes, and it is `127.0.0.1`
 * rather than `localhost` on purpose (SPEC-0018 DL-0005): Windows resolves `localhost` to IPv6
 * `::1` first, where nothing is listening, and the resulting failure looks like an application
 * bug for about an hour before anyone suspects DNS.
 *
 * The stack itself is started by `tools/quality/verify-e2e`, not by Playwright, so the same
 * stack serves a debugging session and CI.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  // No retries: the acceptance criterion is that the journey is green on the FIRST run, and a
  // retry would hide exactly the flakiness this gate exists to surface.
  retries: 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  timeout: 30_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://127.0.0.1:8090',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
