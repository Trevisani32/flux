import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ConfirmarEmail } from './confirmar-email';

describe('ConfirmarEmail', () => {
  let httpTesting: HttpTestingController;

  afterEach(() => httpTesting.verify());

  function criar(fragmento: string | null): { elemento: HTMLElement; atualizar: () => void; trocarUrl: ReturnType<typeof vi.spyOn> } {
    TestBed.configureTestingModule({
      imports: [ConfirmarEmail],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { fragment: fragmento, queryParamMap: convertToParamMap({}) } } },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    const trocarUrl = vi.spyOn(TestBed.inject(Location), 'replaceState');
    const fixture = TestBed.createComponent(ConfirmarEmail);
    fixture.detectChanges();
    return { elemento: fixture.nativeElement as HTMLElement, atualizar: () => fixture.detectChanges(), trocarUrl };
  }

  it('confirma o cadastro com o token do fragmento e tira o token da URL', () => {
    const { elemento, atualizar, trocarUrl } = criar('token=abc_DEF-123');
    expect(trocarUrl).toHaveBeenCalledWith('/confirmar-email');
    expect(elemento.querySelector('h1')?.textContent).toBe('Confirmando seu e-mail…');
    const requisicao = httpTesting.expectOne({ method: 'POST', url: '/api/auth/confirmar-email' });
    expect(requisicao.request.body).toEqual({ token: 'abc_DEF-123' });
    requisicao.flush({ tipo: 'CADASTRO', email: 'ana@teste.local' });
    atualizar();
    expect(elemento.querySelector('h1')?.textContent).toBe('E-mail confirmado');
    expect(elemento.querySelector('a[href="/entrar"]')?.textContent?.trim()).toBe('Entrar');
  });

  it('mostra o novo e-mail na troca confirmada', () => {
    const { elemento, atualizar } = criar('token=xyz');
    httpTesting.expectOne('/api/auth/confirmar-email').flush({ tipo: 'TROCA_DE_EMAIL', email: 'novo@teste.local' });
    atualizar();
    expect(elemento.querySelector('h1')?.textContent).toBe('Novo e-mail confirmado');
    expect(elemento.querySelector('.flux-status__texto b')?.textContent).toBe('novo@teste.local');
    expect(elemento.querySelector('a[href="/minha-conta"]')?.textContent?.trim()).toBe('Ir para o Flux');
  });

  it('mostra link vencido com 422 e sem token não chama a API', () => {
    const { elemento, atualizar } = criar('token=usado');
    httpTesting.expectOne('/api/auth/confirmar-email').flush(
      { detail: 'Este link expirou ou já foi usado.', codigo: 'LINK_INVALIDO' },
      { status: 422, statusText: 'Entidade não processável' },
    );
    atualizar();
    expect(elemento.querySelector('h1')?.textContent).toBe('Este link expirou ou já foi usado');
    TestBed.resetTestingModule();
    const semToken = criar(null);
    httpTesting.expectNone('/api/auth/confirmar-email');
    expect(semToken.elemento.querySelector('h1')?.textContent).toBe('Este link expirou ou já foi usado');
  });
});
