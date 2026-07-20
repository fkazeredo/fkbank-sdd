import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

/**
 * The signed-in person's account.
 *
 * `balance` is a decimal string, not a number: it arrives exactly as the ledger recorded it and
 * is only ever formatted for display. Parsing it into a floating-point value here would be the
 * first step towards arithmetic this application has no authority to perform.
 */
export interface AccountSummary {
  readonly branch: string;
  readonly number: string;
  readonly balance: string;
  readonly currency: string;
}

/** Reads the account behind the current session. */
@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly http = inject(HttpClient);

  /**
   * Loads the account belonging to whoever the bearer token identifies.
   *
   * @throws HttpErrorResponse when the request fails, which the screen turns into its error state
   */
  load(): Promise<AccountSummary> {
    return firstValueFrom(this.http.get<AccountSummary>('/api/account/me'));
  }
}
