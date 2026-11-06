import { Routes } from '@angular/router';

import { authGuard, visitanteGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: 'entrar', title: 'Entrar | Flux', canActivate: [visitanteGuard],
    loadComponent: () => import('./features/auth/entrar/entrar').then((m) => m.Entrar),
  },
  {
    path: '', canActivate: [authGuard],
    loadComponent: () => import('./layout/shell/shell').then((m) => m.Shell),
    children: [
      {
        path: '', pathMatch: 'full', title: 'Início | Flux',
        loadComponent: () => import('./features/inicio/inicio').then((m) => m.Inicio),
      },
      {
        path: 'minha-conta/senha', title: 'Trocar senha | Flux',
        loadComponent: () => import('./features/minha-conta/trocar-senha/trocar-senha').then((m) => m.TrocarSenha),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
