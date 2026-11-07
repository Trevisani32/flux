import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { EsqueciASenha } from './esqueci-a-senha';

describe('EsqueciASenha', () => {
  let http: HttpTestingController;

  afterEach(() => http.verify());

  function criar(): { componente: EsqueciASenha; elemento: HTMLElement; navegar: ReturnType<typeof vi.spyOn>; atualizar: () => void } {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(EsqueciASenha); fixture.detectChanges();
    return { componente: fixture.componentInstance, elemento: fixture.nativeElement as HTMLElement, navegar, atualizar: () => fixture.detectChanges() };
  }

  it('mostra erros de email vazio e inválido', () => {
    const { componente, elemento, atualizar } = criar();
    componente.enviar(); atualizar(); expect(elemento.querySelector('#esqueci-email-erro')?.textContent).toBe('Informe o e-mail.');
    TestBed.resetTestingModule();
    const invalido = criar();
    invalido.componente.formulario.setValue({ email: 'invalido' }); invalido.componente.enviar(); invalido.atualizar();
    expect(invalido.elemento.querySelector('#esqueci-email-erro')?.textContent).toBe('Informe um e-mail válido.');
    http.expectNone('/api/auth/esqueci-a-senha');
  });

  it('pede o link e navega com o email normalizado', () => {
    const { componente, navegar } = criar();
    componente.formulario.setValue({ email: 'ANA@TESTE.LOCAL' }); componente.enviar();
    const req = http.expectOne('/api/auth/esqueci-a-senha'); expect(req.request.body).toEqual({ email: 'ANA@TESTE.LOCAL' }); req.flush(null);
    expect(navegar).toHaveBeenCalledWith(['/confira-seu-email'], { queryParams: { tipo: 'senha' }, state: { email: 'ana@teste.local' } });
  });

  it('mostra alerta ao atingir o limite', () => {
    const { componente, elemento, atualizar } = criar();
    componente.formulario.setValue({ email: 'ana@teste.local' }); componente.enviar();
    http.expectOne('/api/auth/esqueci-a-senha').flush(null, { status: 429, statusText: 'Limite' }); atualizar();
    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('Muitas tentativas.');
  });
});
