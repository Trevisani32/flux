import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { authInterceptor, JA_REPETIDA } from './auth.interceptor';
import { AuthService } from './auth.service';
import { SessaoResponse } from './sessao';

describe('authInterceptor', () => {
  const sessaoInicial: SessaoResponse = {
    tokenAcesso: 'token-inicial',
    expiraEm: '2026-11-06T12:15:00Z',
    usuario: { id: 1, nome: 'Dev', email: 'dev@flux.local' },
  };
  const sessaoRenovada: SessaoResponse = {
    ...sessaoInicial,
    tokenAcesso: 'token-renovado',
  };

  let auth: AuthService;
  let httpTesting: HttpTestingController;
  let router: Router;
  let navegar: ReturnType<typeof vi.spyOn>;

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
    navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => httpTesting.verify());

  function prepararSessao(): void {
    auth.entrar('dev@flux.local', 'senha-de-dev-123').subscribe();
    httpTesting.expectOne('/api/auth/entrar').flush(sessaoInicial);
  }

  it('põe o Bearer em rotas protegidas e não o põe nas rotas de autenticação', () => {
    prepararSessao();

    TestBed.inject(HttpClient).get('/api/usuarios/me').subscribe();
    const protegida = httpTesting.expectOne('/api/usuarios/me');
    expect(protegida.request.headers.get('Authorization')).toBe('Bearer token-inicial');
    protegida.flush({});

    TestBed.inject(HttpClient).post('/api/auth/entrar', {}).subscribe();
    const autenticacao = httpTesting.expectOne('/api/auth/entrar');
    expect(autenticacao.request.headers.has('Authorization')).toBe(false);
    autenticacao.flush(sessaoInicial);
  });

  it('renova a sessão e repete a requisição com o token novo após 401', () => {
    prepararSessao();

    TestBed.inject(HttpClient).get('/api/usuarios/me').subscribe();
    httpTesting.expectOne('/api/usuarios/me').flush(null, { status: 401, statusText: 'Não autorizado' });
    httpTesting.expectOne('/api/auth/renovar').flush(sessaoRenovada);

    const repetida = httpTesting.expectOne('/api/usuarios/me');
    expect(repetida.request.headers.get('Authorization')).toBe('Bearer token-renovado');
    repetida.flush({});
  });

  it('compartilha a renovação quando duas requisições recebem 401 ao mesmo tempo', () => {
    prepararSessao();

    TestBed.inject(HttpClient).get('/api/contas').subscribe();
    TestBed.inject(HttpClient).get('/api/categorias').subscribe();
    httpTesting.expectOne('/api/contas').flush(null, { status: 401, statusText: 'Não autorizado' });
    httpTesting.expectOne('/api/categorias').flush(null, { status: 401, statusText: 'Não autorizado' });

    const renovacoes = httpTesting.match('/api/auth/renovar');
    expect(renovacoes).toHaveLength(1);
    renovacoes[0].flush(sessaoRenovada);

    httpTesting.expectOne('/api/contas').flush({});
    httpTesting.expectOne('/api/categorias').flush({});
  });

  it('limpa a sessão e navega para entrar quando a renovação falha', () => {
    prepararSessao();

    TestBed.inject(HttpClient).get('/api/usuarios/me').subscribe({ error: () => undefined });
    httpTesting.expectOne('/api/usuarios/me').flush(null, { status: 401, statusText: 'Não autorizado' });
    httpTesting.expectOne('/api/auth/renovar').flush(null, { status: 401, statusText: 'Não autorizado' });

    expect(auth.autenticado()).toBe(false);
    expect(navegar).toHaveBeenCalledWith(['/entrar'], { queryParams: { retorno: router.url } });
  });

  it('não renova novamente uma requisição repetida que recebe 401', () => {
    prepararSessao();

    TestBed.inject(HttpClient).get('/api/usuarios/me', {
      context: new HttpContext().set(JA_REPETIDA, true),
    }).subscribe({ error: () => undefined });
    httpTesting.expectOne('/api/usuarios/me').flush(null, { status: 401, statusText: 'Não autorizado' });

    httpTesting.expectNone('/api/auth/renovar');
  });
});
