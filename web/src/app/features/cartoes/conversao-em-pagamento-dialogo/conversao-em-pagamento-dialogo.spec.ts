import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ItemDaLista } from '../../lancamentos/lancamentos.service';
import { FaturasParaPagamento, Pagamento } from '../pagamentos.service';
import { abrirConversaoEmPagamento } from './conversao-em-pagamento-dialogo';

const importado: ItemDaLista = {
  tipo: 'DESPESA', id: 40, data: '2026-11-10', descricao: 'Pag fatura', valor: -265.2, observacao: null, contaId: 1, contaNome: 'Banco Horizonte',
  categoriaId: null, categoriaNome: null, categoriaMaeNome: null, categoriaArquivada: false, contaDestinoId: null, contaDestinoNome: null,
  direcao: null, futuro: false, somenteLeitura: false, origem: 'IMPORTADO', descricaoBanco: 'PAG FATURA CARTAO HORIZONTE',
};

/** Como a API ordena: a sugerida primeiro (fechada, pendente igual ao valor, vencimento perto da data). */
const opcoes: FaturasParaPagamento = {
  lancamento: importado,
  cartoes: [
    { id: 8, nome: 'Cartão Lua', cor: null, faturas: [{ mes: '2026-12', situacao: 'ABERTA', vencimento: '2026-12-05', total: 245.8, pendente: 245.8,
      sugerida: false }] },
    { id: 7, nome: 'Cartão Horizonte', cor: 'OURO', faturas: [
      { mes: '2026-11', situacao: 'FECHADA', vencimento: '2026-11-10', total: 265.2, pendente: 265.2, sugerida: true },
      { mes: '2026-12', situacao: 'ABERTA', vencimento: '2026-12-10', total: 1113.69, pendente: 1113.69, sugerida: false },
    ] },
  ],
};

describe('ConversaoEmPagamentoDialogo', () => {
  let http: HttpTestingController;
  let resultado: Pagamento | undefined | null;
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

  function abrir(resposta: FaturasParaPagamento = opcoes): void {
    resultado = null;
    abrirConversaoEmPagamento(TestBed.inject(MatDialog), { lancamentoId: 40 }).subscribe((valor) => (resultado = valor));
    TestBed.tick();
    http.expectOne('/api/lancamentos/40/faturas-para-pagamento').flush(resposta);
    TestBed.tick();
  }

  function painel(): HTMLElement {
    return document.querySelector('app-conversao-em-pagamento-dialogo') as HTMLElement;
  }

  function textos(seletor: string): string[] {
    return Array.from(painel().querySelectorAll(seletor)).map((item) => (item.textContent ?? '').trim().replace(/\s+/g, ' '));
  }

  function converter(): void {
    Array.from(painel().querySelectorAll<HTMLButtonElement>('button')).find((item) => item.textContent?.trim() === 'Converter')?.click();
    TestBed.tick();
  }

  it('mostra a despesa e escolhe o cartão com a fatura sugerida e a fatura dele (decisão 37)', () => {
    abrir();
    expect(textos('h2')).toEqual(['Converter em pagamento de fatura']);
    expect(textos('.conversao-pagamento__lado .conversao-pagamento__textos')).toEqual(['PAG FATURA CARTAO HORIZONTEBanco Horizonte · 10/11 · importado']);
    expect((painel().querySelector('#conversao-pagamento-cartao') as HTMLSelectElement).selectedIndex).toBe(1);
    expect(textos('.conversao-pagamento__opcao .conversao-pagamento__textos')).toEqual([
      'Novembro de 2026Fechada · vence 10/11 · pendente igual ao valor', 'Dezembro de 2026Aberta · vence 10/12']);
    const radios = Array.from(painel().querySelectorAll<HTMLInputElement>('input[type="radio"]'));
    expect(radios.map((radio) => radio.checked)).toEqual([true, false]);
    expect(textos('.flux-dica')).toEqual(['Deixa de ser despesa e perde a categoria. Data, valor e conta não mudam, e reimportar o extrato continua '
      + 'reconhecendo o lançamento.']);
  });

  it('trocar o cartão escolhe a primeira fatura dele; converter manda o cartão e o mês e fecha com o pagamento', async () => {
    abrir();
    const select = painel().querySelector('#conversao-pagamento-cartao') as HTMLSelectElement;
    select.value = select.options[0].value;
    select.dispatchEvent(new Event('change'));
    TestBed.tick();
    expect(textos('.conversao-pagamento__opcao b')).toEqual(['Dezembro de 2026']);
    select.value = select.options[1].value;
    select.dispatchEvent(new Event('change'));
    TestBed.tick();
    painel().querySelectorAll<HTMLInputElement>('input[type="radio"]')[1].click();
    TestBed.tick();
    converter();
    const pedido = http.expectOne('/api/lancamentos/40/converter-em-pagamento');
    expect(pedido.request.body).toEqual({ cartaoId: 7, mes: '2026-12' });
    pedido.flush({ id: 40, faturaMes: '2026-12' } as Pagamento);
    await vi.waitFor(() => expect(resultado).toEqual({ id: 40, faturaMes: '2026-12' }));
    expect(snackBar.open).toHaveBeenCalledWith('Convertido: agora é o pagamento da fatura de dezembro de 2026.', 'Fechar', { duration: 5000 });
  });

  it('o 422 do mês fica embaixo das faturas', () => {
    abrir();
    converter();
    http.expectOne('/api/lancamentos/40/converter-em-pagamento').flush({ detail: 'Escolha uma fatura deste cartão que já fechou ou está aberta.',
      campo: 'mes' }, { status: 422, statusText: 'Unprocessable Entity' });
    TestBed.tick();
    expect(textos('#conversao-pagamento-fatura-erro')).toEqual(['Escolha uma fatura deste cartão que já fechou ou está aberta.']);
    expect(resultado).toBeNull();
  });

  it('a recusa da API ao carregar não oferece "Tentar de novo"; a falha de rede, sim (Correções do QA)', () => {
    abrirConversaoEmPagamento(TestBed.inject(MatDialog), { lancamentoId: 41 }).subscribe();
    TestBed.tick();
    http.expectOne('/api/lancamentos/41/faturas-para-pagamento').flush({ codigo: 'DATA_FUTURA',
      detail: 'Este lançamento tem data futura: ele só vira pagamento de fatura quando a data chegar.' }, { status: 422, statusText: 'Unprocessable' });
    TestBed.tick();
    expect(textos('app-alerta .flux-alerta__titulo')).toEqual(['Este lançamento tem data futura: ele só vira pagamento de fatura quando a data chegar.']);
    expect(textos('button')).not.toContain('Tentar de novo');
    TestBed.inject(MatDialog).closeAll();
    abrirConversaoEmPagamento(TestBed.inject(MatDialog), { lancamentoId: 42 }).subscribe();
    TestBed.tick();
    http.expectOne('/api/lancamentos/42/faturas-para-pagamento').flush(null, { status: 500, statusText: 'Server Error' });
    TestBed.tick();
    const ultimo = Array.from(document.querySelectorAll('app-conversao-em-pagamento-dialogo')).at(-1) as HTMLElement;
    expect(ultimo.querySelector('app-alerta .flux-alerta__titulo')?.textContent?.trim()).toBe('Não foi possível carregar as faturas.');
    expect(Array.from(ultimo.querySelectorAll('button')).map((item) => item.textContent?.trim())).toContain('Tentar de novo');
  });

  it('sem cartão ativo, explica e só fecha', () => {
    abrir({ lancamento: importado, cartoes: [] });
    expect(textos('app-alerta .flux-alerta__titulo')).toEqual(['Nenhum cartão ativo']);
    expect(textos('.conversao-pagamento__acoes button')).toEqual(['Fechar']);
  });
});
