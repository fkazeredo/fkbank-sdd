import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Auth } from './auth';
import { t } from '../shared/i18n/messages';

/**
 * Where the Authorization Server sends the browser back with an authorization code.
 *
 * It exchanges the code and then replaces itself in history, so the back button does not
 * return to a URL carrying a spent code.
 */
@Component({
  selector: 'fk-auth-callback',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p class="p-8 text-slate-600" data-testid="auth-status">{{ status() }}</p>
  `,
})
export class AuthCallback {
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);

  readonly status = signal(t('auth.signingIn'));

  constructor() {
    void this.complete();
  }

  private async complete(): Promise<void> {
    const parameters = new URLSearchParams(location.search);
    const code = parameters.get('code');
    const state = parameters.get('state');

    if (!code) {
      this.status.set(t('auth.failed'));
      return;
    }

    try {
      await this.auth.completeLogin(code, state);
      await this.router.navigate(['/account'], { replaceUrl: true });
    } catch {
      // The reason is deliberately not surfaced: it would tell an attacker which half of the
      // check failed. The console keeps nothing either, for the same reason.
      this.status.set(t('auth.failed'));
    }
  }
}
