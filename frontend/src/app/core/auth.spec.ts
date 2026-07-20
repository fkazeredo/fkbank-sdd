import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Auth } from './auth';

/**
 * Lets the pending promise chain advance.
 *
 * `completeLogin` awaits the token exchange before it issues the `/api/me` call, so the second
 * request does not exist yet at the instant the first is flushed.
 */
const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('Auth', () => {
  let auth: Auth;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    auth = TestBed.inject(Auth);
    http = TestBed.inject(HttpTestingController);
    sessionStorage.clear();
  });

  it('starts signed out', () => {
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.token()).toBeNull();
    expect(auth.currentUser()).toBeNull();
  });

  it('refuses a callback whose state does not match the one it issued', async () => {
    sessionStorage.setItem('fkbank.pkce.verifier', 'a-verifier');
    sessionStorage.setItem('fkbank.pkce.state', 'the-real-state');

    await expect(auth.completeLogin('a-code', 'a-forged-state')).rejects.toThrow(
      /does not match/i,
    );
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('refuses a callback when no flow was started in this browser', async () => {
    await expect(auth.completeLogin('a-code', 'some-state')).rejects.toThrow();
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('clears the one-time values even when the exchange is rejected', async () => {
    sessionStorage.setItem('fkbank.pkce.verifier', 'a-verifier');
    sessionStorage.setItem('fkbank.pkce.state', 'the-real-state');

    await expect(auth.completeLogin('a-code', 'a-forged-state')).rejects.toThrow();

    expect(sessionStorage.getItem('fkbank.pkce.verifier')).toBeNull();
    expect(sessionStorage.getItem('fkbank.pkce.state')).toBeNull();
  });

  it('exchanges the code with its verifier and then loads the current user', async () => {
    sessionStorage.setItem('fkbank.pkce.verifier', 'a-verifier');
    sessionStorage.setItem('fkbank.pkce.state', 'the-real-state');

    const completed = auth.completeLogin('a-code', 'the-real-state');

    const tokenRequest = http.expectOne('/oauth2/token');
    expect(tokenRequest.request.body).toContain('code_verifier=a-verifier');
    expect(tokenRequest.request.body).toContain('grant_type=authorization_code');
    tokenRequest.flush({ access_token: 'an-access-token' });
    await settle();

    http.expectOne('/api/me').flush({ username: 'e2e.user' });
    await completed;

    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.currentUser()).toEqual({ username: 'e2e.user' });
  });

  it('forgets the session on sign-out', async () => {
    sessionStorage.setItem('fkbank.pkce.verifier', 'a-verifier');
    sessionStorage.setItem('fkbank.pkce.state', 'the-real-state');
    const completed = auth.completeLogin('a-code', 'the-real-state');
    http.expectOne('/oauth2/token').flush({ access_token: 'an-access-token' });
    await settle();
    http.expectOne('/api/me').flush({ username: 'e2e.user' });
    await completed;

    auth.signOut();

    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.token()).toBeNull();
    expect(auth.currentUser()).toBeNull();
  });
});
