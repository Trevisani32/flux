import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { authInterceptor } from '../../../core/auth/auth.interceptor';
import { ConfiguracaoService } from '../../../core/configuracao/configuracao.service';
import { EstadoDaNavegacao } from '../../../core/navegacao/estado-da-navegacao';
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

  function criarComponente(retorno?: string, opcoes: { aviso?: string; cadastroPublico?: boolean } = {}): {
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
        { provide: EstadoDaNavegacao, useValue: { texto: (chave: string) => (chave === 'aviso' ? opcoes.aviso ?? null : null) } },
        { provide: ConfiguracaoService, useValue: { cadastroPublico: signal(opcoes.cadastroPublico ?? false) } },
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

    expect(elemento.querySelector<HTMLButtonElement>('button[type="submit"]')?.disabled).toBe(true);
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

  it('liga os rótulos aos campos nativos', () => {
    const { elemento } = criarComponente();
    expect(elemento.querySelector('label[for="entrar-email"]')?.textContent).toBe('E-mail');
    expect(elemento.querySelector('label[for="entrar-senha"]')?.textContent).toBe('Senha');
    expect(elemento.querySelector('#entrar-email')).not.toBeNull();
    expect(elemento.querySelector('#entrar-senha')).not.toBeNull();
  });

  it('mostra erros nativos ao enviar formulário vazio', () => {
    const { componente, elemento, atualizar } = criarComponente();
    componente.entrar();
    atualizar();
    const emailInput = elemento.querySelector('#entrar-email');
    expect(emailInput?.getAttribute('aria-invalid')).toBe('true');
    expect(emailInput?.getAttribute('aria-describedby')).toBe('entrar-email-erro');
    expect(elemento.querySelector('#entrar-email-erro')?.textContent).toBe('Informe o e-mail.');
    expect(elemento.querySelector('#entrar-senha-erro')?.textContent).toBe('Informe a senha.');
    httpTesting.expectNone('/api/auth/entrar');
  });

  it('alterna a visibilidade da senha sem mudar o rótulo', () => {
    const { elemento, atualizar } = criarComponente();
    const botao = elemento.querySelector<HTMLButtonElement>('button[aria-label="Mostrar senha"]');
    const campo = elemento.querySelector<HTMLInputElement>('#entrar-senha');
    expect(botao?.getAttribute('aria-pressed')).toBe('false');
    expect(campo?.type).toBe('password');
    botao?.click();
    atualizar();
    expect(botao?.getAttribute('aria-pressed')).toBe('true');
    expect(campo?.type).toBe('text');
    botao?.click();
    atualizar();
    expect(campo?.type).toBe('password');
  });

  it('com 403 de e-mail não confirmado oferece reenviar e mostra o sucesso', () => {
    const { componente, elemento, atualizar } = criarComponente();
    componente.formulario.setValue({ email: 'Dev@Flux.Local', senha });
    componente.entrar();
    httpTesting.expectOne('/api/auth/entrar').flush(
      { detail: 'Confirme seu e-mail para entrar.', codigo: 'EMAIL_NAO_CONFIRMADO' },
      { status: 403, statusText: 'Proibido' },
    );
    atualizar();
    const alerta = elemento.querySelector('[role="alert"]');
    expect(alerta?.querySelector('strong')?.textContent).toBe('Confirme seu e-mail para entrar.');
    expect(alerta?.querySelector('b')?.textContent).toBe('dev@flux.local');
    alerta?.querySelector<HTMLButtonElement>('button')?.click();
    const reenvio = httpTesting.expectOne({ method: 'POST', url: '/api/auth/reenviar-confirmacao' });
    expect(reenvio.request.body).toEqual({ email: 'dev@flux.local' });
    reenvio.flush(null, { status: 202, statusText: 'Aceito' });
    atualizar();
    expect(elemento.querySelector('[role="status"] strong')?.textContent).toBe('Link reenviado.');
  });

  it('mostra muitas tentativas com 429', () => {
    const { componente, elemento, atualizar } = criarComponente();
    preencherFormulario(componente);
    componente.entrar();
    httpTesting.expectOne('/api/auth/entrar').flush(null, { status: 429, statusText: 'Muitas tentativas' });
    atualizar();
    expect(elemento.querySelector('[role="alert"]')?.textContent?.trim()).toBe('Muitas tentativas. Tente de novo em alguns minutos.');
  });

  it('mostra o aviso de conta excluída vindo da navegação', () => {
    const { elemento } = criarComponente(undefined, { aviso: 'conta-excluida' });
    expect(elemento.querySelector('[role="status"] strong')?.textContent).toBe('Sua conta foi excluída.');
  });

  it('mostra Criar conta só com o cadastro público ligado e sempre Esqueci a senha', () => {
    const desligado = criarComponente();
    expect(desligado.elemento.querySelector('a[href="/criar-conta"]')).toBeNull();
    expect(desligado.elemento.querySelector('a[href="/esqueci-a-senha"]')?.textContent).toBe('Esqueci a senha');
    TestBed.resetTestingModule();
    const ligado = criarComponente(undefined, { cadastroPublico: true });
    expect(ligado.elemento.querySelector('a[href="/criar-conta"]')?.textContent).toBe('Criar conta');
  });
});
