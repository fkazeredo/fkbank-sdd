import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MoneyPipe } from '../../shared/ui/money.pipe';
import { t } from '../../shared/i18n/messages';
import { AccountService, AccountSummary } from './account.service';

/**
 * The home screen: what the person has, and where it is held.
 *
 * The balance is rendered straight from the string the backend sent, through the money pipe.
 * No figure on this screen is computed here.
 */
@Component({
  selector: 'fk-account',
  imports: [MoneyPipe, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './account.html',
})
export class Account {
  private readonly accounts = inject(AccountService);

  readonly t = t;
  readonly account = signal<AccountSummary | null>(null);
  readonly loading = signal(true);
  readonly failed = signal(false);

  constructor() {
    void this.load();
  }

  async load(): Promise<void> {
    this.loading.set(true);
    this.failed.set(false);

    try {
      this.account.set(await this.accounts.load());
    } catch {
      // The reason is deliberately not shown: a person cannot act on a status code, and the
      // retry below is the only useful response to every failure this call can produce.
      this.failed.set(true);
    } finally {
      this.loading.set(false);
    }
  }
}
