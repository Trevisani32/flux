import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { CartoesService } from '../cartoes/cartoes.service';
import { CategoriasService } from '../categorias/categorias.service';
import { ContasService } from '../contas/contas.service';
import { FluxoDaOcorrencia } from './fluxo-da-ocorrencia';
import { Ocorrencia, RecorrenciasService } from './recorrencias.service';

const ocorrencia: Ocorrencia = {
  recorrenciaId: 7,
  periodo: '2026-11-01',
  data: '2026-11-30',
  dataNaLista: '2026-11-30',
  descricao: 'Conta de luz Fictícia',
  tipo: 'DESPESA',
  valor: -189.9,
  valorPrevisto: 189.9,
  valorVaria: false,
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

describe('FluxoDaOcorrencia', () => {
  let fechado: Subject<unknown>;
  let desfazer: Subject<void>;
  const dialogo = { open: vi.fn(() => ({ afterClosed: () => fechado })) };
  const snackBar = { open: vi.fn(() => ({ onAction: () => desfazer })) };
  const recorrencias = { pular: vi.fn(), voltarAPrever: vi.fn(), buscar: vi.fn() };

  beforeEach(() => {
    fechado = new Subject<unknown>();
    desfazer = new Subject<void>();
    dialogo.open.mockClear();
    snackBar.open.mockClear();
    recorrencias.pular.mockReset();
    recorrencias.voltarAPrever.mockReset();
    recorrencias.pular.mockReturnValue(of(ocorrencia));
    recorrencias.voltarAPrever.mockReturnValue(of(undefined));
    TestBed.configureTestingModule({
      providers: [
        { provide: MatDialog, useValue: dialogo },
        { provide: MatSnackBar, useValue: snackBar },
        { provide: RecorrenciasService, useValue: recorrencias },
        { provide: ContasService, useValue: { listar: () => of({}) } },
        { provide: CartoesService, useValue: { listar: () => of({}) } },
        { provide: CategoriasService, useValue: { listar: () => of({}) } },
      ],
    });
  });

  function fluxo(): FluxoDaOcorrencia {
    return TestBed.inject(FluxoDaOcorrencia);
  }

  it('confirmar na conta avisa o lançamento criado e emite mudou$', () => {
    const mudou = vi.fn();
    fluxo().mudou$.subscribe(mudou);
    fluxo().confirmar(ocorrencia, '2026-11-25');
    fechado.next({
      recorrenciaId: 7,
      periodo: '2026-11-01',
      lancamentoId: 31,
      compraId: null,
      data: '2026-11-30',
      faturaMes: null,
      cartaoNome: null,
    });
    expect(snackBar.open).toHaveBeenCalledWith(
      'Conta de luz Fictícia confirmada: lançamento de 30/11 criado.',
      'Fechar',
      { duration: 5000 },
    );
    expect(mudou).toHaveBeenCalledTimes(1);
  });

  it('no cartão avisa a fatura', () => {
    const netflix: Ocorrencia = {
      ...ocorrencia,
      descricao: 'Netflix Fictícia',
      data: '2026-12-25',
      contaId: null,
      contaNome: null,
      cartaoId: 4,
      cartaoNome: 'Cartão Lua',
      cartaoCor: 'ROXO',
    };
    const mudou = vi.fn();
    fluxo().mudou$.subscribe(mudou);
    fluxo().confirmar(netflix, '2026-11-25');
    fechado.next({
      recorrenciaId: 7,
      periodo: '2026-12-01',
      lancamentoId: null,
      compraId: 32,
      data: '2026-12-25',
      faturaMes: '2027-01',
      cartaoNome: 'Cartão Lua',
    });
    expect(snackBar.open).toHaveBeenCalledWith(
      'Netflix Fictícia confirmada: compra lançada na fatura de janeiro do Cartão Lua.',
      'Fechar',
      { duration: 5000 },
    );
    expect(mudou).toHaveBeenCalledTimes(1);
  });

  it('ligar pelo diálogo de confirmar avisa a ligação, na conta e no cartão', () => {
    const mudou = vi.fn();
    fluxo().mudou$.subscribe(mudou);
    fluxo().confirmar(ocorrencia, '2026-11-25');
    fechado.next({
      recorrenciaId: 7, periodo: '2026-11-01', lancamentoId: 81, compraId: null, data: '2026-11-29', faturaMes: null,
      cartaoNome: null, ligada: true,
    });
    expect(snackBar.open).toHaveBeenLastCalledWith('Ligada ao lançamento de 29/11.', 'Fechar', { duration: 5000 });
    fechado = new Subject<unknown>();
    fluxo().jaLancei({ ...ocorrencia, contaId: null, contaNome: null, cartaoId: 4, cartaoNome: 'Cartão Lua' }, '2026-11-25');
    fechado.next({
      recorrenciaId: 7, periodo: '2026-11-01', lancamentoId: null, compraId: 32, data: '2026-11-28', faturaMes: '2026-12',
      cartaoNome: 'Cartão Lua', ligada: true,
    });
    expect(snackBar.open).toHaveBeenLastCalledWith('Ligada à compra de 28/11.', 'Fechar', { duration: 5000 });
    expect(snackBar.open).toHaveBeenCalledTimes(2);
    expect(mudou).toHaveBeenCalledTimes(2);
  });

  it('pular avisa com Desfazer, e o Desfazer volta a prever e emite mudou$', () => {
    const sexta: Ocorrencia = { ...ocorrencia, data: '2026-11-27' };
    const mudou = vi.fn();
    fluxo().mudou$.subscribe(mudou);
    fluxo().pular(sexta);
    expect(recorrencias.pular).toHaveBeenCalledWith(7, '2026-11-01');
    expect(snackBar.open).toHaveBeenCalledWith('Ocorrência de 27/11 pulada.', 'Desfazer', { duration: 5000 });
    desfazer.next();
    expect(recorrencias.voltarAPrever).toHaveBeenCalledWith(7, '2026-11-01');
    expect(mudou).toHaveBeenCalledTimes(2);
  });
});
