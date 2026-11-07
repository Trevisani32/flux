import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';

import { ConfiguracaoService } from '../../../core/configuracao/configuracao.service';
import { Privacidade } from './privacidade';

describe('Privacidade', () => {
  function criar(): HTMLElement {
    TestBed.configureTestingModule({
      imports: [Privacidade],
      providers: [provideRouter([]), { provide: ConfiguracaoService, useValue: { configuracao: signal({ cadastroPublico: true, controlador: { nome: 'Responsável fictício', email: 'privacidade@teste.local' }, versaoDosTermos: '2026-11-06' }) } }],
    });
    const fixture = TestBed.createComponent(Privacidade);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('mostra título, controlador, cookie e as dez seções', () => {
    const elemento = criar();
    expect(elemento.querySelector('h1')?.textContent).toBe('Política de privacidade');
    expect(Array.from(elemento.querySelectorAll('h2')).map((h2) => h2.id)).toEqual(Array.from({ length: 10 }, (_, indice) => `p${indice + 1}`));
    expect(elemento.textContent).toContain('Responsável fictício');
    expect(Array.from(elemento.querySelectorAll('b')).map((b) => b.textContent)).toContain('flux_renovacao');
  });

  it('aponta de volta para os termos de uso', () => {
    const elemento = criar();
    expect(elemento.querySelector('a[href="/termos-de-uso"]')?.textContent).toBe('← Termos de uso');
  });
});
