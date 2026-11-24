import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { GraficoLinha } from './grafico-linha';
import { PontoDoGrafico } from './mes-do-grafico';

const ITENS: readonly PontoDoGrafico[] = [
  {
    mes: '2026-10',
    saldo: -20,
    rotulo: 'out',
    ano: '2026',
    titulo: 'Fim de outubro',
    leitura: 'Outubro',
  },
  {
    mes: '2026-11',
    saldo: 80,
    rotulo: 'nov',
    ano: null,
    titulo: 'Hoje, 24/11/2026',
    leitura: 'Hoje',
  },
];

@Component({
  imports: [GraficoLinha],
  template: `
    <app-grafico-linha
      [itens]="itens()"
      [selecionado]="selecionado()"
      (escolher)="escolhido.set($event)"
    />
  `,
})
class Hospedeiro {
  readonly itens = signal(ITENS);
  readonly selecionado = signal('2026-11');
  readonly escolhido = signal<string | null>(null);
}

describe('GraficoLinha', () => {
  function criar() {
    const fixture = TestBed.createComponent(Hospedeiro);
    fixture.detectChanges();
    return { fixture, elemento: fixture.nativeElement as HTMLElement };
  }

  it('desenha a linha com área a partir do zero, o último valor escrito e o eixo que desce com saldo negativo', () => {
    const { elemento } = criar();
    expect(elemento.querySelector('.grafico__linha')).not.toBeNull();
    expect(elemento.querySelector('.grafico__area')).not.toBeNull();
    expect(elemento.textContent).toContain('R$\u00a080,00');
    expect(elemento.textContent).toContain('−');
  });

  it('o balão diz "Hoje" no mês atual e "Fim de" nos outros; Enter escolhe o mês; um mês só desenha só o ponto', () => {
    const { fixture, elemento } = criar();
    const grupos = elemento.querySelectorAll<SVGGElement>('g[role="button"]');

    grupos[1].dispatchEvent(new FocusEvent('focus'));
    fixture.detectChanges();
    expect(elemento.querySelector('.grafico__balao-titulo')?.textContent).toContain('Hoje');
    grupos[0].dispatchEvent(new FocusEvent('focus'));
    fixture.detectChanges();
    expect(elemento.querySelector('.grafico__balao-titulo')?.textContent).toContain('Fim de');
    grupos[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    expect(fixture.componentInstance.escolhido()).toBe('2026-10');

    fixture.componentInstance.itens.set([ITENS[1]]);
    fixture.detectChanges();
    expect(elemento.querySelector('.grafico__linha')).toBeNull();
    expect(elemento.querySelector('.grafico__area')).toBeNull();
    expect(elemento.querySelectorAll('.grafico__ponto')).toHaveLength(1);
  });
});
