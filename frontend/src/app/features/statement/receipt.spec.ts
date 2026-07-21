import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Receipt } from './receipt';
import { t } from '../../shared/i18n/messages';

const A_POSTING_ID = '22222222-2222-2222-2222-222222222222';
const RECEIPT_URL = `/api/account/statement/receipts/${A_POSTING_ID}`;

const A_RECEIPT = {
  id: A_POSTING_ID,
  occurredAt: '2026-07-01T12:00:00Z',
  amount: '100.00',
  currency: 'BRL',
  direction: 'OUT' as const,
  rail: 'PIX' as const,
  status: 'COMPLETED' as const,
  counterparty: '***.456.789-**',
};

/** Drains every currently queued microtask, the way an awaited HTTP flush needs to settle. */
function settle(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

describe('Receipt', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ postingId: A_POSTING_ID }) } },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  it('shows a loading state before the receipt answers', () => {
    const fixture = TestBed.createComponent(Receipt);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="receipt-loading"]')).not.toBeNull();

    http.expectOne(RECEIPT_URL).flush(A_RECEIPT);
  });

  it('renders the receipt, including a masked counterparty, once it answers', async () => {
    const fixture = TestBed.createComponent(Receipt);
    fixture.detectChanges();

    http.expectOne(RECEIPT_URL).flush(A_RECEIPT);
    await settle();
    fixture.detectChanges();

    const amount = fixture.nativeElement.querySelector('[data-testid="receipt-amount"]');
    // pt-BR currency formatting confirms the money pipe ran, not just that some text is present.
    expect(amount?.textContent).toContain('100,00');

    const counterparty = fixture.nativeElement.querySelector(
      '[data-testid="receipt-counterparty"]',
    );
    expect(counterparty?.textContent?.trim()).toBe(A_RECEIPT.counterparty);
  });

  it('omits the counterparty row when the backend sends none', async () => {
    const fixture = TestBed.createComponent(Receipt);
    fixture.detectChanges();

    http.expectOne(RECEIPT_URL).flush({ ...A_RECEIPT, rail: 'BOLETO', counterparty: null });
    await settle();
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="receipt-counterparty"]'),
    ).toBeNull();
  });

  it('shows an error with a retry that re-issues the request', async () => {
    const fixture = TestBed.createComponent(Receipt);
    fixture.detectChanges();

    http.expectOne(RECEIPT_URL).flush({}, { status: 404, statusText: 'Not Found' });
    await settle();
    fixture.detectChanges();

    const error = fixture.nativeElement.querySelector('[data-testid="receipt-error"]');
    expect(error?.textContent?.trim()).toBe(t('receipt.error'));

    const retry = fixture.nativeElement.querySelector(
      '[data-testid="receipt-retry"]',
    ) as HTMLButtonElement;
    retry.click();

    http.expectOne(RECEIPT_URL).flush(A_RECEIPT);
    await settle();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="receipt-amount"]')).not.toBeNull();
  });
});
