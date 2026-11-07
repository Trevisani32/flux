import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { AuthService } from '../../core/auth/auth.service';
import { Usuario } from '../../core/auth/sessao';
import { AGORA } from '../../core/tempo/agora';
import { Inicio } from './inicio';

describe('Inicio', () => {
  it('mostra a saudação, a data e os itens em breve', () => {
    const auth = { usuario: signal<Usuario | null>({ id: 1, nome: 'Dev', email: 'dev@flux.local' }) };
    TestBed.configureTestingModule({
      imports: [Inicio],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: AGORA, useValue: () => new Date('2026-11-07T02:30:00Z') },
      ],
    });
    const fixture = TestBed.createComponent(Inicio);
    fixture.detectChanges();
    const elemento = fixture.nativeElement as HTMLElement;
    expect(elemento.querySelector('h1')?.textContent).toBe('Olá, Dev');
    expect(elemento.querySelector('p.cabecalho__subtitulo')?.textContent).toBe('Sexta-feira, 6 de novembro de 2026');
    expect(elemento.querySelector('h2')?.textContent).toBe('Tudo pronto para começar');
    expect([...elemento.querySelectorAll('h3')].map((titulo) => titulo.textContent)).toEqual([
      'Contas e lançamentos',
      'Importação de extratos',
      'Cartões de crédito',
    ]);
    expect([...elemento.querySelectorAll('.flux-pilula')].map((pilula) => pilula.textContent)).toEqual(['Em breve', 'Em breve', 'Em breve']);
  });
});
