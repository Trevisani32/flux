import { Routes } from '@angular/router';

import { authGuard, visitanteGuard } from './core/auth/auth.guard';
import { cadastroLigadoGuard } from './core/configuracao/cadastro.guard';

export const routes: Routes = [
  {
    path: 'entrar', title: 'Entrar | Flux', canActivate: [visitanteGuard],
    loadComponent: () => import('./features/auth/entrar/entrar').then((m) => m.Entrar),
  },
  {
    path: 'criar-conta', title: 'Criar conta | Flux', canActivate: [visitanteGuard, cadastroLigadoGuard],
    loadComponent: () => import('./features/auth/criar-conta/criar-conta').then((m) => m.CriarConta),
  },
  {
    path: 'confira-seu-email', title: 'Confira seu e-mail | Flux', canActivate: [visitanteGuard],
    loadComponent: () => import('./features/auth/confira-seu-email/confira-seu-email').then((m) => m.ConfiraSeuEmail),
  },
  {
    path: 'confirmar-email', title: 'Confirmar e-mail | Flux',
    loadComponent: () => import('./features/auth/confirmar-email/confirmar-email').then((m) => m.ConfirmarEmail),
  },
  {
    path: 'esqueci-a-senha', title: 'Esqueci a senha | Flux', canActivate: [visitanteGuard],
    loadComponent: () => import('./features/auth/esqueci-a-senha/esqueci-a-senha').then((m) => m.EsqueciASenha),
  },
  {
    path: 'redefinir-senha', title: 'Redefinir senha | Flux',
    loadComponent: () => import('./features/auth/redefinir-senha/redefinir-senha').then((m) => m.RedefinirSenha),
  },
  {
    path: 'termos-de-uso', title: 'Termos de uso | Flux',
    loadComponent: () => import('./features/documentos/termos-de-uso/termos-de-uso').then((m) => m.TermosDeUso),
  },
  {
    path: 'privacidade', title: 'Política de privacidade | Flux',
    loadComponent: () => import('./features/documentos/privacidade/privacidade').then((m) => m.Privacidade),
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
        path: 'lancamentos', title: 'Lançamentos | Flux',
        loadComponent: () => import('./features/lancamentos/lancamentos').then((m) => m.Lancamentos),
      },
      {
        path: 'importacao', title: 'Importação | Flux',
        loadComponent: () => import('./features/importacao/importacao').then((m) => m.Importacao),
      },
      {
        path: 'contas', title: 'Contas | Flux',
        loadComponent: () => import('./features/contas/contas').then((m) => m.Contas),
      },
      {
        path: 'categorias', title: 'Categorias | Flux',
        loadComponent: () => import('./features/categorias/categorias').then((m) => m.Categorias),
      },
      {
        path: 'regras', title: 'Regras | Flux',
        loadComponent: () => import('./features/regras/regras').then((m) => m.Regras),
      },
      {
        path: 'minha-conta', pathMatch: 'full', title: 'Minha conta | Flux',
        loadComponent: () => import('./features/minha-conta/minha-conta').then((m) => m.MinhaConta),
      },
      {
        path: 'minha-conta/senha', title: 'Trocar senha | Flux',
        loadComponent: () => import('./features/minha-conta/trocar-senha/trocar-senha').then((m) => m.TrocarSenha),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
