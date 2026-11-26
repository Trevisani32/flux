import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ComprasService } from '../../cartoes/compras.service';
import { Ocorrencia, RecorrenciasService } from '../recorrencias.service';
import { ConfirmarDialogo } from './confirmar-dialogo';

const conta: Ocorrencia = {
  recorrenciaId: 7,
  periodo: '2026-11-01',
  data: '2026-11-30',
  dataNaLista: '2026-11-30',
  descricao: 'Aluguel Fictício',
  tipo: 'DESPESA',
  valor: -1850,
  valorPrevisto: 1850,
  valorVaria: false,
  contaId: 1,
  contaNome: 'Banco Horizonte',
  cartaoId: null,
  cartaoNome: null,
  cartaoCor: null,
  categoriaId: 12,
  categoriaNome: 'Aluguel',
  categoriaMaeNome: 'Moradia',
  atrasada: false,
  visivelAte: null,
};

describe('ConfirmarDialogo', () => {
  const servico = { candidatos: vi.fn(), confirmar: vi.fn(), ligar: vi.fn(), pular: vi.fn() };
  const compras = { previa: vi.fn() };
  const referencia = { close: vi.fn() };

  beforeEach(() => {
    servico.candidatos.mockReset();
    servico.confirmar.mockReset();
    servico.ligar.mockReset();
    servico.pular.mockReset();
    compras.previa.mockReset();
    referencia.close.mockReset();
    servico.candidatos.mockReturnValue(of({ de: '2026-11-20', ate: '2026-12-10', sugerido: null, candidatos: [] }));
    servico.confirmar.mockReturnValue(of({
      recorrenciaId: 7,
      periodo: '2026-11-01',
      lancamentoId: 31,
      compraId: null,
      data: '2026-11-30',
      faturaMes: null,
      cartaoNome: null,
    }));
    servico.ligar.mockReturnValue(of({
      recorrenciaId: 7,
      periodo: '2026-11-01',
      lancamentoId: 31,
      compraId: null,
      data: '2026-11-30',
      faturaMes: null,
      cartaoNome: null,
    }));
    servico.pular.mockReturnValue(of(conta));
  });

  function criar(ocorrencia: Ocorrencia = conta, jaLancei = false) {
    TestBed.configureTestingModule({
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { ocorrencia, hoje: '2026-11-25', jaLancei } },
        { provide: MatDialogRef, useValue: referencia },
        { provide: RecorrenciasService, useValue: servico },
        { provide: ComprasService, useValue: compras },
      ],
    });
    const fixture = TestBed.createComponent(ConfirmarDialogo);
    fixture.detectChanges();
    return fixture;
  }

  function texto(fixture: ReturnType<typeof criar>): string {
    return fixture.nativeElement.textContent.replace(/\s+/g, ' ').trim();
  }

  it('na conta vem a data e o valor previstos', () => {
    const fixture = criar();
    const elemento = fixture.nativeElement as HTMLElement;
    expect(elemento.querySelector<HTMLInputElement>('#confirmar-data')?.value).toBe('2026-11-30');
    expect(elemento.querySelector<HTMLInputElement>('#confirmar-valor')?.value).toBe('1.850,00');
    expect(texto(fixture)).toContain('Prevista para segunda, 30/11/2026 · Banco Horizonte');
    expect(texto(fixture)).not.toContain('Atrasada');
    const ficha = Array.from(elemento.querySelectorAll('dl dt, dl dd')).map((item) => item.textContent?.trim());
    expect(ficha).toEqual(['Descrição', 'Aluguel Fictício', 'Categoria', 'Moradia › Aluguel']);
  });

  it('na que varia o valor vem vazio com a dica da estimativa', () => {
    const fixture = criar({ ...conta, descricao: 'Conta de luz Fictícia', valor: -189.9, valorPrevisto: 189.9, valorVaria: true });
    const campo = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('#confirmar-valor');
    expect(campo?.value).toBe('');
    expect(campo?.placeholder).toBe('0,00');
    const dica = (fixture.nativeElement as HTMLElement).querySelector('.confirmar-dialogo__dica') as HTMLElement;
    expect(dica.childNodes[0].textContent).toBe('O valor varia: digite o da conta (estimativa ');
    expect(dica.querySelector('.valor__texto')?.textContent.replace(/\u00a0/g, ' ')).toBe('−R$ 189,90');
    expect(dica.childNodes[dica.childNodes.length - 1].textContent).toBe(').');
  });

  it('com sugestão mostra Parece que já está no Flux e Confirmar mesmo assim, e Ligar a este liga', () => {
    const sugerido = {
      id: 81,
      alvo: 'LANCAMENTO' as const,
      data: '2026-11-29',
      descricao: 'Aluguel apartamento',
      valor: -1850,
      origem: 'MANUAL' as const,
      categoriaNome: 'Aluguel',
      categoriaMaeNome: 'Moradia',
    };
    servico.candidatos.mockReturnValue(of({ de: '2026-11-20', ate: '2026-12-10', sugerido, candidatos: [sugerido] }));
    const fixture = criar();
    expect(texto(fixture)).toContain('Parece que já está no Flux');
    const alerta = (fixture.nativeElement as HTMLElement).querySelector('app-alerta') as HTMLElement;
    expect(alerta.querySelector('.flux-alerta__texto')?.childNodes[0].textContent?.trimStart())
      .toBe('Aluguel apartamento · 29/11 · ');
    expect(alerta.querySelector('.valor__texto')?.textContent.replace(/\u00a0/g, ' ')).toBe('−R$ 1.850,00');
    expect(alerta.querySelector('.flux-alerta__texto > span')?.textContent).toBe(' · lançado à mão');
    expect(texto(fixture)).toContain('Confirmar mesmo assim');
    fixture.componentInstance.ligar(sugerido);
    expect(servico.ligar).toHaveBeenCalledWith(7, '2026-11-01', { lancamentoId: 81, compraId: null });
    expect(referencia.close).toHaveBeenCalledWith(expect.objectContaining({ lancamentoId: 31, ligada: true }));
  });

  it('Já lancei lista os candidatos e liga o escolhido', () => {
    const candidatos = [
      {
        id: 81,
        alvo: 'LANCAMENTO' as const,
        data: '2026-11-29',
        descricao: 'Aluguel apartamento',
        valor: -1850,
        origem: 'MANUAL' as const,
        categoriaNome: 'Aluguel',
        categoriaMaeNome: 'Moradia',
      },
      {
        id: 82,
        alvo: 'LANCAMENTO' as const,
        data: '2026-11-30',
        descricao: 'Aluguel manual',
        valor: -1840,
        origem: 'IMPORTADO' as const,
        categoriaNome: null,
        categoriaMaeNome: null,
      },
    ];
    servico.candidatos.mockReturnValue(of({ de: '2026-11-20', ate: '2026-12-10', sugerido: null, candidatos }));
    const fixture = criar();
    fixture.componentInstance.abrirJaLancei();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('h2')?.textContent).toBe('Já lancei: Aluguel Fictício');
    expect((fixture.nativeElement as HTMLElement).querySelector('div[mat-dialog-content] > p')?.textContent?.replace(/\s+/g, ' ').trim())
      .toBe('Lançamentos de Banco Horizonte de 20/11 a 10/12, sem outra ligação.');
    expect((fixture.nativeElement as HTMLElement).querySelector('fieldset label')?.textContent?.replace(/\s+/g, ' ').trim())
      .toContain('Aluguel apartamento 29/11 · à mão · Moradia › Aluguel −R$ 1.850,00');
    fixture.componentInstance.selecionar(candidatos[1]);
    fixture.componentInstance.ligar();
    expect(servico.ligar).toHaveBeenCalledWith(7, '2026-11-01', { lancamentoId: 82, compraId: null });
  });

  it('no cartão mostra a fatura e o disponível da prévia da compra', () => {
    const cartao: Ocorrencia = {
      ...conta,
      contaId: null,
      contaNome: null,
      cartaoId: 4,
      cartaoNome: 'Cartão Lua',
      cartaoCor: 'ROXO',
      data: '2026-12-25',
    };
    compras.previa.mockReturnValue(of({
      primeiraFatura: { mes: '2027-01', fechamento: '2026-12-25', vencimento: '2027-01-05' },
      disponivelDepois: 2246.9,
      avisoDaFaturaPaga: null,
    }));
    const fixture = criar(cartao);
    expect(compras.previa).toHaveBeenCalledWith({ cartaoId: 4, data: '2026-12-25', valorTotal: 1850, parcelas: 1, parcelaInicial: 1 });
    expect(texto(fixture)).toContain('Compra à vista na fatura de janeiro');
    const previa = (fixture.nativeElement as HTMLElement).querySelector('.confirmar-dialogo__previa') as HTMLElement;
    expect(previa.querySelectorAll('p')[0]?.textContent?.trim()).toBe('Fecha 25/12 e vence 05/01/2027.');
    expect(previa.querySelectorAll('p')[1]?.firstChild?.textContent).toBe('Disponível depois desta compra: ');
    expect(previa.querySelector('.valor__texto')?.textContent.replace(/\u00a0/g, ' ')).toBe('R$ 2.246,90');
    expect(previa.querySelectorAll('p')[1]?.lastChild?.textContent).toBe('.');
  });

  it('o 422 aparece no alerta e o 400 da data no campo', () => {
    const fixture = criar();
    servico.confirmar.mockReturnValueOnce(throwError(() => new HttpErrorResponse({
      status: 422,
      error: { detail: 'A recorrência está pausada.' },
    })));
    fixture.componentInstance.confirmar();
    fixture.detectChanges();
    const alerta = (fixture.nativeElement as HTMLElement).querySelector('app-alerta') as HTMLElement;
    expect(alerta.querySelector('.flux-alerta__titulo')?.textContent).toBe('Não foi possível confirmar.');
    expect(alerta.querySelector('.flux-alerta__texto')?.textContent).toBe('A recorrência está pausada.');
    servico.confirmar.mockReturnValueOnce(throwError(() => new HttpErrorResponse({
      status: 400,
      error: { campos: [{ campo: 'data', mensagem: 'A data precisa estar dentro da faixa.' }] },
    })));
    fixture.componentInstance.confirmar();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('#confirmar-data')?.getAttribute('aria-invalid')).toBe('true');
    expect(texto(fixture)).toContain('A data precisa estar dentro da faixa.');
  });
});
