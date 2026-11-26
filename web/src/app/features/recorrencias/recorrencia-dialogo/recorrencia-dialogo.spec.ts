import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NEVER, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ListaDeCartoes } from '../../cartoes/cartoes.service';
import { ListaDeCategorias } from '../../categorias/categorias.service';
import { ListaDeContas } from '../../contas/contas.service';
import { Recorrencia, RecorrenciasService } from '../recorrencias.service';
import { RecorrenciaDialogo } from './recorrencia-dialogo';

const contas: ListaDeContas = {
  ativas: [{
    id: 1, nome: 'Banco Horizonte', tipo: 'CORRENTE', saldoInicial: 0, dataSaldoInicial: '2026-10-01',
    cor: 'OURO', saldoAtual: 0, arquivadaEm: null,
  }],
  arquivadas: [{
    id: 2, nome: 'Banco Antigo', tipo: 'CORRENTE', saldoInicial: 0, dataSaldoInicial: '2026-10-01',
    cor: null, saldoAtual: 0, arquivadaEm: '2026-11-01T12:00:00Z',
  }],
  totais: { geral: 0, disponivel: 0, investimentos: 0 },
};

const cartoes: ListaDeCartoes = {
  ativos: [{
    id: 4, nome: 'Cartão Lua', limite: 3000, diaFechamento: 25, diaVencimento: 5, contaPagamentoId: 1,
    contaPagamentoNome: 'Banco Horizonte', contaPagamentoArquivada: false, cor: 'ROXO', arquivadoEm: null,
    faturaAberta: {
      mes: '2026-11', inicio: '2026-10-26', fechamento: '2026-11-25', vencimento: '2026-12-05',
      total: 0, lancamentos: 0,
    },
    proximaFatura: {
      mes: '2026-12', inicio: '2026-11-26', fechamento: '2026-12-25', vencimento: '2027-01-05',
      total: 0, lancamentos: 0,
    },
    emAberto: 0, lancamentosEmAberto: 0,
  }],
  arquivados: [],
};

const categorias: ListaDeCategorias = {
  despesas: {
    ativas: [{
      id: 10, nome: 'Moradia', natureza: 'DESPESA', categoriaMaeId: null, nomeDaMae: null, arquivadaEm: null,
      subcategorias: [{
        id: 12, nome: 'Aluguel', natureza: 'DESPESA', categoriaMaeId: 10, nomeDaMae: 'Moradia',
        arquivadaEm: null, subcategorias: [],
      }],
    }],
    arquivadas: [],
  },
  receitas: { ativas: [], arquivadas: [] },
};

const recorrencia: Recorrencia = {
  id: 7, descricao: 'Aluguel Fictício', tipo: 'DESPESA', valor: 1850, valorComSinal: -1850, valorVaria: false,
  categoriaId: null, categoriaNome: null, categoriaMaeNome: null, categoriaArquivada: false, contaId: 2,
  contaNome: 'Banco Antigo', cartaoId: null, cartaoNome: null, cartaoCor: null,
  frequencia: 'MENSAL', diaDoMes: 30, comecaEm: '2026-11-25', terminaEm: null,
  ocorrenciasDesde: '2026-11-25', situacao: 'ATIVA', motivoDaPausa: null, proxima: '2026-11-30',
  atrasadas: 0, confirmadas: 0,
};

describe('RecorrenciaDialogo', () => {
  const servico = { previa: vi.fn(), criar: vi.fn(), editar: vi.fn() };
  const referencia = { close: vi.fn() };

  beforeEach(() => {
    servico.previa.mockReset();
    servico.criar.mockReset();
    servico.editar.mockReset();
    referencia.close.mockReset();
    servico.previa.mockReturnValue(NEVER);
    servico.criar.mockReturnValue(of(recorrencia));
    servico.editar.mockReturnValue(of(recorrencia));
  });

  afterEach(() => vi.useRealTimers());

  function criar(edicao: Recorrencia | null = null) {
    TestBed.configureTestingModule({
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { recorrencia: edicao, hoje: '2026-11-25', contas, cartoes, categorias } },
        { provide: MatDialogRef, useValue: referencia },
        { provide: RecorrenciasService, useValue: servico },
      ],
    });
    const fixture = TestBed.createComponent(RecorrenciaDialogo);
    fixture.detectChanges();
    return fixture;
  }

  function texto(fixture: ReturnType<typeof criar>): string {
    return fixture.nativeElement.textContent.replace(/\s+/g, ' ').trim();
  }

  it('no cartão a Receita fica desligada e aparece a dica', () => {
    const fixture = criar();
    fixture.componentInstance.formulario.controls.alvo.setValue('cartao-4');
    fixture.componentInstance.mudouAlvo();
    fixture.detectChanges();
    const receita = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('fieldset button'))
      .find((botao) => botao.textContent?.trim() === 'Receita') as HTMLButtonElement;
    expect(receita.disabled).toBe(true);
    expect(texto(fixture)).toContain('O cartão só recebe despesa.');
  });

  it('o dia do mês só aparece na mensal', () => {
    const fixture = criar();
    expect(texto(fixture)).toContain('Dia do mês');
    fixture.componentInstance.escolherFrequencia('SEMANAL');
    fixture.detectChanges(false);
    expect(texto(fixture)).not.toContain('Dia do mês');
    fixture.componentInstance.escolherFrequencia('ANUAL');
    fixture.detectChanges(false);
    expect(texto(fixture)).not.toContain('Dia do mês');
  });

  it('a prévia vem do servidor depois de 300 ms e marca a ajustada com a nota', () => {
    vi.useFakeTimers();
    servico.previa.mockReturnValue(of({
      desde: '2026-11-25',
      comecaAntes: false,
      ocorrencias: [{ data: '2027-02-28', ajustada: true, faturaMes: null, faturaVencimento: null }],
    }));
    const fixture = criar();
    vi.advanceTimersByTime(300);
    const antes = servico.previa.mock.calls.length;
    fixture.componentInstance.formulario.controls.diaDoMes.setValue(30);
    vi.advanceTimersByTime(299);
    expect(servico.previa).toHaveBeenCalledTimes(antes);
    vi.advanceTimersByTime(1);
    expect(servico.previa).toHaveBeenCalledTimes(antes + 1);
    fixture.detectChanges(false);
    expect(servico.previa).toHaveBeenLastCalledWith({
      frequencia: 'MENSAL', diaDoMes: 30, comecaEm: '2026-11-25', terminaEm: null, cartaoId: null,
      recorrenciaId: null,
    });
    expect(fixture.nativeElement.querySelector('.recorrencia-dialogo__ajustada')?.textContent?.trim()).toBe('dom, 28/02/2027');
    expect(texto(fixture)).toContain('28/02: fevereiro não tem dia 30.');
    vi.useRealTimers();
  });

  it('na edição a frequência fica desligada e a conta arquivada continua como opção', () => {
    const fixture = criar(recorrencia);
    const frequencias = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('fieldset button'))
      .filter((botao) => ['Mensal', 'Semanal', 'Anual'].includes(botao.textContent?.trim() ?? ''));
    expect(frequencias.map((botao) => botao.disabled)).toEqual([true, true, true]);
    expect(texto(fixture)).toContain('A frequência não muda. Encerre esta recorrência e crie outra.');
    const opcoes = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll<HTMLOptionElement>('option'));
    expect(opcoes.map((opcao) => opcao.textContent?.trim())).toContain('Banco Antigo (arquivada)');
  });

  it('salvar manda o corpo e mostra os erros 400 e 422', () => {
    const fixture = criar();
    fixture.componentInstance.formulario.setValue({
      tipo: 'DESPESA', descricao: ' Aluguel Fictício ', valor: '1.850,00', valorVaria: false, alvo: 'conta-1',
      categoria: '12', frequencia: 'MENSAL', diaDoMes: 30, comecaEm: '2026-11-25', terminaEm: '',
    });
    servico.criar.mockReturnValueOnce(throwError(() => new HttpErrorResponse({
      status: 400,
      error: { campos: [{ campo: 'descricao', mensagem: 'A descrição é obrigatória.' }] },
    })));
    fixture.componentInstance.salvar();
    fixture.detectChanges();
    expect(texto(fixture)).toContain('A descrição é obrigatória.');
    servico.criar.mockReturnValueOnce(throwError(() => new HttpErrorResponse({
      status: 422,
      error: { detail: 'Já existe uma recorrência igual.' },
    })));
    fixture.componentInstance.salvar();
    fixture.detectChanges();
    expect(servico.criar).toHaveBeenLastCalledWith({
      tipo: 'DESPESA', descricao: 'Aluguel Fictício', valor: 1850, valorVaria: false, categoriaId: 12,
      contaId: 1, cartaoId: null, frequencia: 'MENSAL', diaDoMes: 30, comecaEm: '2026-11-25',
      terminaEm: null,
    });
    const alerta = (fixture.nativeElement as HTMLElement).querySelector('app-alerta') as HTMLElement;
    expect(alerta.querySelector('.flux-alerta__titulo')?.textContent).toBe('Não foi possível salvar.');
    expect(alerta.querySelector('.flux-alerta__texto')?.textContent).toBe('Já existe uma recorrência igual.');
  });
});
