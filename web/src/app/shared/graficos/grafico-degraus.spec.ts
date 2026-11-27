import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { GraficoDegraus } from './grafico-degraus';
import { DiaDoDegrau } from './mes-do-grafico';

const DIAS: readonly DiaDoDegrau[] = [
  { data: '2026-11-25', saldo: 100, rotuloDoEixo: 'hoje', leitura: 'dia 1', titulo: 'Dia 1', linhas: [], dica: null },
  {
    data: '2026-11-26', saldo: 100, rotuloDoEixo: '26/11', leitura: 'dia 2', titulo: 'Dia 2',
    linhas: [{ texto: 'Internet', valor: '−R$\u00a0119,90' }], dica: 'Nenhum movimento neste dia',
  },
  { data: '2026-11-27', saldo: -50, rotuloDoEixo: '27/11', leitura: 'dia 3', titulo: 'Dia 3', linhas: [], dica: null },
  { data: '2026-11-28', saldo: 25, rotuloDoEixo: 'fim', leitura: 'dia 4', titulo: 'Dia 4', linhas: [], dica: null },
];

describe('GraficoDegraus', () => {
  function criar(dias = DIAS, menor: { data: string; rotulo: string } | null = { data: '2026-11-27', rotulo: '−R$\u00a050,00 · 27/11' }) {
    const fixture = TestBed.createComponent(GraficoDegraus);
    fixture.componentRef.setInput('dias', dias);
    fixture.componentRef.setInput('menor', menor);
    fixture.componentRef.setInput('rotulo', 'Saldo previsto dia a dia, de 25/11 a 28/11');
    fixture.componentRef.setInput('altura', 270);
    fixture.componentInstance.largura.set(600);
    fixture.detectChanges();
    return { fixture, elemento: fixture.nativeElement as HTMLElement };
  }

  it('desenha os degraus, a linha do zero e as duas áreas com clipPath próprio', () => {
    const primeiro = criar();
    const linha = primeiro.elemento.querySelector<SVGPathElement>('.grafico-degraus__linha');
    const area = primeiro.elemento.querySelector<SVGPathElement>('.grafico-degraus__area');
    expect(linha?.getAttribute('d')).toBe('M 46 22 H 316 V 240 H 451 V 131 H 586');
    expect(area?.getAttribute('d')).toBe('M 46 22 H 316 V 240 H 451 V 131 H 586 V 167.33333333333334 H 46 Z');
    expect(primeiro.elemento.querySelector('.grafico-degraus__zero')).not.toBeNull();
    const segundo = criar();
    const idsDoPrimeiro = Array.from(primeiro.elemento.querySelectorAll('clipPath')).map((item) => item.id);
    const idsDoSegundo = Array.from(segundo.elemento.querySelectorAll('clipPath')).map((item) => item.id);
    const identificadorDoPrimeiro = /^flux-degraus-(\d+)-acima$/.exec(idsDoPrimeiro[0])?.[1];
    const identificadorDoSegundo = /^flux-degraus-(\d+)-acima$/.exec(idsDoSegundo[0])?.[1];
    expect(idsDoPrimeiro).toEqual([
      `flux-degraus-${identificadorDoPrimeiro}-acima`,
      `flux-degraus-${identificadorDoPrimeiro}-abaixo`,
    ]);
    expect(idsDoSegundo).toEqual([
      `flux-degraus-${identificadorDoSegundo}-acima`,
      `flux-degraus-${identificadorDoSegundo}-abaixo`,
    ]);
    expect(identificadorDoSegundo).not.toBe(identificadorDoPrimeiro);
  });

  it('marca o menor só quando vem', () => {
    const { elemento } = criar();
    const circulo = elemento.querySelector<SVGCircleElement>('.grafico-degraus__menor');
    expect([circulo?.getAttribute('cx'), circulo?.getAttribute('cy'), elemento.querySelector('.grafico-degraus__texto-menor')?.textContent])
      .toEqual(['383.5', '240', '−R$\u00a050,00 · 27/11']);
    const semMenor = criar(DIAS, null);
    expect(semMenor.elemento.querySelector('.grafico-degraus__menor')).toBeNull();
    expect(semMenor.elemento.querySelector('.grafico-degraus__texto-menor')).toBeNull();
  });

  it('um ponto de tabulação só, com as setas, e o balão', () => {
    const { fixture, elemento } = criar();
    const alvos = Array.from(elemento.querySelectorAll<SVGRectElement>('.grafico-degraus__dia'));
    expect(alvos.map((alvo) => alvo.getAttribute('tabindex'))).toEqual(['0', '-1', '-1', '-1']);
    alvos[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    fixture.detectChanges();
    expect(alvos.map((alvo) => alvo.getAttribute('tabindex'))).toEqual(['-1', '0', '-1', '-1']);
    expect(document.activeElement).toBe(alvos[1]);
    expect(Array.from(elemento.querySelectorAll('.grafico-degraus__balao > *')).map((item) => item.textContent))
      .toEqual(['Dia 2', 'Saldo no fim do dia R$\u00a0100,00', 'Internet −R$\u00a0119,90', 'Nenhum movimento neste dia']);
    alvos[1].dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true }));
    fixture.detectChanges();
    expect(alvos.map((alvo) => alvo.getAttribute('tabindex'))).toEqual(['-1', '-1', '-1', '0']);
    expect(document.activeElement).toBe(alvos[3]);
    alvos[3].dispatchEvent(new FocusEvent('blur'));
    fixture.detectChanges();
    expect(elemento.querySelector('.grafico-degraus__balao')).toBeNull();
  });

  it('os rótulos do eixo que ficariam apertados somem', () => {
    const dias = ['hoje', 'a', 'b', 'c', 'fim'].map((rotuloDoEixo, indice) => ({
      ...DIAS[Math.min(indice, DIAS.length - 1)], data: `2026-12-0${indice + 1}`, rotuloDoEixo,
    }));
    const { fixture, elemento } = criar(dias);
    fixture.componentInstance.largura.set(160);
    fixture.detectChanges();
    expect(Array.from(elemento.querySelectorAll('.grafico-degraus__rotulo')).map((item) => [item.textContent, item.getAttribute('x')]))
      .toEqual([['hoje', '56'], ['fim', '136']]);
    fixture.componentInstance.largura.set(600);
    fixture.detectChanges();
    expect(Array.from(elemento.querySelectorAll('.grafico-degraus__rotulo')).map((item) => [item.textContent, item.getAttribute('x')]))
      .toEqual([['hoje', '100'], ['a', '208'], ['b', '316'], ['c', '424'], ['fim', '532']]);
  });
});
