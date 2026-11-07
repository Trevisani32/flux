import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';

import { EstadoDaNavegacao } from '../../../core/navegacao/estado-da-navegacao';
import { ConfiraSeuEmail } from './confira-seu-email';

describe('ConfiraSeuEmail', () => {
  let http: HttpTestingController;

  afterEach(() => http.verify());

  function criar(email: string | null, tipo: string | null = null): { componente: ConfiraSeuEmail; elemento: HTMLElement; atualizar: () => void } {
    TestBed.configureTestingModule({
      imports: [ConfiraSeuEmail],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: EstadoDaNavegacao, useValue: { texto: (chave: string) => chave === 'email' ? email : null } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(tipo === null ? {} : { tipo }) } } }],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ConfiraSeuEmail); fixture.detectChanges();
    return { componente: fixture.componentInstance, elemento: fixture.nativeElement as HTMLElement, atualizar: () => fixture.detectChanges() };
  }

  it('mostra cadastro com email e as três dicas', () => {
    const { elemento } = criar('ana@teste.local');
    expect(elemento.querySelector('.flux-status__texto b')?.textContent).toBe('ana@teste.local');
    expect(elemento.querySelectorAll('.flux-lista-dicas li')).toHaveLength(3);
  });

  it('mostra texto de senha sem dicas', () => {
    const { elemento } = criar('ana@teste.local', 'senha');
    expect(elemento.textContent).toContain('criar uma senha nova');
    expect(elemento.querySelector('.flux-lista-dicas')).toBeNull();
  });

  it('esconde reenvio sem email no estado', () => {
    const { elemento } = criar(null);
    expect(elemento.textContent).toContain('o e-mail informado');
    expect(elemento.querySelector('button')).toBeNull();
  });

  it('reenvia confirmação de cadastro', () => {
    const { componente, elemento, atualizar } = criar('ana@teste.local');
    componente.reenviar(); const req = http.expectOne('/api/auth/reenviar-confirmacao'); expect(req.request.body).toEqual({ email: 'ana@teste.local' }); req.flush(null); atualizar();
    expect(elemento.querySelector('[role="status"]')?.textContent).toContain('Link reenviado.');
  });

  it('pede novo link de senha', () => {
    const { componente } = criar('ana@teste.local', 'senha');
    componente.reenviar(); const req = http.expectOne('/api/auth/esqueci-a-senha'); expect(req.request.body).toEqual({ email: 'ana@teste.local' }); req.flush(null);
  });

  it('mostra alerta no limite de reenvio', () => {
    const { componente, elemento, atualizar } = criar('ana@teste.local');
    componente.reenviar(); http.expectOne('/api/auth/reenviar-confirmacao').flush(null, { status: 429, statusText: 'Limite' }); atualizar();
    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('Muitas tentativas.');
  });
});
