import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

/** Either side a posting can move money in. */
export type Direction = 'IN' | 'OUT';

/**
 * One posting on the account's statement.
 *
 * `amount` and `runningBalance` are decimal strings exactly as the ledger recorded them; this
 * feature only ever hands them to the money pipe, never parses or computes with them.
 */
export interface StatementLine {
  readonly postingId: string;
  readonly occurredAt: string;
  readonly amount: string;
  readonly currency: string;
  readonly direction: Direction;
  readonly runningBalance: string;
}

/** One page of the statement, plus an opaque cursor for the next one, or null past the end. */
export interface StatementPage {
  readonly lines: readonly StatementLine[];
  readonly nextCursor: string | null;
}

/** What a statement page request accepts; every field is optional and left out when absent. */
export interface StatementQuery {
  readonly from?: string;
  readonly to?: string;
  readonly direction?: Direction;
  readonly cursor?: string;
  readonly size?: number;
}

/**
 * A single movement's receipt: enough detail to prove what happened, once, to its owner.
 *
 * `amount` is a decimal string for the same reason as on {@link StatementLine}. `counterparty`
 * is a masked CPF only for a peer-to-peer transfer; every other rail explains the other side of
 * the movement by itself, so it arrives as `null` instead of a placeholder.
 */
export interface Receipt {
  readonly id: string;
  readonly occurredAt: string;
  readonly amount: string;
  readonly currency: string;
  readonly direction: Direction;
  readonly rail: 'PIX' | 'BOLETO' | 'CARD' | 'TRANSFER' | 'YIELD' | 'CREDIT';
  readonly status: 'COMPLETED' | 'REVERSED';
  readonly counterparty: string | null;
}

/** Reads the signed-in person's statement and the receipt behind each of its lines. */
@Injectable({ providedIn: 'root' })
export class StatementService {
  private readonly http = inject(HttpClient);

  /**
   * Loads one page of the statement.
   *
   * A field left `undefined` is never sent, so the backend's own defaults (current month, both
   * directions, first page, page size 20) apply exactly as if the caller had not offered it.
   *
   * @throws HttpErrorResponse when the request fails, which the screen turns into its error state
   */
  list(query: StatementQuery = {}): Promise<StatementPage> {
    let params = new HttpParams();
    if (query.from) {
      params = params.set('from', query.from);
    }
    if (query.to) {
      params = params.set('to', query.to);
    }
    if (query.direction) {
      params = params.set('direction', query.direction);
    }
    if (query.cursor) {
      params = params.set('cursor', query.cursor);
    }
    if (query.size !== undefined) {
      params = params.set('size', query.size);
    }

    return firstValueFrom(this.http.get<StatementPage>('/api/account/statement', { params }));
  }

  /**
   * Loads the receipt for a single posting.
   *
   * @throws HttpErrorResponse when the request fails, including a 404 for a posting that does
   *   not exist and a 404 for one that exists but belongs to someone else — the backend answers
   *   both alike on purpose, so this method has nothing more specific to report either
   */
  receipt(postingId: string): Promise<Receipt> {
    return firstValueFrom(
      this.http.get<Receipt>(`/api/account/statement/receipts/${encodeURIComponent(postingId)}`),
    );
  }
}
