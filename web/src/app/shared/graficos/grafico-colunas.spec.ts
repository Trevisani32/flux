import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { GraficoColunas } from './grafico-colunas';
import { ColunaDoGrafico } from './mes-do-grafico';

const ITENS: readonly ColunaDoGrafico[] = [
  {
    mes: '2026-10',
    receitas: 9850,
    despesas: 7920.4,
    resultado: 1929.6,
    rotulo: 'out',
    ano: '2026',
    titulo: 'Outubro de 2026',
    leitura: 'Outubro',
  },
];

@Component({
  imports: [GraficoColunas],
  template: `
    <app-grafico-colunas
      [itens]="itens()"
      [selecionado]="selecionado()"
      (escolher)="escolhido.set($event)"
    />
  `,
})
class Hospedeiro {
  readonly itens = signal(ITENS);
  readonly selecionado = signal('2026-10');
  readonly escolhido = signal<string | null>(null);
}

describe('GraficoColunas', () => {
  function criar() {
    const fixture = TestBed.createComponent(Hospedeiro);
    fixture.detectChanges();
    return { fixture, elemento: fixture.nativeElement as HTMLElement };
  }

  function eventoDoPonteiro(tipo: string, pointerType: string): Event {
    const evento = new Event(tipo, { bubbles: true });
    Object.defineProperty(evento, 'pointerType', { value: pointerType });
    return evento;
  }

  it('desenha uma faixa por mês, com as duas colunas, o eixo em "mil" e o mês escolhido destacado', () => {
    const { elemento } = criar();
    expect(elemento.querySelectorAll('.grafico__faixa')).toHaveLength(1);
    expect(elemento.querySelectorAll('.grafico__coluna')).toHaveLength(2);
    expect(elemento.textContent).toContain('10 mil');
    expect(elemento.querySelector('.grafico__grupo--sel')).not.toBeNull();
  });

  it('o balão aparece no mouse e no foco, com receitas, despesas e resultado; Enter e o clique escolhem o mês', () => {
    const { fixture, elemento } = criar();
    const grupo = elemento.querySelector<SVGGElement>('g[role="button"]')!;

    grupo.dispatchEvent(eventoDoPonteiro('pointerenter', 'mouse'));
    fixture.detectChanges();
    expect(elemento.querySelector('.grafico__balao')?.textContent).toContain('Receitas');
    expect(elemento.querySelector('.grafico__balao')?.textContent).toContain('Despesas');
    expect(elemento.querySelector('.grafico__balao')?.textContent).toContain('Resultado');

    grupo.dispatchEvent(new FocusEvent('focus'));
    fixture.detectChanges();
    expect(elemento.querySelector('.grafico__balao')).not.toBeNull();
    grupo.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    expect(fixture.componentInstance.escolhido()).toBe('2026-10');

    fixture.componentInstance.escolhido.set(null);
    grupo.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(fixture.componentInstance.escolhido()).toBe('2026-10');
  });

  it('no toque, o primeiro toque mostra o balão e o segundo escolhe o mês', () => {
    const { fixture, elemento } = criar();
    const grupo = elemento.querySelector<SVGGElement>('g[role="button"]')!;

    grupo.dispatchEvent(eventoDoPonteiro('pointerdown', 'touch'));
    grupo.dispatchEvent(eventoDoPonteiro('focus', 'touch'));
    grupo.dispatchEvent(eventoDoPonteiro('pointerleave', 'touch'));
    grupo.dispatchEvent(eventoDoPonteiro('click', 'touch'));
    fixture.detectChanges();
    expect(fixture.componentInstance.escolhido()).toBeNull();
    expect(elemento.querySelector('.grafico__dica')?.textContent).toBe(
      'Toque de novo para ver este mês',
    );

    grupo.dispatchEvent(eventoDoPonteiro('pointerdown', 'touch'));
    grupo.dispatchEvent(eventoDoPonteiro('click', 'touch'));
    expect(fixture.componentInstance.escolhido()).toBe('2026-10');
  });
});
