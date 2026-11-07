import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthService } from '../../../core/auth/auth.service';
import { RedefinirSenha } from './redefinir-senha';

describe('RedefinirSenha', () => {
  let http: HttpTestingController;

  afterEach(() => http.verify());

  function criar(fragment: string | null): { componente: RedefinirSenha; elemento: HTMLElement; navegar: ReturnType<typeof vi.spyOn>; limpar: ReturnType<typeof vi.spyOn>; trocarUrl: ReturnType<typeof vi.spyOn>; atualizar: () => void } {
    const auth = { limparSessao: vi.fn() };
    TestBed.configureTestingModule({
      imports: [RedefinirSenha],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { fragment } } }, { provide: AuthService, useValue: auth }],
    });
    http = TestBed.inject(HttpTestingController);
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const trocarUrl = vi.spyOn(TestBed.inject(Location), 'replaceState');
    const fixture = TestBed.createComponent(RedefinirSenha); fixture.detectChanges();
    return { componente: fixture.componentInstance, elemento: fixture.nativeElement as HTMLElement, navegar, limpar: auth.limparSessao, trocarUrl, atualizar: () => fixture.detectChanges() };
  }

  it('mostra link expirado e não chama API sem token', () => {
    const { componente, elemento } = criar(null); componente.salvar();
    expect(elemento.querySelector('h1')?.textContent).toBe('Este link expirou ou já foi usado'); http.expectNone('/api/auth/redefinir-senha');
  });

  it('remove token da URL e bloqueia senhas diferentes', () => {
    const { componente, elemento, trocarUrl, atualizar } = criar('token=abc');
    expect(trocarUrl).toHaveBeenCalledWith('/redefinir-senha');
    componente.formulario.setValue({ novaSenha: 'senha-nova-123', confirmarNovaSenha: 'outra-senha-123' }); componente.salvar(); atualizar();
    expect(elemento.querySelector('#redefinir-senha-confirmacao-erro')?.textContent).toBe('As senhas não conferem.'); http.expectNone('/api/auth/redefinir-senha');
  });

  it('redefine senha, limpa sessão e navega', () => {
    const { componente, navegar, limpar } = criar('token=abc');
    componente.formulario.setValue({ novaSenha: 'senha-nova-123', confirmarNovaSenha: 'senha-nova-123' }); componente.salvar();
    const req = http.expectOne('/api/auth/redefinir-senha'); expect(req.request.body).toEqual({ token: 'abc', novaSenha: 'senha-nova-123' }); req.flush(null, { status: 204, statusText: 'Sem conteúdo' });
    expect(limpar).toHaveBeenCalled(); expect(navegar).toHaveBeenCalledWith(['/entrar'], { state: { aviso: 'senha-redefinida' } });
  });

  it('mostra link expirado para LINK_INVALIDO', () => {
    const { componente, elemento, atualizar } = criar('token=abc');
    componente.formulario.setValue({ novaSenha: 'senha-nova-123', confirmarNovaSenha: 'senha-nova-123' }); componente.salvar();
    http.expectOne('/api/auth/redefinir-senha').flush({ codigo: 'LINK_INVALIDO' }, { status: 422, statusText: 'Regra' }); atualizar();
    expect(elemento.querySelector('h1')?.textContent).toBe('Este link expirou ou já foi usado');
  });

  it('mostra detalhe de 422 diferente', () => {
    const { componente, elemento, atualizar } = criar('token=abc');
    componente.formulario.setValue({ novaSenha: 'senha-nova-123', confirmarNovaSenha: 'senha-nova-123' }); componente.salvar();
    http.expectOne('/api/auth/redefinir-senha').flush({ detail: 'Senha recusada.' }, { status: 422, statusText: 'Regra' }); atualizar();
    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('Senha recusada.');
  });

  it('mostra limite excedido', () => {
    const { componente, elemento, atualizar } = criar('token=abc');
    componente.formulario.setValue({ novaSenha: 'senha-nova-123', confirmarNovaSenha: 'senha-nova-123' }); componente.salvar();
    http.expectOne('/api/auth/redefinir-senha').flush(null, { status: 429, statusText: 'Limite' }); atualizar();
    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('Muitas tentativas.');
  });
});
