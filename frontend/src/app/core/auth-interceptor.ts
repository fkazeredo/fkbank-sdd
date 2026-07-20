import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Auth } from './auth';

/**
 * Attaches the bearer token to FKBANK's own API calls.
 *
 * Scoped to `/api/`: the token endpoint authenticates by other means, and a token must never
 * be attached to a request leaving for a third party.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const token = inject(Auth).token();

  if (!token || !request.url.startsWith('/api/')) {
    return next(request);
  }

  return next(
    request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }),
  );
};
