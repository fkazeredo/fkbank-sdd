import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Statement } from './statement';
import { t } from '../../shared/i18n/messages';

const A_LINE = {
  postingId: '11111111-1111-1111-1111-111111111111',
  occurredAt: '2026-07-01T12:00:00Z',
  amount: '42.50',
  currency: 'BRL',
  direction: 'IN' as const,
  runningBalance: '1042.50',
};

const STATEMENT_URL = '/api/account/statement';

/** Drains every currently queued microtask, the way an awaited HTTP flush needs to settle. */
function settle(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

describe('Statement', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  it('shows a loading state before the first page answers', () => {
    const fixture = TestBed.createComponent(Statement);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="statement-loading"]'),
    ).not.toBeNull();

    http.expectOne(STATEMENT_URL).flush({ lines: [], nextCursor: null });
  });

  it('shows the empty message when the statement has no lines', async () => {
    const fixture = TestBed.createComponent(Statement);
    fixture.detectChanges();

    http.expectOne(STATEMENT_URL).flush({ lines: [], nextCursor: null });
    await settle();
    fixture.detectChanges();

    const empty = fixture.nativeElement.querySelector('[data-testid="statement-empty"]');
    expect(empty?.textContent?.trim()).toBe(t('statement.empty'));
  });

  it('renders a line with its amount through the money pipe', async () => {
    const fixture = TestBed.createComponent(Statement);
    fixture.detectChanges();

    http.expectOne(STATEMENT_URL).flush({ lines: [A_LINE], nextCursor: null });
    await settle();
    fixture.detectChanges();

    const amount = fixture.nativeElement.querySelector('[data-testid="statement-line-amount"]');
    // The money pipe renders pt-BR currency formatting (a comma decimal separator), never the
    // raw decimal string, so the assertion checks the pipe actually ran rather than merely
    // that some text is present.
    expect(amount?.textContent).toContain('42,50');
    expect(amount?.textContent?.trim().startsWith('+')).toBe(true);

    const link = fixture.nativeElement.querySelector('[data-testid="statement-line"]');
    expect(link?.getAttribute('href')).toBe('/account/statement/11111111-1111-1111-1111-111111111111');
  });

  it('shows an error with a retry that re-issues the request', async () => {
    const fixture = TestBed.createComponent(Statement);
    fixture.detectChanges();

    http
      .expectOne(STATEMENT_URL)
      .flush({}, { status: 500, statusText: 'Internal Server Error' });
    await settle();
    fixture.detectChanges();

    const error = fixture.nativeElement.querySelector('[data-testid="statement-error"]');
    expect(error?.textContent?.trim()).toBe(t('statement.error'));

    const retry = fixture.nativeElement.querySelector(
      '[data-testid="statement-retry"]',
    ) as HTMLButtonElement;
    retry.click();

    http.expectOne(STATEMENT_URL).flush({ lines: [], nextCursor: null });
    await settle();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="statement-empty"]')).not.toBeNull();
  });

  it('re-fetches page one, without a cursor, when the direction filter changes', async () => {
    const fixture = TestBed.createComponent(Statement);
    fixture.detectChanges();

    http.expectOne(STATEMENT_URL).flush({ lines: [], nextCursor: null });
    await settle();
    fixture.detectChanges();

    const inButton = fixture.nativeElement.querySelector(
      '[data-testid="statement-filter-in"]',
    ) as HTMLButtonElement;
    inButton.click();

    const request = http.expectOne(
      (req) => req.url === STATEMENT_URL && req.params.get('direction') === 'IN',
    );
    expect(request.request.params.has('cursor')).toBe(false);
    request.flush({ lines: [], nextCursor: null });
  });
});
