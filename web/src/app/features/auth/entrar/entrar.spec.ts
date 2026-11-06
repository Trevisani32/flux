import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { authInterceptor } from '../../../core/auth/auth.interceptor';
import { Entrar } from './entrar';

describe('Entrar', () => {
  const email = 'dev@flux.local';
  const senha = 'senha-de-dev-123';
  const sessao = {
    tokenAcesso: 'token-de-acesso',
    expiraEm: '2026-11-06T12:15:00Z',
    usuario: { id: 1, nome: 'Dev', email },
  };

  let httpTesting: HttpTestingController;

  afterEach(() => httpTesting.verify());

  function criarComponente(retorno?: string): {
    componente: Entrar;
    elemento: HTMLElement;
    navegar: ReturnType<typeof vi.spyOn>;
    atualizar: () => void;
  } {
    TestBed.configureTestingModule({
      imports: [Entrar],
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap(retorno === undefined ? {} : { retorno }),
            },
          },
        },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    const router = TestBed.inject(Router);
    const navegar = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(Entrar);
    fixture.detectChanges();
    return {
      componente: fixture.componentInstance,
      elemento: fixture.nativeElement as HTMLElement,
      navegar,
      atualizar: () => fixture.detectChanges(),
    };
  }

  function preencherFormulario(componente: Entrar): void {
    componente.formulario.setValue({ email, senha });
  }

  it('envia o formulário para entrar', () => {
    const { componente } = criarComponente();
    preencherFormulario(componente);

    componente.entrar();

    const requisicao = httpTesting.expectOne({ method: 'POST', url: '/api/auth/entrar' });
    expect(requisicao.request.body).toEqual({ email, senha });
    requisicao.flush(sessao);
  });

  it('desabilita o botão enquanto a requisição está em andamento', () => {
    const { componente, elemento, atualizar } = criarComponente();
    preencherFormulario(componente);

    componente.entrar();
    atualizar();

    expect(elemento.querySelector<HTMLButtonElement>('button')?.disabled).toBe(true);
    httpTesting.expectOne('/api/auth/entrar').flush(sessao);
  });

  it('mostra a mensagem de credenciais inválidas e não navega com 401', () => {
    const { componente, elemento, navegar, atualizar } = criarComponente();
    preencherFormulario(componente);

    componente.entrar();
    httpTesting.expectOne('/api/auth/entrar').flush(null, { status: 401, statusText: 'Não autorizado' });
    atualizar();

    expect(elemento.querySelector('[role="alert"]')?.textContent?.trim()).toBe('E-mail ou senha inválidos');
    expect(navegar).not.toHaveBeenCalled();
  });

  it('navega para o retorno interno após entrar', () => {
    const { componente, navegar } = criarComponente('/minha-conta/senha');
    preencherFormulario(componente);

    componente.entrar();
    httpTesting.expectOne('/api/auth/entrar').flush(sessao);

    expect(navegar).toHaveBeenCalledWith('/minha-conta/senha');
  });

  it('navega para a raiz quando o retorno é uma URL externa', () => {
    const { componente, navegar } = criarComponente('https://exemplo.com');
    preencherFormulario(componente);

    componente.entrar();
    httpTesting.expectOne('/api/auth/entrar').flush(sessao);

    expect(navegar).toHaveBeenCalledWith('/');
  });

  it('navega para a raiz quando não há retorno', () => {
    const { componente, navegar } = criarComponente();
    preencherFormulario(componente);

    componente.entrar();
    httpTesting.expectOne('/api/auth/entrar').flush(sessao);

    expect(navegar).toHaveBeenCalledWith('/');
  });
});
