import { ChangeDetectionStrategy, Component } from '@angular/core';
import { t } from '../../shared/i18n/messages';

/**
 * Placeholder for the PIX feature.
 *
 * The walking skeleton renders the product's full navigation so the shape of the application
 * is visible from day one; the behavior arrives with this feature's own specification.
 */
@Component({
  selector: 'fk-pix',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section data-testid="feature-pix" class="rounded-lg border border-dashed border-slate-300 bg-white p-8">
      <h1 class="text-xl font-semibold">{{ title }}</h1>
      <p class="mt-2 text-slate-600">{{ description }}</p>
    </section>
  `,
})
export class Pix {
  readonly title = t('nav.pix');
  readonly description = t('feature.comingSoon', { feature: this.title });
}
