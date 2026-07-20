import { CanActivateFn } from '@angular/router';
import { inject } from '@angular/core';
import { Auth } from './auth';

/**
 * Keeps the authenticated shell behind a session.
 *
 * This is convenience, not security: the backend denies every unauthenticated request by
 * default (OR-2), so a bypassed guard reveals empty screens rather than data.
 */
export const authGuard: CanActivateFn = () => {
  const auth = inject(Auth);

  if (auth.isAuthenticated()) {
    return true;
  }

  void auth.login();
  return false;
};
