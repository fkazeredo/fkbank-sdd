import { Routes } from '@angular/router';
import { authGuard } from './core/auth-guard';
import { AuthCallback } from './core/auth-callback';
import { Shell } from './layout/shell';

/**
 * Every product route lives inside the authenticated shell.
 *
 * Features are lazily loaded so a slice that grows one screen does not grow the bundle every
 * other screen has to download.
 */
export const routes: Routes = [
  { path: 'auth/callback', component: AuthCallback },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'account' },
      {
        path: 'account',
        loadComponent: () => import('./features/account/account').then((m) => m.Account),
      },
      { path: 'pix', loadComponent: () => import('./features/pix/pix').then((m) => m.Pix) },
      { path: 'pay', loadComponent: () => import('./features/pay/pay').then((m) => m.Pay) },
      {
        path: 'boxes',
        loadComponent: () => import('./features/boxes/boxes').then((m) => m.Boxes),
      },
      { path: 'card', loadComponent: () => import('./features/card/card').then((m) => m.Card) },
      {
        path: 'credit',
        loadComponent: () => import('./features/credit/credit').then((m) => m.Credit),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
