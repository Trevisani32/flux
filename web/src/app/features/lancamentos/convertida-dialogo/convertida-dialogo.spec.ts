import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ListaDeCategorias } from '../../categorias/categorias.service';
import { Conta, ListaDeContas } from '../../contas/contas.service';
import { abrirDialogoDeLancamento, ResultadoDoLancamento } from '../lancamento-dialogo/lancamento-dialogo';
import { ItemDaLista, LadoDaTransferencia } from '../lancamentos.service';

function conta(id: number, nome: string, arquivadaEm: string | null = null): Conta {
  return { id, nome, tipo: 'CORRENTE', saldoInicial: 0, dataSaldoInicial: '2026-10-01', cor: null, saldoAtual: 0, arquivadaEm };
}

const contas: ListaDeContas = {
  ativas: [conta(1, 'Banco Horizonte'), conta(2, 'Conta conjunta')],
  arquivadas: [conta(3, 'Poupança antiga', '2026-11-03T15:00:00Z')],
  totais: { geral: 0, disponivel: 0, investimentos: 0 },
};

const categorias: ListaDeCategorias = { despesas: { ativas: [], arquivadas: [] }, receitas: { ativas: [], arquivadas: [] } };

const saida: LadoDaTransferencia = { contaId: 1, contaNome: 'Banco Horizonte', data: '2026-12-02', origem: 'IMPORTADO',
  descricaoBanco: 'TED 341.0001DEV FICTICIO', observacao: 'Passagens' };
const entrada: LadoDaTransferencia = { contaId: 2, contaNome: 'Conta conjunta', data: '2026-12-03', origem: 'IMPORTADO',
  descricaoBanco: 'Transferência recebida - DEV FICTICIO', observacao: 'Hotel' };

function convertida(parcial: Partial<ItemDaLista>): ItemDaLista {
  return {
    tipo: 'TRANSFERENCIA', id: 40, data: '2026-12-02', descricao: 'Reserva para a viagem', valor: 1000, observacao: 'Passagens', contaId: 1,
    contaNome: 'Banco Horizonte', categoriaId: null, categoriaNome: null, categoriaMaeNome: null, categoriaArquivada: false, contaDestinoId: 2,
    contaDestinoNome: 'Conta conjunta', direcao: null, futuro: false, somenteLeitura: false, origem: 'IMPORTADO', descricaoBanco: null,
    lados: [saida, entrada], ...parcial,
  };
}

describe('ConvertidaDialogo', () => {
  let httpTesting: HttpTestingController;
  let resultado: ResultadoDoLancamento | undefined | null;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    httpTesting = TestBed.inject(HttpTestingController);
  });

  // Mesmo quando o verify acusa um pedido pendente, o diálogo fecha e o TestBed volta ao zero: um afterEach que lança
  // pula a limpeza do Angular, e o diálogo e o módulo velhos derrubariam os casos (e os arquivos) seguintes.
  afterEach(() => {
    try {
      httpTesting.verify();
    } finally {
      TestBed.inject(MatDialog).closeAll();
      TestBed.resetTestingModule();
    }
  });

  function abrir(item: ItemDaLista): void {
    resultado = null;
    abrirDialogoDeLancamento(TestBed.inject(MatDialog), { item, hoje: '2026-12-10', contas, categorias, contaPadrao: null })
      .subscribe((valor) => (resultado = valor));
    TestBed.tick();
  }

  function painel(): HTMLElement {
    return document.querySelector('app-convertida-dialogo') as HTMLElement;
  }

  function textos(seletor: string): string[] {
    return Array.from(painel().querySelectorAll(seletor)).map((no) => no.textContent?.trim().replace(/\s+/g, ' ') ?? '');
  }

  function botao(texto: string): HTMLButtonElement {
    return Array.from(painel().querySelectorAll<HTMLButtonElement>('button')).find((item) => item.textContent?.trim() === texto) as HTMLButtonElement;
  }

  function digitar(seletor: string, valor: string): void {
    const entradaDeTexto = painel().querySelector(seletor) as HTMLInputElement;
    entradaDeTexto.value = valor;
    entradaDeTexto.dispatchEvent(new Event('input'));
    TestBed.tick();
  }

  it('a convertida abre no diálogo dela: valor e lados travados, o "No extrato" e as observações dos dois; salvar muda só o texto', async () => {
    abrir(convertida({}));

    expect(document.querySelector('app-lancamento-dialogo')).toBeNull();
    expect(painel().querySelector('h2')?.textContent).toBe('Editar transferência');
    expect(textos('.flux-alerta__titulo')).toEqual(['Convertida de lançamentos importados']);
    expect(textos('.flux-alerta__texto')).toEqual(['Contas, datas e valor vêm dos extratos e não mudam.']);
    const valor = painel().querySelector('#convertida-valor') as HTMLInputElement;
    expect(valor.value).toBe('1.000,00');
    expect(valor.disabled).toBe(true);
    expect(painel().querySelector('label[for="convertida-valor"]')?.textContent).toBe('Valor');
    expect(textos('.convertida__rotulo')).toEqual(['De', 'Para']);
    expect(textos('.convertida__conta')).toEqual(['Banco Horizonte', 'Conta conjunta']);
    expect(textos('.convertida__meta')).toEqual(['02/12/2026 · importado', 'No extrato: TED 341.0001DEV FICTICIO', '03/12/2026 · importado',
      'No extrato: Transferência recebida - DEV FICTICIO']);
    expect((painel().querySelector('#convertida-descricao') as HTMLInputElement).value).toBe('Reserva para a viagem');
    expect((painel().querySelector('#convertida-observacao') as HTMLTextAreaElement).value).toBe('Passagens\nHotel');
    expect(Array.from(painel().querySelectorAll('button')).map((item) => item.textContent?.trim()))
      .toEqual(['Desfazer transferência', 'Cancelar', 'Salvar']);

    digitar('#convertida-descricao', '  ');
    botao('Salvar').click();
    botao('Salvar').click();
    TestBed.tick();
    const pedido = httpTesting.expectOne({ method: 'PUT', url: '/api/transferencias/40' });
    expect(pedido.request.body).toEqual({ valor: 1000, data: '2026-12-02', contaOrigemId: 1, contaDestinoId: 2, descricao: null,
      observacao: 'Passagens\nHotel' });
    expect(TestBed.inject(MatDialog).openDialogs[0].disableClose).toBe(true);
    pedido.flush(convertida({ descricao: 'Transferência' }));
    await vi.waitFor(() => expect(resultado).toBe('salvo'));
  });

  it('com um lado lançado à mão, o alerta e o "Desfazer" dizem isso; desfeita, fecha com "desfeita"', async () => {
    abrir(convertida({ lados: [saida, { ...entrada, origem: 'MANUAL', descricaoBanco: null, observacao: null }] }));
    expect(textos('.flux-alerta__titulo')).toEqual(['Convertida de um lançamento importado']);
    expect(textos('.convertida__meta')).toEqual(['02/12/2026 · importado', 'No extrato: TED 341.0001DEV FICTICIO', '03/12/2026 · lançado à mão']);
    expect((painel().querySelector('#convertida-observacao') as HTMLTextAreaElement).value).toBe('Passagens');

    botao('Desfazer transferência').click();
    TestBed.tick();
    const confirmacao = document.querySelector('app-confirmacao-dialogo') as HTMLElement;
    expect(confirmacao.querySelector('h2')?.textContent).toBe('Desfazer a transferência?');
    expect(Array.from(confirmacao.querySelectorAll('.confirmacao__texto')).map((p) => p.textContent?.replace(/\s+/g, ' '))).toEqual([
      'Os dois lançamentos voltam a ser uma despesa em Banco Horizonte e uma receita em Conta conjunta, sem categoria. O importado volta '
        + 'com a descrição do extrato, e o lançado à mão fica com a descrição que tem.',
      'Nenhum deles é apagado. Se quiser, aplique as regras depois.']);
    (Array.from(confirmacao.querySelectorAll<HTMLButtonElement>('button')).find((item) => item.textContent?.trim()
      === 'Desfazer transferência') as HTMLButtonElement).click();
    TestBed.tick();
    httpTesting.expectOne({ method: 'POST', url: '/api/transferencias/40/desfazer' }).flush([]);
    await vi.waitFor(() => expect(resultado).toBe('desfeita'));
    await vi.waitFor(() => expect(document.querySelector('.mat-mdc-snack-bar-container')?.textContent).toContain('Transferência desfeita.'));
  });

  it('o erro de rede deixa salvar de novo; o 422 (outra aba arquivou a conta) deixa só "Fechar", que pede a recarga', async () => {
    abrir(convertida({}));
    botao('Salvar').click();
    TestBed.tick();
    httpTesting.expectOne({ method: 'PUT', url: '/api/transferencias/40' }).flush(null, { status: 500, statusText: 'Erro' });
    TestBed.tick();
    expect(textos('[role="alert"]')).toEqual(['Não foi possível salvar. Tente novamente.']);
    expect(botao('Salvar').disabled).toBe(false);

    botao('Salvar').click();
    TestBed.tick();
    httpTesting.expectOne({ method: 'PUT', url: '/api/transferencias/40' }).flush({ detail: 'Conta conjunta está arquivada. Reative a conta '
      + 'para editar ou desfazer esta transferência.', codigo: 'CONTA_ARQUIVADA' }, { status: 422, statusText: 'Erro' });
    TestBed.tick();
    expect(textos('[role="alert"]')).toEqual(['Conta conjunta está arquivada. Reative a conta para editar ou desfazer esta transferência.']);
    expect(Array.from(painel().querySelectorAll('button')).map((item) => item.textContent?.trim())).toEqual(['Fechar']);
    botao('Fechar').click();
    TestBed.tick();
    await vi.waitFor(() => expect(resultado).toBe('desatualizada'));
  });

  it('o 404 ao desfazer aparece na confirmação e deixa a convertida desatualizada: só "Fechar", que pede a recarga', async () => {
    abrir(convertida({}));
    botao('Desfazer transferência').click();
    TestBed.tick();
    const confirmacao = document.querySelector('app-confirmacao-dialogo') as HTMLElement;
    const daConfirmacao = (texto: string) => Array.from(confirmacao.querySelectorAll<HTMLButtonElement>('button'))
      .find((item) => item.textContent?.trim() === texto) as HTMLButtonElement;
    daConfirmacao('Desfazer transferência').click();
    TestBed.tick();
    httpTesting.expectOne({ method: 'POST', url: '/api/transferencias/40/desfazer' }).flush({ detail: 'Transferência não encontrada.' },
      { status: 404, statusText: 'Erro' });
    TestBed.tick();
    expect(confirmacao.querySelector('[role="alert"]')?.textContent?.trim()).toBe('Transferência não encontrada.');

    daConfirmacao('Cancelar').click();
    TestBed.tick();
    await vi.waitFor(() => expect(document.querySelector('app-confirmacao-dialogo')).toBeNull());
    expect(Array.from(painel().querySelectorAll('button')).map((item) => item.textContent?.trim())).toEqual(['Fechar']);
    expect(textos('[role="alert"]')).toEqual(['Transferência não encontrada.']);
    expect(resultado).toBeNull();
    botao('Fechar').click();
    TestBed.tick();
    await vi.waitFor(() => expect(resultado).toBe('desatualizada'));
  });

  it('"Cancelar" fecha sem nada; a convertida de uma conta arquivada abre só leitura, como na 03b', async () => {
    abrir(convertida({}));
    botao('Cancelar').click();
    TestBed.tick();
    await vi.waitFor(() => expect(resultado).toBeUndefined());
    httpTesting.expectNone({ method: 'PUT', url: '/api/transferencias/40' });

    abrir(convertida({ somenteLeitura: true, contaDestinoId: 3, contaDestinoNome: 'Poupança antiga' }));
    expect(document.querySelector('app-convertida-dialogo')).toBeNull();
    const leitura = document.querySelector('app-lancamento-dialogo') as HTMLElement;
    expect(leitura.querySelector('.flux-alerta__titulo')?.textContent).toBe('Poupança antiga está arquivada');
    expect(leitura.querySelector('.flux-alerta__texto')?.textContent)
      .toBe('Reative a conta em Contas, na seção "Arquivadas", para editar ou desfazer esta transferência.');
    expect(Array.from(leitura.querySelectorAll('button')).map((item) => item.textContent?.trim())).toEqual(['Fechar']);
  });
});