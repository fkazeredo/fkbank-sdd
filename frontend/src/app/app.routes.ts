import { Routes } from '@angular/router';
import { authGuard } from './core/auth-guard';
import { AuthCallback } from './core/auth-callback';
import { Shell } from './layout/shell';

/**
 * Public routes first, then everything else behind the authenticated shell.
 *
 * The sign-in landing is `/signin`, not `/login`: on this origin `/login` belongs to the
 * Authorization Server's own form, and the edge proxies it there before the SPA is ever
 * consulted. A route by that name here would be unreachable.
 *
 * Features are lazily loaded so a slice that grows one screen does not grow the bundle every
 * other screen has to download.
 */
export const routes: Routes = [
  { path: 'auth/callback', component: AuthCallback },
  {
    path: 'signin',
    loadComponent: () => import('./features/login/login').then((m) => m.Login),
  },
  {
    path: 'signup',
    loadComponent: () => import('./features/signup/signup').then((m) => m.Signup),
  },
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
