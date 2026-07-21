import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MoneyPipe } from '../../shared/ui/money.pipe';
import { t } from '../../shared/i18n/messages';
import { Direction, StatementLine, StatementService } from './statement.service';

/** The direction filter as the screen offers it; `'ALL'` sends no `direction` at all. */
type DirectionFilter = 'ALL' | Direction;

/**
 * The product's fixed MVP timezone (`docs/DOMAIN.md` — "product timezone UTC-3 fixed in the
 * MVP"), the same boundary the backend's `StatementFilter.currentMonth` draws a period at.
 * Fixed rather than derived from the browser, so a period picked here always matches the month
 * the backend would compute by itself.
 */
const PRODUCT_TIMEZONE_OFFSET_MINUTES = -180;

/** The `[from, to)` window for the calendar month `monthsAgo` months before the current one. */
function monthWindow(monthsAgo: number): { from: string; to: string } {
  const nowAtProductTimezone = new Date(Date.now() + PRODUCT_TIMEZONE_OFFSET_MINUTES * 60_000);
  const year = nowAtProductTimezone.getUTCFullYear();
  const month = nowAtProductTimezone.getUTCMonth() - monthsAgo;
  const startOfMonth = Date.UTC(year, month, 1, 0, 0, 0);
  const startOfNextMonth = Date.UTC(year, month + 1, 1, 0, 0, 0);
  return {
    from: new Date(startOfMonth - PRODUCT_TIMEZONE_OFFSET_MINUTES * 60_000).toISOString(),
    to: new Date(startOfNextMonth - PRODUCT_TIMEZONE_OFFSET_MINUTES * 60_000).toISOString(),
  };
}

/**
 * The account's statement: every posting, newest first, with a receipt behind each line.
 *
 * Filtering by direction, or moving to a different month, both restart from page one, since the
 * backend's cursor is only meaningful for the exact filter and period it was issued under.
 * Loading more within the current period appends to what is already shown instead of replacing
 * it, so the person never loses their place while paging through history.
 */
@Component({
  selector: 'fk-statement',
  imports: [MoneyPipe, DatePipe, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './statement.html',
})
export class Statement {
  private readonly statements = inject(StatementService);

  readonly t = t;
  readonly lines = signal<readonly StatementLine[]>([]);
  readonly nextCursor = signal<string | null>(null);
  readonly filter = signal<DirectionFilter>('ALL');
  readonly monthsAgo = signal(0);
  readonly loading = signal(true);
  readonly loadingMore = signal(false);
  readonly failed = signal(false);

  readonly period = computed(() => monthWindow(this.monthsAgo()));
  readonly canGoToNextMonth = computed(() => this.monthsAgo() > 0);

  constructor() {
    void this.load();
  }

  async load(): Promise<void> {
    this.loading.set(true);
    this.failed.set(false);

    try {
      const page = await this.statements.list({ ...this.period(), direction: this.directionParam() });
      this.lines.set(page.lines);
      this.nextCursor.set(page.nextCursor);
    } catch {
      // As on the account screen: no code the person could act on, so the retry below is the
      // one useful response to every failure this call can produce.
      this.failed.set(true);
    } finally {
      this.loading.set(false);
    }
  }

  async loadMore(): Promise<void> {
    const cursor = this.nextCursor();
    if (!cursor || this.loadingMore()) {
      return;
    }

    this.loadingMore.set(true);

    try {
      const page = await this.statements.list({
        ...this.period(),
        direction: this.directionParam(),
        cursor,
      });
      this.lines.set([...this.lines(), ...page.lines]);
      this.nextCursor.set(page.nextCursor);
    } catch {
      this.failed.set(true);
    } finally {
      this.loadingMore.set(false);
    }
  }

  async setFilter(next: DirectionFilter): Promise<void> {
    if (this.filter() === next) {
      return;
    }

    this.filter.set(next);
    await this.load();
  }

  async previousMonth(): Promise<void> {
    this.monthsAgo.update((current) => current + 1);
    await this.load();
  }

  async nextMonth(): Promise<void> {
    if (!this.canGoToNextMonth()) {
      return;
    }
    this.monthsAgo.update((current) => current - 1);
    await this.load();
  }

  private directionParam(): Direction | undefined {
    const current = this.filter();
    return current === 'ALL' ? undefined : current;
  }
}
