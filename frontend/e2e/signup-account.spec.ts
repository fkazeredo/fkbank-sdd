import { test, expect, type APIRequestContext } from '@playwright/test';

/**
 * Acceptance criterion 1, driven through the real browser end to end: sign-up form, the real
 * KYC bureau container, the real OIDC authorization-code + PKCE exchange, and the home screen it
 * lands on. Acceptance criterion 3 (the bureau declines) is exercised the same way, since the
 * rendered outcome screen and the reason category shown to the applicant are as much a part of
 * the acceptance criterion as the HTTP status code.
 *
 * The bureau emulator's control plane (`compose.e2e.yaml`) is published on its own port,
 * separate from the single application origin, because it is the emulator's own surface for
 * choosing what the bureau will answer next - not a route the product itself serves. Every
 * scenario assignment here is scoped to the one CPF the test that set it actually uses (the
 * control API's optional `cpf` field) rather than to the emulator's shared default: this suite
 * runs `fullyParallel` (`playwright.config.ts`), and a global default scenario is shared state
 * that a decline test racing an approve test in another worker would otherwise clobber.
 */
const BUREAU_CONTROL_ORIGIN = 'http://127.0.0.1:9101';

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

function formatCpf(digits: string): string {
  return `${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6, 9)}-${digits.slice(9)}`;
}

async function setScenarioForCpf(request: APIRequestContext, cpf: string, scenario: string): Promise<void> {
  const response = await request.post(`${BUREAU_CONTROL_ORIGIN}/control/scenario`, { data: { scenario, cpf } });
  if (!response.ok()) {
    throw new Error(`could not assign the ${scenario} scenario to ${cpf}: ${response.status()}`);
  }
}

async function fillSignUpForm(
  page: import('@playwright/test').Page,
  fields: { fullName: string; cpf: string; email: string; password: string; birthDate: string; monthlyIncome: string },
): Promise<void> {
  await page.goto('/signup');
  await page.locator('[data-testid="signup-full-name"]').fill(fields.fullName);
  await page.locator('[data-testid="signup-cpf"]').fill(fields.cpf);
  await page.locator('[data-testid="signup-email"]').fill(fields.email);
  await page.locator('[data-testid="signup-password"]').fill(fields.password);
  await page.locator('[data-testid="signup-birth-date"]').fill(fields.birthDate);
  await page.locator('[data-testid="signup-monthly-income"]').fill(fields.monthlyIncome);
  await page.locator('[data-testid="signup-submit"]').click();
}

test.describe('sign-up and account opening', () => {
  test('approved applicant signs up, signs in, and sees a zero balance on their new account', async ({
    page,
    request,
  }) => {
    const cpf = randomCpf();
    await setScenarioForCpf(request, cpf, 'approve');
    const email = `e2e.signup.${Date.now()}@example.com`;
    const password = 'Passw0rd1';

    await fillSignUpForm(page, {
      fullName: 'Happy Path Applicant',
      cpf: formatCpf(cpf),
      email,
      password,
      birthDate: '1990-05-15',
      monthlyIncome: '3500.00',
    });

    // An approved applicant is sent to sign in - there is nothing left to decide on the form.
    await page.waitForURL(/\/signin/, { timeout: 15_000 });

    await page.locator('[data-testid="login-signin"]').click();
    await page.waitForURL(/\/login(\?.*)?$/, { timeout: 15_000 });
    await page.locator('#username').fill(email);
    await page.locator('#password').fill(password);
    await page.locator('button[type="submit"]').click();

    await page.waitForURL((url) => !url.pathname.startsWith('/login') && !url.pathname.startsWith('/oauth2'), {
      timeout: 15_000,
    });

    // DL-0008's pt-BR money rendering is the deliberate, owner-decided reading of the "$0.00"
    // acceptance wording - see the money pipe test for the rule itself; this only checks that the
    // home screen actually renders it, end to end.
    await expect(page.locator('[data-testid="account-balance"]')).toHaveText(/R\$\s*0,00/);
    await expect(page.locator('[data-testid="account-branch"]')).toHaveText(/0001/);
    await expect(page.locator('[data-testid="account-number"]')).not.toHaveText(/^\s*$/);
  });

  test('a declined applicant sees the reason category and never the raw bureau payload', async ({
    page,
    request,
  }) => {
    const cpf = randomCpf();
    await setScenarioForCpf(request, cpf, 'decline');
    const email = `e2e.declined.${Date.now()}@example.com`;

    await fillSignUpForm(page, {
      fullName: 'Declined Applicant',
      cpf: formatCpf(cpf),
      email,
      password: 'Passw0rd1',
      birthDate: '1990-05-15',
      monthlyIncome: '3500.00',
    });

    const rejected = page.locator('[data-testid="signup-rejected"]');
    await expect(rejected).toBeVisible({ timeout: 15_000 });
    await expect(page.locator('[data-testid="signup-rejected-reason"]')).toBeVisible();
    // The bureau emulator's own vocabulary - the inquiry id, the raw category name it decided on
    // internally - must never leak into what the rejected applicant is shown.
    await expect(rejected).not.toContainText('inquiryId');
    await expect(rejected).not.toContainText('DOCUMENT_MISMATCH');
  });
});
