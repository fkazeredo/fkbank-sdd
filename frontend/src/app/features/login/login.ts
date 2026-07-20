import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Auth } from '../../core/auth';
import { t } from '../../shared/i18n/messages';

/**
 * What a visitor without a session sees.
 *
 * Signing in hands the browser to the Authorization Server: the password is typed there and
 * never passes through this application, so there is no field here to capture it and nothing
 * for this code to forward, log or hold. The screen's whole job is to offer the two doors —
 * come back in, or open an account.
 */
@Component({
  selector: 'fk-login',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex min-h-screen items-center justify-center bg-slate-50 px-4 text-slate-900">
      <section class="w-full max-w-md rounded-lg border border-slate-200 bg-white p-8">
        <h1 class="text-2xl font-semibold tracking-tight text-violet-700">{{ t('app.name') }}</h1>
        <p class="mt-2 text-lg text-slate-900">{{ t('login.tagline') }}</p>
        <p class="mt-2 text-sm text-slate-600">{{ t('login.intro') }}</p>

        <button
          type="button"
          data-testid="login-signin"
          class="mt-8 w-full rounded bg-violet-700 px-4 py-2 font-medium text-white hover:bg-violet-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-600 disabled:opacity-60"
          [disabled]="redirecting()"
          (click)="signIn()"
        >
          {{ redirecting() ? t('login.redirecting') : t('login.signIn') }}
        </button>

        <p class="mt-6 text-center text-sm text-slate-600">
          {{ t('login.noAccount') }}
          <a
            routerLink="/signup"
            data-testid="login-signup-link"
            class="font-medium text-violet-700 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-violet-600"
          >
            {{ t('login.createAccount') }}
          </a>
        </p>
      </section>
    </div>
  `,
})
export class Login {
  private readonly auth = inject(Auth);

  readonly t = t;
  readonly redirecting = signal(false);

  signIn(): void {
    this.redirecting.set(true);
    void this.auth.login();
  }
}
