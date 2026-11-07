import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';

import { AuthService } from '../../../core/auth/auth.service';
import { ConfiguracaoService } from '../../../core/configuracao/configuracao.service';
import { TermosDeUso } from './termos-de-uso';

const configuracao = { cadastroPublico: true, controlador: { nome: 'Responsável fictício', email: 'privacidade@teste.local' }, versaoDosTermos: '2026-11-06' };

describe('TermosDeUso', () => {
  function criar(logado = false, contato = 'privacidade@teste.local'): HTMLElement {
    TestBed.configureTestingModule({
      imports: [TermosDeUso],
      providers: [
        provideRouter([]),
        { provide: ConfiguracaoService, useValue: { configuracao: signal({ ...configuracao, controlador: { ...configuracao.controlador, email: contato } }) } },
        { provide: AuthService, useValue: { autenticado: signal(logado) } },
      ],
    });
    const fixture = TestBed.createComponent(TermosDeUso);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('mostra título, versão, leitura e as dez seções', () => {
    const elemento = criar();
    expect(elemento.querySelector('h1')?.textContent).toBe('Termos de uso');
    expect(elemento.textContent).toContain('Versão de 6 de novembro de 2026 · cerca de 4 minutos de leitura');
    expect(Array.from(elemento.querySelectorAll('h2')).map((h2) => h2.id)).toEqual(Array.from({ length: 10 }, (_, indice) => `t${indice + 1}`));
  });

  it('mostra controlador, contato e entrar para visitante', () => {
    const elemento = criar();
    expect(elemento.textContent).toContain('Esta instância é mantida por Responsável fictício');
    expect(elemento.querySelector('a[href="mailto:privacidade@teste.local"]')?.textContent).toBe('privacidade@teste.local');
    expect(Array.from(elemento.querySelectorAll('a[href="/entrar"]')).at(-1)?.textContent).toBe('Entrar');
  });

  it('mostra traço sem contato e volta ao Flux para usuário logado', () => {
    const elemento = criar(true, '');
    expect(elemento.textContent).toContain('—');
    expect(elemento.textContent).toContain('Voltar ao Flux');
    expect(elemento.textContent).toContain('Versão 2026-11-06');
  });
});
