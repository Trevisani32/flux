import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { Conta, ListaDeContas } from '../../contas/contas.service';
import { Fatura } from '../cartoes.service';
import { Pagamento } from '../pagamentos.service';
import { abrirPagamento, DadosDoDialogoDePagamento, ResultadoDoPagamento } from './pagamento-dialogo';

function conta(parcial: Partial<Conta>): Conta {
  return { id: 1, nome: 'Banco Horizonte', tipo: 'CORRENTE', saldoInicial: 0, dataSaldoInicial: '2026-10-01', cor: null, saldoAtual: 0,
    arquivadaEm: null, ...parcial };
}

const contas: ListaDeContas = {
  ativas: [conta({}), conta({ id: 3, nome: 'Corretora', tipo: 'INVESTIMENTO' })],
  arquivadas: [conta({ id: 2, nome: 'Poupança antiga', tipo: 'POUPANCA', arquivadaEm: '2026-11-01T15:00:00Z' })],
  totais: { geral: 0, disponivel: 0, investimentos: 0 },
};

/** A fatura de novembro do Cartão Horizonte (fecha 03/11, vence 10/11), fechada, com R$ 265,20 pendentes. */
function fatura(parcial: Partial<Fatura>): Fatura {
  return {
    cartaoId: 7, cartaoNome: 'Cartão Horizonte', cartaoCor: 'OURO', cartaoArquivado: false, mes: '2026-11', inicio: '2026-10-04',
    fechamento: '2026-11-03', vencimento: '2026-11-10', gravada: true, fechamentoAjustado: false, fechamentoNormal: '2026-11-03', ajusteDe: '2026-10-04',
    ajusteAte: '2026-11-09', ajusteAteOVencimento: true, situacao: 'FECHADA', compras: 265.2, quantidadeCompras: 3, estornos: 0, quantidadeEstornos: 0,
    total: 265.2, depois: 0, depoisAte: null, primeiroMes: '2026-10', ultimoMes: '2027-12', itens: [], pago: 0, quantidadePagamentos: 0,
    pendente: 265.2, pagoAMais: 0, pagaFora: false, limite: 5000, disponivel: 732.65, usoDoLimite: 85, contaPagamentoId: 1, pagamentos: [],
    ...parcial,
  };
}

function pagamento(parcial: Partial<Pagamento>): Pagamento {
  return { id: 5, cartaoId: 7, cartaoNome: 'Cartão Horizonte', cartaoArquivado: false, faturaMes: '2026-11', faturaVencimento: '2026-11-10',
    contaId: 1, contaNome: 'Banco Horizonte', contaArquivada: false, data: '2026-11-10', valor: 265.2, observacao: null, origem: 'MANUAL',
    descricaoBanco: null, somenteLeitura: false, ...parcial };
}

describe('PagamentoDialogo', () => {
  let http: HttpTestingController;
  let resultado: ResultadoDoPagamento | undefined | null;
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

  function abrir(dados: Partial<DadosDoDialogoDePagamento>): void {
    resultado = null;
    abrirPagamento(TestBed.inject(MatDialog), { cartaoId: 7, mes: '2026-11', pagamentoId: null, hoje: '2026-11-12', ...dados })
      .subscribe((valor) => (resultado = valor));
    TestBed.tick();
    http.expectOne('/api/contas').flush(contas);
  }

  function painel(): HTMLElement {
    return document.querySelector('app-pagamento-dialogo') as HTMLElement;
  }

  function texto(seletor: string, raiz: HTMLElement = painel()): string {
    return (raiz.querySelector(seletor)?.textContent ?? '').trim().replace(/\s+/g, ' ');
  }

  function preencher(seletor: string, valor: string): void {
    const entrada = painel().querySelector(seletor) as HTMLInputElement;
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input'));
    TestBed.tick();
  }

  function botao(rotulo: string, raiz: HTMLElement = painel()): HTMLButtonElement {
    return Array.from(raiz.querySelectorAll<HTMLButtonElement>('button')).find((item) => item.textContent?.trim().endsWith(rotulo)) as HTMLButtonElement;
  }

  it('"Pagar fatura": a conta de pagamento, hoje e o pendente vêm preenchidos; registrar fecha com "pago" (decisão 30)', async () => {
    abrir({});
    http.expectOne('/api/cartoes/7/faturas/2026-11').flush(fatura({}));
    TestBed.tick();
    expect(texto('h2')).toBe('Pagar a fatura de novembro');
    expect(texto('.pagamento__subtitulo')).toBe('Cartão Horizonte · pendente R$ 265,20 · vence 10/11');
    const select = painel().querySelector('#pagamento-conta') as HTMLSelectElement;
    expect(Array.from(select.options).map((opcao) => opcao.textContent?.trim())).toEqual(['Escolha', 'Banco Horizonte']);
    expect(select.selectedIndex).toBe(1);
    expect((painel().querySelector('#pagamento-data') as HTMLInputElement).value).toBe('2026-11-12');
    expect((painel().querySelector('#pagamento-data') as HTMLInputElement).max).toBe('2026-11-12');
    expect((painel().querySelector('#pagamento-valor') as HTMLInputElement).value).toBe('265,20');
    expect(texto('#pagamento-valor-dica')).toBe('Pode ser parcial ou passar do pendente.');
    expect(painel().querySelector('app-alerta')).toBeNull();
    expect(Array.from(painel().querySelectorAll('.pagamento__acoes button')).map((item) => item.textContent?.trim()))
      .toEqual(['Cancelar', 'Registrar pagamento']);
    preencher('#pagamento-observacao', '  débito automático ');
    botao('Registrar pagamento').click();
    const pedido = http.expectOne('/api/cartoes/7/faturas/2026-11/pagamentos');
    expect(pedido.request.method).toBe('POST');
    expect(pedido.request.body).toEqual({ contaId: 1, data: '2026-11-12', valor: 265.2, observacao: 'débito automático' });
    pedido.flush(pagamento({}));
    await vi.waitFor(() => expect(resultado).toBe('pago'));
  });

  it('na aberta, avisa que compras ainda entram; a conta arquivada não vem preenchida; a data futura e o valor vazio param na tela', () => {
    abrir({ mes: '2026-12' });
    http.expectOne('/api/cartoes/7/faturas/2026-12').flush(fatura({ mes: '2026-12', situacao: 'ABERTA', fechamento: '2026-12-03',
      vencimento: '2026-12-10', pendente: 0, contaPagamentoId: 2 }));
    TestBed.tick();
    expect(texto('h2')).toBe('Pagar a fatura de dezembro');
    expect(texto('app-alerta .flux-alerta__titulo')).toBe('A fatura ainda está aberta');
    expect(texto('app-alerta .flux-alerta__texto')).toBe('Compras até 03/12 ainda podem entrar nela.');
    expect((painel().querySelector('#pagamento-conta') as HTMLSelectElement).selectedIndex).toBe(0);
    expect((painel().querySelector('#pagamento-valor') as HTMLInputElement).value).toBe('');
    preencher('#pagamento-data', '2026-11-13');
    botao('Registrar pagamento').click();
    TestBed.tick();
    expect(texto('#pagamento-conta-erro')).toBe('Escolha a conta.');
    expect(texto('#pagamento-data-erro')).toBe('A data pode ir até hoje, 12/11/2026.');
    expect(texto('#pagamento-valor-erro')).toBe('Informe o valor.');
  });

  it('o 422 com campo vai para o campo da conta, e o 400 da data, para o da data', () => {
    abrir({});
    http.expectOne('/api/cartoes/7/faturas/2026-11').flush(fatura({}));
    TestBed.tick();
    botao('Registrar pagamento').click();
    http.expectOne('/api/cartoes/7/faturas/2026-11/pagamentos').flush({ detail: 'Banco Horizonte está arquivada. Escolha uma conta ativa.',
      campo: 'contaId' }, { status: 422, statusText: 'Unprocessable Entity' });
    TestBed.tick();
    expect(texto('#pagamento-conta-erro')).toBe('Banco Horizonte está arquivada. Escolha uma conta ativa.');
    botao('Registrar pagamento').click();
    http.expectOne('/api/cartoes/7/faturas/2026-11/pagamentos').flush({ detail: 'Dados inválidos.',
      campos: [{ campo: 'data', mensagem: 'A data precisa ser 01/10/2026 ou depois, a data do saldo inicial de Banco Horizonte.' }] },
    { status: 400, statusText: 'Bad Request' });
    TestBed.tick();
    expect(texto('#pagamento-data-erro')).toBe('A data precisa ser 01/10/2026 ou depois, a data do saldo inicial de Banco Horizonte.');
    expect(resultado).toBeNull();
  });

  it('o lançado à mão se edita com PUT e se exclui com a confirmação (decisão 33)', async () => {
    abrir({ cartaoId: null, mes: null, pagamentoId: 5 });
    http.expectOne('/api/pagamentos/5').flush(pagamento({ observacao: 'pix' }));
    TestBed.tick();
    expect(texto('h2')).toBe('Pagamento da fatura de novembro');
    expect(texto('.pagamento__subtitulo')).toBe('Cartão Horizonte · vence 10/11');
    expect((painel().querySelector('#pagamento-valor') as HTMLInputElement).value).toBe('265,20');
    expect(Array.from(painel().querySelectorAll('.pagamento__acoes button')).map((item) => item.textContent?.trim()))
      .toEqual(['Excluir', 'Cancelar', 'Salvar']);
    preencher('#pagamento-valor', '200');
    botao('Salvar').click();
    const pedido = http.expectOne('/api/pagamentos/5');
    expect(pedido.request.method).toBe('PUT');
    expect(pedido.request.body).toEqual({ contaId: 1, data: '2026-11-10', valor: 200, observacao: 'pix' });
    pedido.flush(pagamento({ valor: 200 }));
    await vi.waitFor(() => expect(resultado).toBe('salvo'));
    expect(snackBar.open).toHaveBeenCalledWith('Pagamento salvo.', 'Fechar', { duration: 4000 });

    abrir({ cartaoId: null, mes: null, pagamentoId: 5 });
    http.expectOne('/api/pagamentos/5').flush(pagamento({}));
    TestBed.tick();
    botao('Excluir').click();
    TestBed.tick();
    const confirmacao = document.querySelector('app-confirmacao-dialogo') as HTMLElement;
    expect(texto('h2', confirmacao)).toBe('Excluir o pagamento?');
    expect(texto('.confirmacao__texto', confirmacao)).toBe('O pagamento de R$ 265,20 sai de Banco Horizonte, e a fatura de novembro volta a ter '
      + 'esse valor pendente. Isso não pode ser desfeito.');
    botao('Excluir pagamento', confirmacao).click();
    http.expectOne({ method: 'DELETE', url: '/api/pagamentos/5' }).flush(null);
    await vi.waitFor(() => expect(resultado).toBe('excluido'));
  });

  it('o importado trava conta, data e valor e se desfaz com a confirmação (decisão 37)', async () => {
    abrir({ cartaoId: null, mes: null, pagamentoId: 5 });
    http.expectOne('/api/pagamentos/5').flush(pagamento({ origem: 'IMPORTADO', descricaoBanco: 'PAG FATURA CARTAO HORIZONTE' }));
    TestBed.tick();
    expect(texto('app-alerta .flux-alerta__titulo')).toBe('Pagamento importado');
    expect((painel().querySelector('#pagamento-conta') as HTMLSelectElement).disabled).toBe(true);
    expect((painel().querySelector('#pagamento-data') as HTMLInputElement).disabled).toBe(true);
    expect((painel().querySelector('#pagamento-valor') as HTMLInputElement).disabled).toBe(true);
    expect(Array.from(painel().querySelectorAll('.pagamento__acoes button')).map((item) => item.textContent?.trim()))
      .toEqual(['Desfazer', 'Cancelar', 'Salvar']);
    botao('Desfazer').click();
    TestBed.tick();
    const confirmacao = document.querySelector('app-confirmacao-dialogo') as HTMLElement;
    expect(texto('.confirmacao__texto', confirmacao)).toBe('O lançamento volta a ser uma despesa de Banco Horizonte, sem categoria, e a fatura de '
      + 'novembro volta a ter esse valor pendente.');
    botao('Desfazer pagamento', confirmacao).click();
    http.expectOne({ method: 'POST', url: '/api/pagamentos/5/desfazer' }).flush({});
    await vi.waitFor(() => expect(resultado).toBe('desfeito'));
    expect(snackBar.open).toHaveBeenCalledWith('O pagamento voltou a ser despesa.', 'Fechar', { duration: 4000 });
  });

  it('o pagamento que não existe mais (excluído em outra aba) avisa e fecha com "inexistente", pelo botão e pelo Esc (Correções do QA)',
    async () => {
      abrir({ cartaoId: null, mes: null, pagamentoId: 5 });
      http.expectOne('/api/pagamentos/5').flush({ detail: 'Pagamento não encontrado.' }, { status: 404, statusText: 'Not Found' });
      TestBed.tick();
      expect(texto('app-alerta .flux-alerta__titulo')).toBe('Pagamento não encontrado.');
      botao('Fechar').click();
      await vi.waitFor(() => expect(resultado).toBe('inexistente'));
      abrir({ cartaoId: null, mes: null, pagamentoId: 6 });
      http.expectOne('/api/pagamentos/6').flush({ detail: 'Pagamento não encontrado.' }, { status: 404, statusText: 'Not Found' });
      TestBed.tick();
      document.querySelector('.cdk-overlay-pane')?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      await vi.waitFor(() => expect(resultado).toBe('inexistente'));
    });

  it('o 404 ao salvar (o pagamento ou o cartão excluído em outra aba depois de abrir) avisa e fecha com "inexistente", pelo botão e pelo '
    + 'Esc (Correções do QA)', async () => {
    abrir({ cartaoId: null, mes: null, pagamentoId: 5 });
    http.expectOne('/api/pagamentos/5').flush(pagamento({}));
    TestBed.tick();
    botao('Salvar').click();
    http.expectOne({ method: 'PUT', url: '/api/pagamentos/5' }).flush({ detail: 'Pagamento não encontrado.' }, { status: 404, statusText: 'Not Found' });
    TestBed.tick();
    expect(texto('app-alerta .flux-alerta__titulo')).toBe('Pagamento não encontrado.');
    expect(resultado).toBeNull();
    botao('Cancelar').click();
    await vi.waitFor(() => expect(resultado).toBe('inexistente'));

    abrir({});
    http.expectOne('/api/cartoes/7/faturas/2026-11').flush(fatura({}));
    TestBed.tick();
    botao('Registrar pagamento').click();
    http.expectOne({ method: 'POST', url: '/api/cartoes/7/faturas/2026-11/pagamentos' }).flush({ detail: 'Cartão não encontrado.' },
      { status: 404, statusText: 'Not Found' });
    TestBed.tick();
    expect(texto('app-alerta .flux-alerta__titulo')).toBe('Cartão não encontrado.');
    document.querySelector('.cdk-overlay-pane')?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await vi.waitFor(() => expect(resultado).toBe('inexistente'));
  });

  it('com a conta arquivada, a ficha fica só para leitura', () => {
    abrir({ cartaoId: null, mes: null, pagamentoId: 5 });
    http.expectOne('/api/pagamentos/5').flush(pagamento({ somenteLeitura: true, contaArquivada: true, observacao: 'pix' }));
    TestBed.tick();
    expect(texto('app-alerta .flux-alerta__titulo')).toBe('Banco Horizonte está arquivada');
    expect(texto('app-alerta .flux-alerta__texto')).toBe('Reative a conta em Contas, na seção "Arquivadas", para editar ou excluir este pagamento.');
    expect(Array.from(painel().querySelectorAll('.pagamento__ficha dd')).map((item) => item.textContent?.trim().replace(/\s+/g, ' ')))
      .toEqual(['R$ 265,20', '10/11/2026', 'Banco Horizonte', 'Cartão Horizonte', 'pix']);
    expect(painel().querySelector('form')).toBeNull();
  });
});
