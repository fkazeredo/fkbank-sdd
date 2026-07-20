import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { createCodeChallenge, createCodeVerifier, createState } from './pkce';

/** The signed-in person, as the backend describes them. */
export interface CurrentUser {
  readonly username: string;
}

interface TokenResponse {
  readonly access_token: string;
}

/**
 * Where the verifier and state wait while the browser is away at the Authorization Server.
 *
 * `sessionStorage`, not `localStorage`: these are single-flow values, and they must not
 * outlive the tab or leak into another one.
 */
const VERIFIER_KEY = 'fkbank.pkce.verifier';
const STATE_KEY = 'fkbank.pkce.state';

/**
 * Drives OIDC + PKCE login and holds the session.
 *
 * The access token lives in a signal and nowhere else. Keeping it out of `localStorage` means
 * a cross-site scripting bug cannot read it back out of persistent storage, and it disappears
 * when the tab does.
 */
@Injectable({ providedIn: 'root' })
export class Auth {
  private readonly http = inject(HttpClient);

  private readonly accessToken = signal<string | null>(null);
  private readonly user = signal<CurrentUser | null>(null);

  readonly currentUser = this.user.asReadonly();
  readonly isAuthenticated = computed(() => this.accessToken() !== null);

  /** The token the HTTP interceptor attaches; null while signed out. */
  token(): string | null {
    return this.accessToken();
  }

  /**
   * Sends the browser to the Authorization Server to sign in.
   *
   * The verifier is stored before navigating, because the page is about to be replaced and it
   * has to still be here when the callback returns.
   */
  async login(): Promise<void> {
    const verifier = createCodeVerifier();
    const state = createState();
    sessionStorage.setItem(VERIFIER_KEY, verifier);
    sessionStorage.setItem(STATE_KEY, state);

    const parameters = new URLSearchParams({
      response_type: 'code',
      client_id: 'fkbank-spa',
      scope: 'openid profile',
      redirect_uri: `${location.origin}/auth/callback`,
      state,
      code_challenge: await createCodeChallenge(verifier),
      code_challenge_method: 'S256',
    });

    location.assign(`/oauth2/authorize?${parameters.toString()}`);
  }

  /**
   * Completes the flow: exchanges the authorization code for a token and loads the profile.
   *
   * @throws Error when the callback does not match the request this app started, which is the
   *     signal that the response was not produced by our own redirect
   */
  async completeLogin(code: string, state: string | null): Promise<void> {
    const expectedState = sessionStorage.getItem(STATE_KEY);
    const verifier = sessionStorage.getItem(VERIFIER_KEY);
    sessionStorage.removeItem(STATE_KEY);
    sessionStorage.removeItem(VERIFIER_KEY);

    if (!verifier || !expectedState || state !== expectedState) {
      throw new Error('The sign-in response does not match this browser session.');
    }

    const body = new URLSearchParams({
      grant_type: 'authorization_code',
      code,
      redirect_uri: `${location.origin}/auth/callback`,
      client_id: 'fkbank-spa',
      code_verifier: verifier,
    });

    const response = await firstValueFrom(
      this.http.post<TokenResponse>('/oauth2/token', body.toString(), {
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      }),
    );

    this.accessToken.set(response.access_token);
    await this.loadCurrentUser();
  }

  /** Asks the backend who this token belongs to. */
  async loadCurrentUser(): Promise<void> {
    const me = await firstValueFrom(this.http.get<CurrentUser>('/api/me'));
    this.user.set(me);
  }

  signOut(): void {
    this.accessToken.set(null);
    this.user.set(null);
  }
}
