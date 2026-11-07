import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { CriarConta } from './criar-conta';

describe('CriarConta', () => {
  let http: HttpTestingController;

  afterEach(() => http.verify());

  function criar(): { componente: CriarConta; elemento: HTMLElement; navegar: ReturnType<typeof vi.spyOn>; atualizar: () => void } {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(CriarConta);
    fixture.detectChanges();
    return { componente: fixture.componentInstance, elemento: fixture.nativeElement as HTMLElement, navegar, atualizar: () => fixture.detectChanges() };
  }

  it('liga os rótulos aos quatro campos', () => {
    const { elemento } = criar();
    for (const campo of ['nome', 'email', 'senha', 'aceite']) {
      expect(elemento.querySelector(`label[for="criar-conta-${campo}"]`)).not.toBeNull();
      expect(elemento.querySelector(`#criar-conta-${campo}`)).not.toBeNull();
    }
  });

  it('mostra erros e não envia com formulário vazio', () => {
    const { componente, elemento, atualizar } = criar();
    componente.criar(); atualizar();
    expect(elemento.querySelector('#criar-conta-nome-erro')?.textContent).toBe('Informe o nome.');
    expect(elemento.querySelector('#criar-conta-email-erro')?.textContent).toBe('Informe o e-mail.');
    expect(elemento.querySelector('#criar-conta-senha-erro')?.textContent).toBe('Informe a senha.');
    expect(elemento.querySelector('#criar-conta-aceite-erro')?.textContent).toBe('Aceite os termos de uso e a política de privacidade para continuar.');
    for (const campo of ['nome', 'email', 'senha', 'aceite']) {
      expect(elemento.querySelector(`#criar-conta-${campo}`)?.getAttribute('aria-invalid')).toBe('true');
      expect(elemento.querySelector(`#criar-conta-${campo}`)?.getAttribute('aria-describedby')).toBe(`criar-conta-${campo}-erro`);
    }
    http.expectNone('/api/auth/cadastrar');
  });

  it('recusa nome apenas com espaços', () => {
    const { componente, elemento, atualizar } = criar();
    componente.formulario.setValue({ nome: '   ', email: 'ana@teste.local', senha: 'senha-de-teste-123', aceiteDosTermos: true });
    componente.criar(); atualizar();
    expect(elemento.querySelector('#criar-conta-nome-erro')?.textContent).toBe('Informe o nome.');
  });

  it('usa a dica de senha quando não há erro', () => {
    const { elemento } = criar();
    expect(elemento.querySelector('#criar-conta-senha')?.getAttribute('aria-describedby')).toBe('criar-conta-senha-dica');
  });

  it('envia dados normalizados e navega para conferir email', () => {
    const { componente, navegar } = criar();
    componente.formulario.setValue({ nome: ' Ana ', email: 'ANA@TESTE.LOCAL', senha: 'senha-de-teste-123', aceiteDosTermos: true });
    componente.criar();
    const req = http.expectOne({ method: 'POST', url: '/api/auth/cadastrar' });
    expect(req.request.body).toEqual({ nome: 'Ana', email: 'ANA@TESTE.LOCAL', senha: 'senha-de-teste-123', aceiteDosTermos: true });
    req.flush(null);
    expect(navegar).toHaveBeenCalledWith(['/confira-seu-email'], { state: { email: 'ana@teste.local' } });
  });

  it('mostra alerta de limite excedido', () => {
    const { componente, elemento, atualizar } = criar();
    componente.formulario.setValue({ nome: 'Ana', email: 'ana@teste.local', senha: 'senha-de-teste-123', aceiteDosTermos: true });
    componente.criar(); http.expectOne('/api/auth/cadastrar').flush(null, { status: 429, statusText: 'Muitas tentativas' }); atualizar();
    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('Muitas tentativas. Tente de novo em alguns minutos.');
  });

  it('mostra o detalhe da regra de negócio', () => {
    const { componente, elemento, atualizar } = criar();
    componente.formulario.setValue({ nome: 'Ana', email: 'ana@teste.local', senha: 'senha-de-teste-123', aceiteDosTermos: true });
    componente.criar(); http.expectOne('/api/auth/cadastrar').flush({ detail: 'Regra da conta.' }, { status: 422, statusText: 'Regra' }); atualizar();
    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('Regra da conta.');
  });

  it('abre os documentos em nova aba com noopener', () => {
    const { elemento } = criar();
    const links = Array.from(elemento.querySelectorAll<HTMLAnchorElement>('label[for="criar-conta-aceite"] a'));
    expect(links.map((link) => link.getAttribute('href'))).toEqual(['/termos-de-uso', '/privacidade']);
    for (const link of links) {
      expect(link.target).toBe('_blank'); expect(link.rel).toBe('noopener');
    }
  });
});
