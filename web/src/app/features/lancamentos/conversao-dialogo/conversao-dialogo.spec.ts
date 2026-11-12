import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ListaDeCategorias } from '../../categorias/categorias.service';
import { Conta, ListaDeContas } from '../../contas/contas.service';
import { abrirDialogoDeLancamento, ResultadoDoLancamento } from '../lancamento-dialogo/lancamento-dialogo';
import { ItemDaLista, ParDoLancamento, ParesDoLancamento } from '../lancamentos.service';
import { abrirConversao } from './conversao-dialogo';

function conta(id: number, nome: string, dataSaldoInicial = '2026-10-01'): Conta {
  return { id, nome, tipo: 'CORRENTE', saldoInicial: 0, dataSaldoInicial, cor: null, saldoAtual: 0, arquivadaEm: null };
}

const contas: ListaDeContas = {
  ativas: [conta(1, 'Banco Horizonte'), conta(2, 'Conta conjunta'), conta(3, 'Carteira')],
  arquivadas: [],
  totais: { geral: 0, disponivel: 0, investimentos: 0 },
};

const categorias: ListaDeCategorias = { despesas: { ativas: [], arquivadas: [] }, receitas: { ativas: [], arquivadas: [] } };

function item(parcial: Partial<ItemDaLista>): ItemDaLista {
  return {
    tipo: 'DESPESA', id: 10, data: '2026-12-02', descricao: 'TED 341.0001DEV FICTICIO', valor: -1000, observacao: null, contaId: 1,
    contaNome: 'Banco Horizonte', categoriaId: null, categoriaNome: null, categoriaMaeNome: null, categoriaArquivada: false, contaDestinoId: null,
    contaDestinoNome: null, direcao: null, futuro: false, somenteLeitura: false, origem: 'IMPORTADO', descricaoBanco: 'TED 341.0001DEV FICTICIO',
    lados: null, ...parcial,
  };
}

function par(parcial: Partial<ParDoLancamento>): ParDoLancamento {
  return { id: 20, data: '2026-12-03', descricao: 'Transferência recebida - DEV FICTICIO', valor: 1000, contaId: 2, contaNome: 'Conta conjunta',
    origem: 'IMPORTADO', ...parcial };
}

function pares(lista: ParDoLancamento[], lancamento: ItemDaLista = item({})): ParesDoLancamento {
  return { lancamento, inicio: '2026-11-27', fim: '2026-12-07', pares: lista };
}

const doisPares = pares([par({}), par({ id: 21, data: '2026-12-06', descricao: 'Reserva', contaId: 3, contaNome: 'Carteira', origem: 'MANUAL' })]);
const criada = item({ tipo: 'TRANSFERENCIA', id: 40, valor: 1000, descricao: 'Transferência', contaDestinoId: 2, contaDestinoNome: 'Conta conjunta' });

describe('ConversaoDialogo', () => {
  let httpTesting: HttpTestingController;
  let resultado: ItemDaLista | undefined | null;

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

  function abrir(parSugerido: number | null = null, comContas: ListaDeContas = contas): void {
    resultado = null;
    abrirConversao(TestBed.inject(MatDialog), { lancamentoId: 10, contas: comContas, parSugerido }).subscribe((valor) => (resultado = valor));
    TestBed.tick();
  }

  function carregar(resposta: ParesDoLancamento): void {
    httpTesting.expectOne({ method: 'GET', url: '/api/lancamentos/10/pares' }).flush(resposta);
    TestBed.tick();
  }

  function painel(): HTMLElement {
    return document.querySelector('app-conversao-dialogo') as HTMLElement;
  }

  function textos(seletor: string): string[] {
    return Array.from(painel().querySelectorAll(seletor)).map((no) => no.textContent?.trim().replace(/\s+/g, ' ') ?? '');
  }

  function botao(texto: string, raiz: ParentNode = painel()): HTMLButtonElement {
    return Array.from(raiz.querySelectorAll<HTMLButtonElement>('button')).find((item) => item.textContent?.trim() === texto) as HTMLButtonElement;
  }

  function radios(): HTMLInputElement[] {
    return Array.from(painel().querySelectorAll<HTMLInputElement>('input[type="radio"]'));
  }

  function digitar(seletor: string, valor: string): void {
    const entrada = painel().querySelector(seletor) as HTMLInputElement;
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input'));
    TestBed.tick();
  }

  function escolher(seletor: string, rotulo: string): void {
    const lista = painel().querySelector(seletor) as HTMLSelectElement;
    lista.value = (Array.from(lista.options).find((opcao) => opcao.textContent?.trim() === rotulo) as HTMLOptionElement).value;
    lista.dispatchEvent(new Event('change'));
    TestBed.tick();
  }

  function alertaDeErro(): string | undefined {
    return painel().querySelector('[role="alert"]')?.textContent?.trim().replace(/\s+/g, ' ');
  }

  it('com pares: o importado fixo, os pares com a distância e a origem e o sugerido escolhido; Converter manda o par e a descrição', async () => {
    abrir(21);
    carregar(doisPares);

    expect(painel().querySelector('h2')?.textContent).toBe('Converter em transferência');
    expect(textos('.conversao__fixo b')).toEqual(['TED 341.0001DEV FICTICIO']);
    expect(textos('.conversao__fixo .conversao__meta')).toEqual(['Sai de Banco Horizonte em 02/12 · importado']);
    expect(textos('.conversao__legenda')).toEqual(['Entrou em']);
    expect(textos('.conversao__opcao b')).toEqual(['Transferência recebida - DEV FICTICIO', 'Reserva', 'Lançar a entrada em outra conta']);
    expect(textos('.conversao__opcao .conversao__meta')).toEqual(['Conta conjunta · 03/12, 1 dia depois · importado',
      'Carteira · 06/12, 4 dias depois · lançado à mão', 'Para conta sem extrato: carteira, investimento']);
    expect(radios().map((radio) => radio.name)).toEqual(['outroLado', 'outroLado', 'outroLado']);
    expect(radios().every((radio) => radio.closest('label') !== null)).toBe(true);
    expect(radios().map((radio) => radio.checked)).toEqual([false, true, false]);
    expect(painel().querySelector('#conversao-conta')).toBeNull();
    expect(painel().querySelector('label[for="conversao-descricao"]')?.textContent?.trim()).toBe('Descrição (opcional)');

    digitar('#conversao-descricao', '  Reserva para a viagem ');
    botao('Converter').click();
    TestBed.tick();
    const pedido = httpTesting.expectOne({ method: 'POST', url: '/api/lancamentos/10/converter' });
    expect(pedido.request.body).toEqual({ parId: 21, contaId: null, descricao: 'Reserva para a viagem' });
    pedido.flush(criada);
    TestBed.tick();
    await vi.waitFor(() => expect(resultado).toEqual(criada));
    await vi.waitFor(() => expect(document.querySelector('.mat-mdc-snack-bar-container')?.textContent).toContain('Transferência criada.'));
  });

  it('com dois pares e nenhum sugerido, nada vem escolhido e Converter pede o outro lado; com um par só, ele vem escolhido', async () => {
    abrir(99);
    carregar(doisPares);
    expect(radios().map((radio) => radio.checked)).toEqual([false, false, false]);
    botao('Converter').click();
    TestBed.tick();
    expect(textos('#conversao-outro-lado-erro')).toEqual(['Escolha o outro lado da transferência.']);
    expect(painel().querySelector('fieldset')?.getAttribute('aria-describedby')).toBe('conversao-outro-lado-erro');
    httpTesting.expectNone({ method: 'POST', url: '/api/lancamentos/10/converter' });
    radios()[0].click();
    TestBed.tick();
    expect(painel().querySelector('#conversao-outro-lado-erro')).toBeNull();

    TestBed.inject(MatDialog).closeAll();
    await vi.waitFor(() => expect(painel()).toBeNull());
    abrir();
    carregar(pares([par({})]));
    expect(radios().map((radio) => radio.checked)).toEqual([true, false]);
  });

  it('sem par, o lado novo vem escolhido; a conta é pedida antes de enviar e vai como contaId (a receita importada pede a saída)', async () => {
    abrir();
    carregar(pares([], item({ tipo: 'RECEITA', valor: 250, descricao: 'PIX RECEBIDO', descricaoBanco: 'PIX RECEBIDO' })));

    expect(textos('.conversao__legenda')).toEqual(['Saiu de']);
    expect(textos('.conversao__fixo .conversao__meta')).toEqual(['Entra em Banco Horizonte em 02/12 · importado']);
    expect(textos('.conversao__sem-par')).toEqual(['Nenhuma saída de R$ 250,00 em outra conta entre 27/11 e 07/12.']);
    expect(textos('.conversao__opcao b')).toEqual(['Lançar a saída em outra conta']);
    expect(textos('.conversao__opcao .conversao__meta')).toEqual(['Uma despesa de R$ 250,00 em 02/12, ligada a esta entrada']);
    expect(radios().map((radio) => radio.checked)).toEqual([true]);
    expect(painel().querySelector('label[for="conversao-conta"]')?.textContent).toBe('Conta da saída');
    expect(Array.from((painel().querySelector('#conversao-conta') as HTMLSelectElement).options).map((opcao) => opcao.textContent?.trim()))
      .toEqual(['Escolha a conta', 'Conta conjunta', 'Carteira']);

    botao('Converter').click();
    TestBed.tick();
    expect(textos('#conversao-conta-erro')).toEqual(['Escolha a conta.']);
    httpTesting.expectNone({ method: 'POST', url: '/api/lancamentos/10/converter' });
    escolher('#conversao-conta', 'Carteira');
    expect(painel().querySelector('#conversao-conta-erro')).toBeNull();
    botao('Converter').click();
    TestBed.tick();
    const pedido = httpTesting.expectOne({ method: 'POST', url: '/api/lancamentos/10/converter' });
    expect(pedido.request.body).toEqual({ parId: null, contaId: 3, descricao: null });
    pedido.flush(criada);
    await vi.waitFor(() => expect(resultado).toEqual(criada));
  });

  it('a conta que começou depois da data do importado vem desabilitada, com o motivo; sem nenhuma que sirva, a tela diz e não há Converter',
    async () => {
      abrir(null, { ...contas, ativas: [conta(1, 'Banco Horizonte'), conta(2, 'Conta conjunta'), conta(3, 'Carteira', '2026-12-05')] });
      carregar(pares([]));
      expect(radios().map((radio) => radio.checked)).toEqual([true]);
      const opcoes = Array.from((painel().querySelector('#conversao-conta') as HTMLSelectElement).options);
      expect(opcoes.map((opcao) => opcao.textContent?.trim())).toEqual(['Escolha a conta', 'Conta conjunta', 'Carteira (começa em 05/12/2026)']);
      expect(opcoes.map((opcao) => opcao.disabled)).toEqual([true, false, true]);
      expect(painel().querySelector('#conversao-descricao')).not.toBeNull();

      TestBed.inject(MatDialog).closeAll();
      await vi.waitFor(() => expect(painel()).toBeNull());
      abrir(null, { ...contas, ativas: [conta(1, 'Banco Horizonte'), conta(3, 'Carteira', '2026-12-05')] });
      carregar(pares([]));
      expect(radios().map((radio) => [radio.checked, radio.disabled])).toEqual([[false, true]]);
      expect(textos('.conversao__opcao .conversao__meta')).toEqual(['Nenhuma outra conta ativa existia em 02/12/2026: a conta do outro lado '
        + 'precisa ter o saldo inicial até essa data.']);
      expect(botao('Converter')).toBeUndefined();
      expect(botao('Cancelar')).toBeDefined();
      // Sem o que converter, nem a descrição nem a nota da categoria aparecem, e o envio do formulário (o Enter)
      // não mostra erro nem manda pedido.
      expect(painel().querySelector('#conversao-descricao')).toBeNull();
      expect(painel().querySelector('.conversao__nota')).toBeNull();
      painel().querySelector('form')?.dispatchEvent(new Event('submit', { cancelable: true }));
      TestBed.tick();
      expect(painel().querySelector('.flux-campo__erro')).toBeNull();
      httpTesting.expectNone({ method: 'POST', url: '/api/lancamentos/10/converter' });
    });

  it('o 422 da conta do lado novo vai para o select da conta, mantém a escolha e não recarrega os pares', () => {
    abrir(null, { ...contas, ativas: [...contas.ativas, conta(4, 'Investimento')] });
    carregar(pares([]));
    escolher('#conversao-conta', 'Carteira');
    botao('Converter').click();
    TestBed.tick();
    httpTesting.expectOne({ method: 'POST', url: '/api/lancamentos/10/converter' }).flush({ detail: 'Carteira está arquivada. Reative a conta '
      + 'para lançar nela.', codigo: 'CONTA_ARQUIVADA', campo: 'contaId' }, { status: 422, statusText: 'Erro' });
    TestBed.tick();

    expect(textos('#conversao-conta-erro')).toEqual(['Carteira está arquivada. Reative a conta para lançar nela.']);
    expect(painel().querySelector('#conversao-conta')?.getAttribute('aria-invalid')).toBe('true');
    expect(radios().map((radio) => radio.checked)).toEqual([true]);
    const select = painel().querySelector('#conversao-conta') as HTMLSelectElement;
    expect(select.options[select.selectedIndex]?.textContent?.trim()).toBe('Carteira');
    expect(painel().querySelector('[role="alert"]')).toBeNull();
    httpTesting.expectNone({ method: 'GET', url: '/api/lancamentos/10/pares' });

    escolher('#conversao-conta', 'Investimento');
    expect(painel().querySelector('#conversao-conta-erro')).toBeNull();
    botao('Converter').click();
    TestBed.tick();
    httpTesting.expectOne({ method: 'POST', url: '/api/lancamentos/10/converter' }).flush({ detail: 'Você chegou ao limite de 1.000 '
      + 'lançamentos em dezembro de 2026.', codigo: 'LIMITE_DE_LANCAMENTOS', campo: 'contaId' }, { status: 422, statusText: 'Erro' });
    TestBed.tick();
    expect(textos('#conversao-conta-erro')).toEqual(['Você chegou ao limite de 1.000 lançamentos em dezembro de 2026.']);
    expect(painel().textContent).not.toContain('A lista foi atualizada');
    httpTesting.expectNone({ method: 'GET', url: '/api/lancamentos/10/pares' });
  });

  it('o 422 ao converter mostra a mensagem e recarrega os pares, sem a lista velha e sem escolher nada', () => {
    abrir();
    carregar(pares([par({})]));
    botao('Converter').click();
    TestBed.tick();
    httpTesting.expectOne({ method: 'POST', url: '/api/lancamentos/10/converter' }).flush(
      { detail: 'O lançamento escolhido já está numa transferência.', codigo: 'PAR_INVALIDO' }, { status: 422, statusText: 'Erro' });
    TestBed.tick();

    expect(radios()).toHaveLength(0);
    expect(textos('.conversao__carregando')).toEqual(['Carregando os pares…']);
    expect(botao('Converter')).toBeUndefined();
    expect(alertaDeErro()).toBe('O lançamento escolhido já está numa transferência.');
    carregar(pares([par({ id: 22, descricao: 'Outra entrada' })]));
    expect(textos('.conversao__opcao b')).toEqual(['Outra entrada', 'Lançar a entrada em outra conta']);
    expect(radios().map((radio) => radio.checked)).toEqual([false, false]);
    expect(alertaDeErro()).toBe('O lançamento escolhido já está numa transferência.A lista foi atualizada. Confira o outro lado e converta de novo.');
  });

  it('se a recarga depois do erro falhar, fica só o alerta dela, e "Tentar de novo" pede de novo', () => {
    abrir();
    carregar(pares([par({})]));
    botao('Converter').click();
    TestBed.tick();
    httpTesting.expectOne({ method: 'POST', url: '/api/lancamentos/10/converter' }).flush(
      { detail: 'Conta conjunta está arquivada. Reative a conta para converter este lançamento.', codigo: 'CONTA_ARQUIVADA' },
      { status: 422, statusText: 'Erro' });
    TestBed.tick();
    httpTesting.expectOne({ method: 'GET', url: '/api/lancamentos/10/pares' }).flush(null, { status: 500, statusText: 'Erro' });
    TestBed.tick();

    expect(textos('[role="alert"] .flux-alerta__titulo')).toEqual(['Não foi possível carregar os pares.']);
    expect(painel().textContent).not.toContain('Conta conjunta está arquivada');
    expect(botao('Converter')).toBeUndefined();
    botao('Tentar de novo').click();
    TestBed.tick();
    carregar(doisPares);
    expect(radios()).toHaveLength(3);
    expect(painel().querySelector('[role="alert"]')).toBeNull();
  });

  it('o 422 ao carregar mostra a mensagem, sem "Tentar de novo" nem Converter; "Voltar" e "Cancelar" fecham sem nada', async () => {
    abrir();
    httpTesting.expectOne({ method: 'GET', url: '/api/lancamentos/10/pares' }).flush(
      { detail: 'Este lançamento já está numa transferência.', codigo: 'JA_EM_TRANSFERENCIA' }, { status: 422, statusText: 'Erro' });
    TestBed.tick();
    expect(textos('[role="alert"] .flux-alerta__texto')).toEqual(['Este lançamento já está numa transferência.']);
    expect(botao('Tentar de novo')).toBeUndefined();
    expect(botao('Converter')).toBeUndefined();
    (painel().querySelector('button[aria-label="Voltar"]') as HTMLButtonElement).click();
    TestBed.tick();
    await vi.waitFor(() => expect(resultado).toBeUndefined());

    abrir();
    carregar(doisPares);
    botao('Cancelar').click();
    TestBed.tick();
    await vi.waitFor(() => expect(resultado).toBeUndefined());
    httpTesting.expectNone({ method: 'POST', url: '/api/lancamentos/10/converter' });
  });

  it('dois cliques em Converter mandam um pedido só e seguram o Esc; o erro de rede não recarrega, e o 400 vai para o campo', () => {
    abrir();
    carregar(pares([par({})]));
    botao('Converter').click();
    botao('Converter').click();
    TestBed.tick();
    const pedido = httpTesting.expectOne({ method: 'POST', url: '/api/lancamentos/10/converter' });
    expect(botao('Converter').disabled).toBe(true);
    expect(botao('Cancelar').disabled).toBe(true);
    expect((painel().querySelector('button[aria-label="Voltar"]') as HTMLButtonElement).disabled).toBe(true);
    expect(TestBed.inject(MatDialog).openDialogs[0].disableClose).toBe(true);

    pedido.flush(null, { status: 500, statusText: 'Erro' });
    TestBed.tick();
    expect(alertaDeErro()).toBe('Não foi possível converter. Tente novamente.');
    httpTesting.expectNone({ method: 'GET', url: '/api/lancamentos/10/pares' });
    expect(radios().map((radio) => radio.checked)).toEqual([true, false]);
    expect(TestBed.inject(MatDialog).openDialogs[0].disableClose).toBe(false);

    botao('Converter').click();
    TestBed.tick();
    httpTesting.expectOne({ method: 'POST', url: '/api/lancamentos/10/converter' }).flush(
      { campos: [{ campo: 'descricao', mensagem: 'Use no máximo 255 caracteres.' }] }, { status: 400, statusText: 'Erro' });
    TestBed.tick();
    expect(textos('#conversao-descricao-erro')).toEqual(['Use no máximo 255 caracteres.']);
    expect(painel().querySelector('[role="alert"]')).toBeNull();
    digitar('#conversao-descricao', 'Reserva');
    expect(painel().querySelector('#conversao-descricao-erro')).toBeNull();
  });

  it('abre pelo atalho do importado; "Cancelar" volta ao diálogo do lançamento, e convertido ele fecha com "convertido"', async () => {
    let doLancamento: ResultadoDoLancamento | undefined | null = null;
    abrirDialogoDeLancamento(TestBed.inject(MatDialog), { item: item({}), hoje: '2026-12-10', contas, categorias, contaPadrao: 1 })
      .subscribe((valor) => (doLancamento = valor));
    TestBed.tick();
    const lancamento = document.querySelector('app-lancamento-dialogo') as HTMLElement;
    expect(lancamento.querySelector('.flux-alerta__texto')?.textContent)
      .toBe('Tipo, valor, data e conta vêm do extrato e não mudam. Se é dinheiro indo para outra conta sua:');

    botao('Converter em transferência', lancamento).click();
    TestBed.tick();
    carregar(pares([par({})]));
    botao('Cancelar').click();
    TestBed.tick();
    await vi.waitFor(() => expect(document.querySelector('app-conversao-dialogo')).toBeNull());
    expect(document.querySelector('app-lancamento-dialogo')).not.toBeNull();
    expect(doLancamento).toBeNull();

    botao('Converter em transferência', lancamento).click();
    TestBed.tick();
    carregar(pares([par({})]));
    botao('Converter').click();
    TestBed.tick();
    httpTesting.expectOne({ method: 'POST', url: '/api/lancamentos/10/converter' }).flush(criada);
    await vi.waitFor(() => expect(doLancamento).toBe('convertido'));
  });

  it('o atalho fala da entrada na receita importada e não aparece no lançamento manual', async () => {
    abrirDialogoDeLancamento(TestBed.inject(MatDialog), { item: item({ tipo: 'RECEITA', valor: 250 }), hoje: '2026-12-10', contas, categorias,
      contaPadrao: 1 }).subscribe();
    TestBed.tick();
    expect(document.querySelector('app-lancamento-dialogo .flux-alerta__texto')?.textContent)
      .toBe('Tipo, valor, data e conta vêm do extrato e não mudam. Se é dinheiro vindo de outra conta sua:');
    TestBed.inject(MatDialog).closeAll();
    await vi.waitFor(() => expect(document.querySelector('app-lancamento-dialogo')).toBeNull());

    abrirDialogoDeLancamento(TestBed.inject(MatDialog), { item: item({ origem: 'MANUAL', descricaoBanco: null }), hoje: '2026-12-10', contas,
      categorias, contaPadrao: 1 }).subscribe();
    TestBed.tick();
    expect(botao('Converter em transferência', document.querySelector('app-lancamento-dialogo') as HTMLElement)).toBeUndefined();
  });
});