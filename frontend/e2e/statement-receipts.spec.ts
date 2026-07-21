import { test, expect } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import path from 'node:path';

const REPO_ROOT = path.resolve(__dirname, '..', '..');
const COMPOSE_FILE = path.join(REPO_ROOT, 'compose.e2e.yaml');

/**
 * SPEC-0003 acceptance criterion 4, driven through the real browser end to end: deposit a
 * known amount, see it rendered as a statement line with its running balance, open the
 * movement's receipt and see the same amount, timestamp, rail and status.
 *
 * No deposit route exists yet in this codebase (SPEC-0004, the boleto cash-in slice, has not
 * landed) - the same gap the builder's own manual verification worked around. This test funds
 * the freshly opened account the identical way: one posting inserted directly against the
 * ephemeral e2e Postgres container, debiting the internal boleto-settlement account and
 * crediting the new customer's available account, with both legs' materialized balances
 * updated to match - exactly the movement a real boleto-settlement rail will perform once it
 * exists. The db container is resolved through `docker compose ps -q`, never a hardcoded
 * container name, so this keeps working regardless of the compose project name a given checkout
 * happens to produce.
 */
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

function dbContainerId(): string {
  return execFileSync('docker', ['compose', '-f', COMPOSE_FILE, 'ps', '-q', 'db']).toString().trim();
}

function psql(sql: string): string {
  return execFileSync(
    'docker',
    ['exec', dbContainerId(), 'psql', '-U', 'app', '-d', 'app', '-t', '-A', '-v', 'ON_ERROR_STOP=1', '-c', sql],
  ).toString().trim();
}

/**
 * Funds a freshly opened account the same way a real boleto-settlement rail eventually will,
 * and returns the new posting's public id.
 */
function fundWithABoletoDeposit(email: string, amount: string): string {
  // psql's `-t -A` output still appends the `INSERT 0 1` command tag after a RETURNING row, so
  // the new posting's id is the first line of the raw output, not the whole trimmed string.
  const rawOutput = psql(`
    WITH c AS (SELECT id FROM customer WHERE email = '${email}'),
    acct AS (SELECT a.id AS id FROM account a, c WHERE a.code = 'customer:available:' || c.id::text)
    INSERT INTO posting (id, debit_account_id, credit_account_id, amount, currency, occurred_at)
    SELECT gen_random_uuid(), 1, acct.id, ${amount}, 'BRL', now() FROM acct
    RETURNING id;
  `);
  const postingId = rawOutput.split('\n')[0].trim();
  psql(`UPDATE balance SET amount = amount - ${amount} WHERE account_id = 1;`);
  psql(`
    WITH c AS (SELECT id FROM customer WHERE email = '${email}'),
    acct AS (SELECT a.id AS id FROM account a, c WHERE a.code = 'customer:available:' || c.id::text)
    UPDATE balance SET amount = amount + ${amount} FROM acct WHERE balance.account_id = acct.id;
  `);
  return postingId;
}

test('a deposit renders a statement line and its receipt shows the same movement', async ({ page, request }) => {
  const cpf = randomCpf();
  const email = `e2e.statement.${Date.now()}@example.com`;

  const signUpResponse = await request.post('/api/signup', {
    data: {
      fullName: 'Statement Journey Applicant',
      cpf,
      email,
      password: PASSWORD,
      birthDate: '1990-05-15',
      monthlyIncome: '3500.00',
    },
  });
  expect(signUpResponse.status(), await signUpResponse.text()).toBe(201);

  const postingId = fundWithABoletoDeposit(email, '250.75');

  await page.goto('/');
  await page.waitForURL(/\/signin/, { timeout: 15_000 });
  await page.locator('[data-testid="login-signin"]').click();
  await page.waitForURL(/\/login(\?.*)?$/, { timeout: 15_000 });
  await page.locator('#username').fill(email);
  await page.locator('#password').fill(PASSWORD);
  await page.locator('button[type="submit"]').click();
  await page.waitForURL((url) => !url.pathname.startsWith('/login') && !url.pathname.startsWith('/oauth2'), {
    timeout: 15_000,
  });

  // Client-side SPA navigation (a routerlink, not a full page load), so the just-established
  // session survives the trip - a hard `page.goto` to a deep link would not (see the QA report's
  // white-box notes on session persistence, out of this slice's scope).
  await page.locator('[data-testid="account-view-statement"]').click();
  await page.waitForURL(/\/account\/statement$/, { timeout: 15_000 });

  const lines = page.locator('[data-testid="statement-line"]');
  await expect(lines).toHaveCount(1);
  await expect(page.locator('[data-testid="statement-line-amount"]')).toHaveText(/\+R\$\s*250,75/);
  await expect(page.locator('[data-testid="statement-line-balance"]')).toContainText('250,75');

  await lines.first().click();
  await page.waitForURL(new RegExp(`/account/statement/${postingId}`), { timeout: 15_000 });

  await expect(page.locator('[data-testid="receipt-amount"]')).toContainText('250,75');
  await expect(page.locator('[data-testid="receipt-rail"]')).toHaveText('BOLETO');
  await expect(page.locator('[data-testid="receipt-status"]')).toHaveText('COMPLETED');
  await expect(page.locator('[data-testid="receipt-date"]')).not.toHaveText(/^\s*$/);
});
