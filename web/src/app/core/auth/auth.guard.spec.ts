import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { authGuard, visitanteGuard } from './auth.guard';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { SessaoResponse } from './sessao';

describe('guards de autenticação', () => {
  const sessao: SessaoResponse = {
    tokenAcesso: 'token-de-acesso',
    expiraEm: '2026-11-06T12:15:00Z',
    usuario: { id: 1, nome: 'Dev', email: 'dev@flux.local' },
  };

  let auth: AuthService;
  let httpTesting: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    auth = TestBed.inject(AuthService);
    httpTesting = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => httpTesting.verify());

  function entrar(): void {
    auth.entrar('dev@flux.local', 'senha-de-dev-123').subscribe();
    httpTesting.expectOne('/api/auth/entrar').flush(sessao);
  }

  function executarAuthGuard(): ReturnType<typeof authGuard> {
    return TestBed.runInInjectionContext(() => authGuard(
      {} as ActivatedRouteSnapshot,
      { url: '/minha-conta/senha' } as RouterStateSnapshot,
    ));
  }

  it('redireciona o visitante deslogado para entrar com a URL de retorno', () => {
    const resultado = executarAuthGuard();

    expect(resultado).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(resultado as UrlTree)).toBe('/entrar?retorno=%2Fminha-conta%2Fsenha');
  });

  it('permite authGuard para usuário logado', () => {
    entrar();

    expect(executarAuthGuard()).toBe(true);
  });

  it('redireciona usuário logado que visita a tela de entrar', () => {
    entrar();

    const resultado = TestBed.runInInjectionContext(() => visitanteGuard(
      {} as ActivatedRouteSnapshot,
      {} as RouterStateSnapshot,
    ));

    expect(resultado).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(resultado as UrlTree)).toBe('/');
  });

  it('permite visitante deslogado na tela de entrar', () => {
    const resultado = TestBed.runInInjectionContext(() => visitanteGuard(
      {} as ActivatedRouteSnapshot,
      {} as RouterStateSnapshot,
    ));

    expect(resultado).toBe(true);
  });
});
