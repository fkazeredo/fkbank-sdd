import { ChangeDetectionStrategy, Component } from '@angular/core';
import { t } from '../../shared/i18n/messages';

/**
 * Placeholder for the Boxes feature.
 *
 * The walking skeleton renders the product's full navigation so the shape of the application
 * is visible from day one; the behavior arrives with this feature's own specification.
 */
@Component({
  selector: 'fk-boxes',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section data-testid="feature-boxes" class="rounded-lg border border-dashed border-slate-300 bg-white p-8">
      <h1 class="text-xl font-semibold">{{ title }}</h1>
      <p class="mt-2 text-slate-600">{{ description }}</p>
    </section>
  `,
})
export class Boxes {
  readonly title = t('nav.boxes');
  readonly description = t('feature.comingSoon', { feature: this.title });
}
