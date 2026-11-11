import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';

import { ICONES } from '../../../shared/icone/icones';
import { Navegacao } from './navegacao';

describe('Navegacao', () => {
  it('tem os grupos Visão geral (Início, Lançamentos, Importação, Contas) e Organização (Categorias e Regras)', () => {
    TestBed.configureTestingModule({ imports: [Navegacao], providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(Navegacao);
    fixture.detectChanges();
    const elemento = fixture.nativeElement as HTMLElement;

    const grupos = Array.from(elemento.querySelectorAll('.navegacao__grupo')).map((grupo) => grupo.textContent);
    expect(grupos).toEqual(['Visão geral', 'Organização']);
    const listas = Array.from(elemento.querySelectorAll('ul.navegacao__lista'));
    expect(listas.map((lista) => lista.getAttribute('aria-labelledby'))).toEqual(['navegacao-visao-geral', 'navegacao-organizacao']);
    const links = Array.from(elemento.querySelectorAll<HTMLAnchorElement>('a.navegacao__link'));
    expect(links.map((link) => [link.textContent?.trim(), link.getAttribute('href')])).toEqual([
      ['Início', '/'],
      ['Lançamentos', '/lancamentos'],
      ['Importação', '/importacao'],
      ['Contas', '/contas'],
      ['Categorias', '/categorias'],
      ['Regras', '/regras'],
    ]);
    const icones = links.map((link) => Array.from(link.querySelectorAll('path')).map((caminho) => caminho.getAttribute('d')));
    expect(icones).toEqual([[...ICONES.inicio], [...ICONES.lancamentos], [...ICONES.importar], [...ICONES.contas], [...ICONES.etiqueta],
      [...ICONES.regra]]);
  });
});
