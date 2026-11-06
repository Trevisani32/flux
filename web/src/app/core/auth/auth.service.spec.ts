import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { SessaoResponse, Usuario } from './sessao';

describe('AuthService', () => {
  const usuario: Usuario = { id: 1, nome: 'Dev', email: 'dev@flux.local' };
  const sessao: SessaoResponse = {
    tokenAcesso: 'token-de-acesso',
    expiraEm: '2026-11-06T12:15:00Z',
    usuario,
  };

  let auth: AuthService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    auth = TestBed.inject(AuthService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    Reflect.deleteProperty(navigator, 'locks');
    localStorage.clear();
    sessionStorage.clear();
  });

  it('guarda o usuário e o token em memória ao entrar', () => {
    auth.entrar('dev@flux.local', 'senha-de-dev-123').subscribe();

    const requisicao = httpTesting.expectOne('/api/auth/entrar');
    requisicao.flush(sessao);

    expect(auth.usuario()).toEqual(usuario);
    expect(auth.tokenAcesso()).toBe('token-de-acesso');
    expect(auth.autenticado()).toBe(true);
  });

  it('restaura a sessão sem propagar erro e permanece deslogado com 401', async () => {
    const restauracao = auth.restaurarSessao();

    httpTesting.expectOne('/api/auth/renovar').flush(null, { status: 401, statusText: 'Não autorizado' });
    await expect(restauracao).resolves.toBeUndefined();

    expect(auth.usuario()).toBeNull();
    expect(auth.tokenAcesso()).toBeNull();
    expect(auth.autenticado()).toBe(false);
  });

  it('limpa a sessão ao sair mesmo quando o POST falha', () => {
    auth.entrar('dev@flux.local', 'senha-de-dev-123').subscribe();
    httpTesting.expectOne('/api/auth/entrar').flush(sessao);

    auth.sair().subscribe();
    httpTesting.expectOne('/api/auth/sair').flush(null, { status: 500, statusText: 'Erro interno' });

    expect(auth.usuario()).toBeNull();
    expect(auth.tokenAcesso()).toBeNull();
    expect(auth.autenticado()).toBe(false);
  });

  it('compartilha uma única renovação entre chamadas simultâneas', () => {
    const primeira = vi.fn();
    const segunda = vi.fn();

    auth.renovar().subscribe(primeira);
    auth.renovar().subscribe(segunda);

    const requisicoes = httpTesting.match('/api/auth/renovar');
    expect(requisicoes).toHaveLength(1);
    requisicoes[0].flush(sessao);

    expect(primeira).toHaveBeenCalledOnce();
    expect(segunda).toHaveBeenCalledOnce();
  });

  it('executa a renovação dentro da trava entre abas quando Web Locks existe', () => {
    const locks = {
      request: vi.fn((_nome: string, callback: () => Promise<unknown>) => callback()),
    };
    Object.defineProperty(navigator, 'locks', { value: locks, configurable: true });

    auth.renovar().subscribe();

    expect(locks.request).toHaveBeenCalledWith('flux-renovacao', expect.any(Function));
    httpTesting.expectOne('/api/auth/renovar').flush(sessao);
  });

  it('não grava a sessão no localStorage nem no sessionStorage', () => {
    auth.entrar('dev@flux.local', 'senha-de-dev-123').subscribe();
    httpTesting.expectOne('/api/auth/entrar').flush(sessao);

    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });
});
