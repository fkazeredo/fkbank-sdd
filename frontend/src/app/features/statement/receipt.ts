import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { MoneyPipe } from '../../shared/ui/money.pipe';
import { t } from '../../shared/i18n/messages';
import { Receipt as ReceiptDetails, StatementService } from './statement.service';

/**
 * The single-movement receipt: proof of what happened, shown once, to the person it happened to.
 *
 * `counterparty` renders only when the backend sent one: a peer-to-peer transfer has another
 * person on the other side, while a boleto or PIX settlement does not, and the template mirrors
 * that distinction rather than inventing a placeholder for the missing case.
 */
@Component({
  selector: 'fk-receipt',
  imports: [MoneyPipe, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './receipt.html',
})
export class Receipt {
  private readonly route = inject(ActivatedRoute);
  private readonly statements = inject(StatementService);

  readonly t = t;
  readonly receipt = signal<ReceiptDetails | null>(null);
  readonly loading = signal(true);
  readonly failed = signal(false);

  constructor() {
    void this.load();
  }

  async load(): Promise<void> {
    const postingId = this.route.snapshot.paramMap.get('postingId');
    if (!postingId) {
      // The route contract guarantees this segment, so an absent value means the route itself
      // is misconfigured rather than something a retry could fix — the error state still gives
      // the person a way out instead of leaving the screen blank.
      this.loading.set(false);
      this.failed.set(true);
      return;
    }

    this.loading.set(true);
    this.failed.set(false);

    try {
      this.receipt.set(await this.statements.receipt(postingId));
    } catch {
      this.failed.set(true);
    } finally {
      this.loading.set(false);
    }
  }
}
