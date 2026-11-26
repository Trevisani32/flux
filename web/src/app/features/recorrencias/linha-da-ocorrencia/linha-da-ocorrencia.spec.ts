import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { FluxoDaOcorrencia } from '../fluxo-da-ocorrencia';
import { Ocorrencia } from '../recorrencias.service';
import { LinhaDaOcorrencia } from './linha-da-ocorrencia';

const ocorrencia: Ocorrencia = {
  recorrenciaId: 7,
  periodo: '2026-11-01',
  data: '2026-11-27',
  dataNaLista: '2026-11-27',
  descricao: 'Conta de luz Fictícia',
  tipo: 'DESPESA',
  valor: -189.9,
  valorPrevisto: 189.9,
  valorVaria: true,
  contaId: 1,
  contaNome: 'Banco Horizonte',
  cartaoId: null,
  cartaoNome: null,
  cartaoCor: null,
  categoriaId: 12,
  categoriaNome: 'Energia',
  categoriaMaeNome: 'Casa',
  atrasada: false,
  visivelAte: null,
};

describe('LinhaDaOcorrencia', () => {
  const fluxo = { confirmar: vi.fn(), pular: vi.fn(), acoes: vi.fn() };

  beforeEach(() => {
    fluxo.confirmar.mockReset();
    fluxo.pular.mockReset();
    fluxo.acoes.mockReset();
    TestBed.configureTestingModule({ providers: [{ provide: FluxoDaOcorrencia, useValue: fluxo }] });
  });

  function criar(entradas: Partial<{ modo: 'confirmar' | 'lista'; contaFiltrada: boolean; ocorrencia: Ocorrencia }> = {}) {
    const fixture = TestBed.createComponent(LinhaDaOcorrencia);
    fixture.componentRef.setInput('ocorrencia', entradas.ocorrencia ?? ocorrencia);
    fixture.componentRef.setInput('hoje', '2026-11-25');
    fixture.componentRef.setInput('modo', entradas.modo ?? 'confirmar');
    fixture.componentRef.setInput('contaFiltrada', entradas.contaFiltrada ?? false);
    fixture.detectChanges();
    return fixture;
  }

  it('no modo confirmar mostra quando, o lugar e ≈ na que varia', () => {
    const fixture = criar({ ocorrencia: { ...ocorrencia, data: '2026-11-26' } });
    const elemento = fixture.nativeElement as HTMLElement;
    expect(elemento.querySelector('.flux-ocorrencia__titulo')?.textContent).toBe('Conta de luz Fictícia');
    const meta = elemento.querySelector('.flux-ocorrencia__meta')?.textContent?.replace(/\s+/g, ' ').trim();
    expect(meta).toBe('amanhã, 26/11 · Banco Horizonte');
    expect(elemento.querySelector('.flux-ocorrencia__valor')?.textContent).toContain('≈');
    expect(elemento.querySelector('.valor__texto')?.textContent.replace(/\u00a0/g, ' ')).toBe('−R$ 189,90');
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('[aria-label^="Confirmar"]')?.click();
    expect(fluxo.confirmar).toHaveBeenCalledWith({ ...ocorrencia, data: '2026-11-26' }, '2026-11-25');
  });

  it('no modo lista mostra a pílula, a categoria e, no cartão, compra prevista em', () => {
    const noCartao: Ocorrencia = {
      ...ocorrencia,
      descricao: 'Netflix Fictícia',
      valor: -39.9,
      valorVaria: false,
      contaId: null,
      contaNome: null,
      cartaoId: 4,
      cartaoNome: 'Cartão Lua',
      cartaoCor: 'ROXO',
      data: '2026-12-25',
      dataNaLista: '2027-01-05',
    };
    const fixture = criar({ modo: 'lista', ocorrencia: noCartao });
    const elemento = fixture.nativeElement as HTMLElement;
    expect(elemento.querySelector('.flux-ocorrencia__titulo')?.textContent).toBe('Netflix Fictícia');
    expect(elemento.querySelector('.flux-pilula')?.textContent?.trim()).toBe('Previsto');
    expect(elemento.querySelector('.flux-ocorrencia__meta')?.textContent?.replace(/\s+/g, ' ').trim())
      .toBe('Previsto Casa › Energia · Cartão Lua · compra prevista em 25/12');
    expect(elemento.querySelector('.valor__texto')?.textContent.replace(/\u00a0/g, ' ')).toBe('−R$ 39,90');
  });

  it('com a conta filtrada diz fora do saldo', () => {
    const fixture = criar({ modo: 'lista', contaFiltrada: true });
    expect((fixture.nativeElement as HTMLElement).querySelector('.flux-ocorrencia__meta')?.textContent?.replace(/\s+/g, ' ').trim())
      .toBe('Previsto Casa › Energia · fora do saldo');
  });
});
