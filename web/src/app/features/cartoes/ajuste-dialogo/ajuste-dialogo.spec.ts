import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { Fatura, ResultadoDoAjuste } from '../cartoes.service';
import { abrirAjuste, avisoDoAjuste } from './ajuste-dialogo';

/** Fatura de dezembro de um cartão que fecha dia 3 e vence dia 10: o ajuste vai de 04/11 a 09/12 (decisão 12). */
const dezembro: Fatura = {
  cartaoId: 7, cartaoNome: 'Cartão Horizonte', cartaoCor: null, cartaoArquivado: false, mes: '2026-12', inicio: '2026-11-04',
  fechamento: '2026-12-03', vencimento: '2026-12-10', gravada: true, fechamentoAjustado: false, fechamentoNormal: '2026-12-03', ajusteDe: '2026-11-04',
  ajusteAte: '2026-12-09', ajusteAteOVencimento: true, situacao: 'ABERTA', compras: 0, quantidadeCompras: 0, estornos: 0, quantidadeEstornos: 0,
  total: 0, depois: 0, depoisAte: null, primeiroMes: '2026-11', ultimoMes: '2027-12', itens: [],
};

describe('AjusteDialogo', () => {
  let http: HttpTestingController;
  let resultado: ResultadoDoAjuste | undefined | null;
  const snackBar = { open: vi.fn() };

  beforeEach(() => {
    snackBar.open.mockClear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), { provide: MatSnackBar, useValue: snackBar }] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    try {
      http.verify();
    } finally {
      TestBed.inject(MatDialog).closeAll();
      TestBed.resetTestingModule();
    }
  });

  function abrir(fatura: Fatura): void {
    resultado = null;
    abrirAjuste(TestBed.inject(MatDialog), fatura).subscribe((valor) => (resultado = valor));
    TestBed.tick();
  }

  function painel(): HTMLElement {
    return document.querySelector('app-ajuste-dialogo') as HTMLElement;
  }

  function escolherData(valor: string): void {
    const entrada = painel().querySelector('#ajuste-data') as HTMLInputElement;
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input'));
    TestBed.tick();
  }

  function ajustar(): void {
    painel().querySelector<HTMLButtonElement>('button[type="submit"]')?.click();
    TestBed.tick();
  }

  it('escreve os avisos do resultado, com as compras e os estornos à parte', () => {
    expect(avisoDoAjuste(0, 0)).toBe('Fechamento ajustado.');
    expect(avisoDoAjuste(1, 0)).toBe('Fechamento ajustado. 1 compra mudou de fatura.');
    expect(avisoDoAjuste(2, 0)).toBe('Fechamento ajustado. 2 compras mudaram de fatura.');
    expect(avisoDoAjuste(0, 1)).toBe('Fechamento ajustado. 1 estorno mudou de fatura.');
    expect(avisoDoAjuste(2, 1)).toBe('Fechamento ajustado. 2 compras e 1 estorno mudaram de fatura.');
    expect(avisoDoAjuste(1, 2)).toBe('Fechamento ajustado. 1 compra e 2 estornos mudaram de fatura.');
  });

  it('mostra os limites e diz, só com datas, o trecho que muda de fatura (decisão 12)', () => {
    abrir(dezembro);
    expect(painel().querySelector('h2')?.textContent).toBe('Ajustar o fechamento de dezembro');
    expect(painel().querySelector('.ajuste__texto')?.textContent).toBe('Use quando o banco fechar esta fatura em outro dia. Pelo cartão, ela fecha em 03/12.');
    expect(painel().querySelector('#ajuste-data-dica')?.textContent)
      .toBe('De 04/11 a 09/12: depois do fechamento de novembro e antes do vencimento, 10/12.');
    expect(painel().querySelector('app-alerta')).toBeNull();
    escolherData('2026-12-05');
    expect(painel().querySelector('.flux-alerta__titulo')?.textContent).toBe('Compras de 04/12 a 05/12 passam de janeiro para esta fatura.');
    expect(painel().querySelector('.flux-alerta__texto')?.textContent)
      .toBe('As parcelas delas andam junto, um mês antes. A fatura de janeiro passa a começar em 06/12.');
    escolherData('2026-12-02');
    expect(painel().querySelector('.flux-alerta__titulo')?.textContent).toBe('Compras de 03/12 passam desta fatura para janeiro.');
  });

  it('o limite que vem do fechamento da seguinte aparece na dica', () => {
    abrir({ ...dezembro, ajusteAte: '2026-12-31', ajusteAteOVencimento: false, vencimento: '2027-01-05', fechamento: '2026-12-25',
      fechamentoNormal: '2026-12-25' });
    expect(painel().querySelector('#ajuste-data-dica')?.textContent)
      .toBe('De 04/11 a 31/12: depois do fechamento de novembro e antes do fechamento de janeiro, 01/01.');
  });

  it('fora dos limites, não chama a API; dentro, ajusta e fecha com o resultado', async () => {
    abrir(dezembro);
    escolherData('2026-12-10');
    ajustar();
    expect(painel().querySelector('#ajuste-data-erro')?.textContent)
      .toBe('De 04/11 a 09/12: depois do fechamento de novembro e antes do vencimento, 10/12.');
    escolherData('2026-12-04');
    ajustar();
    const pedido = http.expectOne({ method: 'PUT', url: '/api/cartoes/7/faturas/2026-12/fechamento' });
    expect(pedido.request.body).toEqual({ data: '2026-12-04' });
    const resposta: ResultadoDoAjuste = { comprasMovidas: 1, estornosMovidos: 0, fatura: { ...dezembro, fechamento: '2026-12-04', fechamentoAjustado: true } };
    pedido.flush(resposta);
    TestBed.tick();
    await vi.waitFor(() => expect(resultado).toEqual(resposta));
    expect(snackBar.open).toHaveBeenCalledWith('Fechamento ajustado. 1 compra mudou de fatura.', 'Fechar', { duration: 5000 });
  });

  it('o 422 da data volta ao campo, com a mensagem da API', () => {
    abrir(dezembro);
    escolherData('2026-12-04');
    ajustar();
    http.expectOne({ method: 'PUT', url: '/api/cartoes/7/faturas/2026-12/fechamento' }).flush(
      { codigo: 'FECHAMENTO_FORA_DOS_LIMITES', campo: 'data', detail: 'Escolha uma data de 05/11/2026 a 09/12/2026: depois do fechamento de novembro e antes do vencimento de dezembro.' },
      { status: 422, statusText: 'Unprocessable' });
    TestBed.tick();
    expect(painel().querySelector('#ajuste-data-erro')?.textContent)
      .toBe('Escolha uma data de 05/11/2026 a 09/12/2026: depois do fechamento de novembro e antes do vencimento de dezembro.');
    expect(resultado).toBeNull();
  });
});
