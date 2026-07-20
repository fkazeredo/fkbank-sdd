import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Auth } from '../core/auth';
import { t } from '../shared/i18n/messages';

/** One entry of the product navigation. */
interface NavigationEntry {
  readonly path: string;
  readonly label: string;
}

/**
 * The authenticated frame: product navigation plus whoever is signed in.
 *
 * The six entries are the product's shape (docs/PRODUCT.md); each becomes a real feature in
 * its own slice. They are rendered as placeholders here so the skeleton shows the whole
 * application, not a blank page with one route.
 */
@Component({
  selector: 'fk-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './shell.html',
})
export class Shell {
  private readonly auth = inject(Auth);

  readonly currentUser = this.auth.currentUser;
  readonly t = t;

  readonly navigation: readonly NavigationEntry[] = [
    { path: '/account', label: t('nav.account') },
    { path: '/pix', label: t('nav.pix') },
    { path: '/pay', label: t('nav.pay') },
    { path: '/boxes', label: t('nav.boxes') },
    { path: '/card', label: t('nav.card') },
    { path: '/credit', label: t('nav.credit') },
  ];

  signOut(): void {
    this.auth.signOut();
    location.assign('/');
  }
}
