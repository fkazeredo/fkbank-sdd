import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MoneyPipe } from '../../shared/ui/money.pipe';
import { t } from '../../shared/i18n/messages';
import { Direction, StatementLine, StatementService } from './statement.service';

/** The direction filter as the screen offers it; `'ALL'` sends no `direction` at all. */
type DirectionFilter = 'ALL' | Direction;

/**
 * The account's statement: every posting, newest first, with a receipt behind each line.
 *
 * Filtering by direction restarts from page one, since the backend's cursor is only meaningful
 * for the filter it was issued under. Loading more appends to what is already shown instead of
 * replacing it, so the person never loses their place while paging through history.
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
  readonly loading = signal(true);
  readonly loadingMore = signal(false);
  readonly failed = signal(false);

  constructor() {
    void this.load();
  }

  async load(): Promise<void> {
    this.loading.set(true);
    this.failed.set(false);

    try {
      const page = await this.statements.list({ direction: this.directionParam() });
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
      const page = await this.statements.list({ direction: this.directionParam(), cursor });
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

  private directionParam(): Direction | undefined {
    const current = this.filter();
    return current === 'ALL' ? undefined : current;
  }
}
