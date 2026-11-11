import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { Excluido, Importacao } from '../importacao.service';
import { abrirExcluidos } from './excluidos-dialogo';

const importacao: Importacao = {
  id: 12, nomeArquivo: 'poupanca-outubro.ofx', contaId: 3, contaNome: 'Poupança', contaArquivada: false, criadaEm: '2026-11-06T12:15:00Z',
  dataInicio: '2026-10-01', dataFim: '2026-10-31', importadas: 3, jaImportadas: 0, desmarcadas: 0, deFora: 0, editados: 0, excluidos: 2,
  desfeitaEm: null,
};

const excluidos: Excluido[] = [
  { id: 41, data: '2026-10-05', descricao: 'TARIFA MANUTENCAO', valor: -4.9, ignoradoEm: '2026-11-06T15:00:00Z' },
  { id: 42, data: '2026-10-10', descricao: 'TRANSF ENTRE CONTAS', valor: 500, ignoradoEm: '2026-11-06T15:05:00Z' },
];

describe('ExcluidosDialogo', () => {
  let httpTesting: HttpTestingController;
  let resultado: boolean | undefined | null;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    TestBed.inject(MatDialog).closeAll();
  });

  function abrir(): HTMLElement {
    resultado = null;
    abrirExcluidos(TestBed.inject(MatDialog), importacao).subscribe((valor) => (resultado = valor));
    TestBed.tick();
    httpTesting.expectOne({ method: 'GET', url: '/api/importacoes/12/excluidos' }).flush(excluidos);
    TestBed.tick();
    return document.querySelector('app-excluidos-dialogo') as HTMLElement;
  }

  function botoes(painel: HTMLElement, texto: string): HTMLButtonElement[] {
    return Array.from(painel.querySelectorAll<HTMLButtonElement>('button')).filter((botao) => botao.textContent?.trim() === texto);
  }

  function descricoes(painel: HTMLElement): (string | null)[] {
    return Array.from(painel.querySelectorAll('.excluidos__textos b')).map((item) => item.textContent);
  }

  it('lista os excluídos; "Restaurar" manda sem categoria, tira o item da lista e avisa; "Fechar" devolve true (decisão 31)', async () => {
    const aviso = vi.spyOn(TestBed.inject(MatSnackBar), 'open');
    const painel = abrir();
    expect(descricoes(painel)).toEqual(['TARIFA MANUTENCAO', 'TRANSF ENTRE CONTAS']);
    expect(painel.querySelector('.excluidos__textos span')?.textContent).toBe('05/10/2026 · excluído em 06/11');

    botoes(painel, 'Restaurar')[0].click();
    const pedido = httpTesting.expectOne({ method: 'POST', url: '/api/lancamentos/41/restaurar' });
    expect(pedido.request.body).toEqual({ categoriaId: null });
    pedido.flush({});
    TestBed.tick();
    expect(descricoes(painel)).toEqual(['TRANSF ENTRE CONTAS']);
    expect(aviso).toHaveBeenCalledWith('Lançamento restaurado.', 'Fechar', { duration: 4000 });

    botoes(painel, 'Fechar')[0].click();
    await vi.waitFor(() => expect(resultado).toBe(true));
  });

  it('o 422 da API aparece no alerta, com o detail, e a lista continua', () => {
    const painel = abrir();
    botoes(painel, 'Restaurar')[1].click();
    httpTesting.expectOne({ method: 'POST', url: '/api/lancamentos/42/restaurar' }).flush({ codigo: 'CONTA_ARQUIVADA',
      detail: 'Poupança está arquivada. Reative a conta para restaurar este lançamento.' }, { status: 422, statusText: 'Unprocessable Entity' });
    TestBed.tick();
    expect(painel.querySelector('.flux-alerta--erro .flux-alerta__titulo')?.textContent)
      .toBe('Poupança está arquivada. Reative a conta para restaurar este lançamento.');
    expect(descricoes(painel)).toEqual(['TARIFA MANUTENCAO', 'TRANSF ENTRE CONTAS']);
  });
});
