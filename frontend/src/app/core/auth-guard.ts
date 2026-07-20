import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { Auth } from './auth';

/**
 * Keeps the authenticated shell behind a session.
 *
 * This is convenience, not security: the backend denies every unauthenticated request by
 * default, so a bypassed guard reveals empty screens rather than data.
 *
 * A visitor without a session is sent to the sign-in landing rather than straight out to the
 * Authorization Server. Someone arriving to open an account would otherwise be redirected into
 * a form asking for credentials they do not have yet, with no visible way back.
 */
export const authGuard: CanActivateFn = () => {
  const auth = inject(Auth);

  if (auth.isAuthenticated()) {
    return true;
  }

  return inject(Router).createUrlTree(['/signin']);
};
