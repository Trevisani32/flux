import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { Conta, ListaDeContas } from '../../contas/contas.service';
import { Cartao } from '../cartoes.service';
import { CartaoDialogo, DadosDoDialogoDeCartao } from './cartao-dialogo';

function conta(id: number, nome: string, parcial: Partial<Conta> = {}): Conta {
  return { id, nome, tipo: 'CORRENTE', saldoInicial: 0, dataSaldoInicial: '2026-10-01', cor: null, saldoAtual: 0, arquivadaEm: null, ...parcial };
}

const contas: ListaDeContas = {
  ativas: [conta(1, 'Banco Horizonte'), conta(2, 'Carteira', { tipo: 'CARTEIRA' }), conta(3, 'Reserva', { tipo: 'INVESTIMENTO' })],
  arquivadas: [conta(4, 'Poupança antiga', { arquivadaEm: '2026-11-01T10:00:00Z' })],
  totais: { geral: 0, disponivel: 0, investimentos: 0 },
};

const horizonte: Cartao = {
  id: 7, nome: 'Cartão Horizonte', limite: 5000, diaFechamento: 3, diaVencimento: 10, contaPagamentoId: 4, contaPagamentoNome: 'Poupança antiga',
  contaPagamentoArquivada: true, cor: 'OURO', arquivadoEm: null,
  faturaAberta: { mes: '2026-12', inicio: '2026-11-04', fechamento: '2026-12-03', vencimento: '2026-12-10', total: 0, lancamentos: 0 },
  proximaFatura: { mes: '2027-01', inicio: '2026-12-04', fechamento: '2027-01-03', vencimento: '2027-01-10', total: 0, lancamentos: 0 },
  emAberto: 0, lancamentosEmAberto: 0,
};

describe('CartaoDialogo', () => {
  let http: HttpTestingController;
  let referencia: MatDialogRef<CartaoDialogo, Cartao>;

  function abrir(cartao: Cartao | null): void {
    referencia = TestBed.inject(MatDialog).open<CartaoDialogo, DadosDoDialogoDeCartao, Cartao>(CartaoDialogo, { data: { cartao, contas } });
    TestBed.tick();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.useRealTimers();
    try {
      http.verify();
    } finally {
      TestBed.inject(MatDialog).closeAll();
      TestBed.resetTestingModule();
    }
  });

  function painel(): HTMLElement {
    return document.querySelector('.mat-mdc-dialog-container') as HTMLElement;
  }

  function campo<T extends HTMLElement = HTMLInputElement>(seletor: string): T {
    return painel().querySelector<T>(seletor) as T;
  }

  function digitar(seletor: string, valor: string): void {
    const entrada = campo(seletor);
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input'));
    entrada.dispatchEvent(new Event('blur'));
    TestBed.tick();
  }

  function enviar(): void {
    painel().querySelector<HTMLButtonElement>('button[type="submit"]')?.click();
    TestBed.tick();
  }

  it('abre o Novo cartão com as contas que pagam fatura e a paleta das contas', () => {
    abrir(null);
    expect(painel().querySelector('h2')?.textContent).toBe('Novo cartão');
    const opcoes = Array.from(campo<HTMLSelectElement>('#cartao-conta').options).map((opcao) => opcao.textContent?.trim());
    expect(opcoes).toEqual(['Escolha', 'Banco Horizonte', 'Carteira']);
    expect(Array.from(painel().querySelectorAll<HTMLInputElement>('.flux-cor input')).map((radio) => radio.getAttribute('aria-label')))
      .toEqual(['Sem cor', 'Ouro', 'Laranja', 'Rosa', 'Roxo', 'Índigo', 'Azul', 'Ciano', 'Cinza']);
    expect(painel().querySelector('button[type="submit"]')?.textContent?.trim()).toBe('Salvar');
    expect(painel().querySelector('#cartao-dias-dica')).toBeNull();
    digitar('#cartao-fechamento', '3');
    digitar('#cartao-vencimento', '10');
    expect(painel().querySelector('#cartao-dias-dica')?.textContent)
      .toBe('A compra até o dia 3 entra na fatura que vence no dia 10 do mesmo mês; depois do dia 3, na fatura seguinte.');
    digitar('#cartao-fechamento', '25');
    digitar('#cartao-vencimento', '5');
    expect(painel().querySelector('#cartao-dias-dica')?.textContent)
      .toBe('A compra até o dia 25 entra na fatura que vence no dia 5 do mês seguinte; depois do dia 25, na fatura seguinte.');
  });

  it('mostra os erros da tela, inclusive os dias que empatam em fevereiro (decisão 3), sem chamar a API', () => {
    abrir(null);
    digitar('#cartao-limite', '0');
    digitar('#cartao-fechamento', '28');
    digitar('#cartao-vencimento', '30');
    enviar();
    expect(painel().querySelector('#cartao-nome-erro')?.textContent).toBe('Escreva o nome do cartão.');
    expect(painel().querySelector('#cartao-limite-erro')?.textContent).toBe('O limite precisa ser maior que zero.');
    expect(painel().querySelector('#cartao-vencimento-erro')?.textContent)
      .toBe('Com o fechamento no dia 28 ou depois, o vencimento precisa ser num dia antes dele, no mês seguinte.');
    expect(painel().querySelector('#cartao-conta-erro')?.textContent).toBe('Escolha a conta de pagamento.');
    digitar('#cartao-fechamento', '32');
    expect(painel().querySelector('#cartao-fechamento-erro')?.textContent).toBe('Escolha um dia de 1 a 31.');
  });

  it('cria o cartão com o limite lido e fecha com ele; o 422 do nome volta ao campo', async () => {
    abrir(null);
    digitar('#cartao-nome', '  Cartão Estrela ');
    digitar('#cartao-limite', '3.000');
    digitar('#cartao-fechamento', '25');
    digitar('#cartao-vencimento', '5');
    const select = campo<HTMLSelectElement>('#cartao-conta');
    select.value = select.options[1].value;
    select.dispatchEvent(new Event('change'));
    enviar();
    const pedido = http.expectOne({ method: 'POST', url: '/api/cartoes' });
    expect(pedido.request.body).toEqual({ nome: 'Cartão Estrela', limite: 3000, diaFechamento: 25, diaVencimento: 5, contaPagamentoId: 1, cor: null });
    pedido.flush({ codigo: 'NOME_EM_USO', detail: 'Já existe um cartão ativo com este nome.' }, { status: 422, statusText: 'Unprocessable' });
    TestBed.tick();
    expect(painel().querySelector('#cartao-nome-erro')?.textContent).toBe('Já existe um cartão ativo com este nome.');
    enviar();
    let fechou: Cartao | undefined;
    referencia.afterClosed().subscribe((salvo) => (fechou = salvo));
    http.expectOne({ method: 'POST', url: '/api/cartoes' }).flush(horizonte);
    TestBed.tick();
    await vi.waitFor(() => expect(fechou).toEqual(horizonte));
  });

  it('na edição, a conta arquivada que o cartão já tem fica, e mudar os dias mostra a partir de qual fatura valem (decisão 7)', () => {
    vi.useFakeTimers();
    abrir(horizonte);
    expect(painel().querySelector('h2')?.textContent).toBe('Editar cartão');
    expect(campo<HTMLSelectElement>('#cartao-conta').selectedOptions[0].textContent?.trim()).toBe('Poupança antiga (arquivada)');
    expect(campo('#cartao-limite').value).toBe('5.000,00');
    digitar('#cartao-fechamento', '13');
    digitar('#cartao-vencimento', '20');
    vi.advanceTimersByTime(300);
    const previa = http.expectOne({ method: 'POST', url: '/api/cartoes/7/previa-dos-dias' });
    expect(previa.request.body).toEqual({ diaFechamento: 13, diaVencimento: 20 });
    previa.flush({ troca: { abertaMes: '2026-12', abertaFechamento: '2026-12-03', primeiraMes: '2027-01', primeiraInicio: '2026-12-04',
      primeiraFechamento: '2027-01-13', primeiraVencimento: '2027-01-20' }, valemJa: false });
    TestBed.tick();
    expect(painel().querySelector('app-alerta .flux-alerta__titulo')?.textContent).toBe('Os dias novos valem a partir da fatura de janeiro.');
    expect(painel().querySelector('app-alerta .flux-alerta__texto')?.textContent).toBe('A de dezembro (aberta) continua fechando em 03/12. '
      + 'Janeiro passa a fechar em 13/01 e vencer em 20/01; as compras de 04/12 a 13/01 ficam nela.');
    // Só os dias pedem a prévia: mudar o nome não pede outra.
    digitar('#cartao-nome', 'Cartão Horizonte Black');
    vi.advanceTimersByTime(300);
    http.expectNone({ method: 'POST', url: '/api/cartoes/7/previa-dos-dias' });
    digitar('#cartao-fechamento', '3');
    digitar('#cartao-vencimento', '10');
    vi.advanceTimersByTime(300);
    TestBed.tick();
    expect(painel().querySelector('app-alerta')).toBeNull();
  });

  it('no cartão sem lançamento, os dias novos valem já, e o aviso diz isso (exceção da decisão 7)', () => {
    vi.useFakeTimers();
    abrir(horizonte);
    digitar('#cartao-fechamento', '13');
    digitar('#cartao-vencimento', '20');
    vi.advanceTimersByTime(300);
    http.expectOne({ method: 'POST', url: '/api/cartoes/7/previa-dos-dias' }).flush({ troca: null, valemJa: true });
    TestBed.tick();
    expect(painel().querySelector('app-alerta .flux-alerta__titulo')?.textContent).toBe('O cartão ainda não tem lançamentos: os dias novos valem já.');
    expect(painel().querySelector('app-alerta .flux-alerta__texto')).toBeNull();
  });
});
