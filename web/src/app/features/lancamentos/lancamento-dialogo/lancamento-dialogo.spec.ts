import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { Categoria, ListaDeCategorias } from '../../categorias/categorias.service';
import { Conta, ListaDeContas } from '../../contas/contas.service';
import { ItemDaLista, LancamentosService } from '../lancamentos.service';
import { abrirDialogoDeLancamento, contaPadrao, dataLimite, ResultadoDoLancamento } from './lancamento-dialogo';

function conta(id: number, nome: string, dataSaldoInicial: string, arquivadaEm: string | null = null): Conta {
  return { id, nome, tipo: 'CORRENTE', saldoInicial: 0, dataSaldoInicial, cor: null, saldoAtual: 0, arquivadaEm };
}

function categoria(id: number, nome: string, natureza: 'DESPESA' | 'RECEITA', subcategorias: Categoria[] = []): Categoria {
  return { id, nome, natureza, categoriaMaeId: null, nomeDaMae: null, arquivadaEm: null, subcategorias };
}

function item(parcial: Partial<ItemDaLista>): ItemDaLista {
  return {
    tipo: 'DESPESA', id: 10, data: '2026-10-20', descricao: 'Mensalidade', valor: -400, observacao: 'Parcela 3 de 6.',
    contaId: 1, contaNome: 'Banco Horizonte', categoriaId: 9, categoriaNome: 'Educação', categoriaMaeNome: null,
    categoriaArquivada: true, contaDestinoId: null, contaDestinoNome: null, direcao: null, futuro: false, somenteLeitura: false,
    ...parcial,
  };
}

const contas: ListaDeContas = {
  ativas: [conta(1, 'Banco Horizonte', '2026-10-01'), conta(2, 'Poupança', '2026-10-20')],
  arquivadas: [conta(3, 'Conta salário', '2026-09-01', '2026-11-03T15:00:00Z')],
  totais: { geral: 0, disponivel: 0, investimentos: 0 },
};

const categorias: ListaDeCategorias = {
  despesas: { ativas: [categoria(1, 'Alimentação', 'DESPESA', [categoria(2, 'Mercado', 'DESPESA')]), categoria(4, 'Lazer', 'DESPESA')], arquivadas: [] },
  receitas: { ativas: [categoria(5, 'Salário', 'RECEITA')], arquivadas: [] },
};

describe('LancamentoDialogo', () => {
  let httpTesting: HttpTestingController;
  let resultado: ResultadoDoLancamento | undefined | null;

  function abrir(itemAberto: ItemDaLista | null, padrao: number | null = 1): void {
    resultado = null;
    abrirDialogoDeLancamento(TestBed.inject(MatDialog), { item: itemAberto, hoje: '2026-11-09', contas, categorias, contaPadrao: padrao })
      .subscribe((valor) => (resultado = valor));
    TestBed.tick();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    TestBed.inject(MatDialog).closeAll();
  });

  function painel(): HTMLElement {
    return document.querySelector('app-lancamento-dialogo') as HTMLElement;
  }

  function campo<T extends HTMLElement>(seletor: string): T {
    return painel().querySelector<T>(seletor) as T;
  }

  function digitar(seletor: string, valor: string): void {
    const entrada = campo<HTMLInputElement>(seletor);
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input'));
    entrada.dispatchEvent(new Event('blur'));
    TestBed.tick();
  }

  function escolher(seletor: string, rotulo: string): void {
    const lista = campo<HTMLSelectElement>(seletor);
    const opcao = Array.from(lista.options).find((item) => item.textContent?.trim() === rotulo) as HTMLOptionElement;
    lista.value = opcao.value;
    lista.dispatchEvent(new Event('change'));
    TestBed.tick();
  }

  function marcarTipo(tipo: string): void {
    campo<HTMLInputElement>(`input[name="tipo"][value="${tipo}"]`).click();
    TestBed.tick();
  }

  function opcoes(seletor: string): string[] {
    return Array.from(campo<HTMLSelectElement>(seletor).options).map((opcao) => opcao.textContent ?? '');
  }

  function textoSelecionado(seletor: string): string {
    const lista = campo<HTMLSelectElement>(seletor);
    return lista.options[lista.selectedIndex]?.textContent?.trim() ?? '';
  }

  function botao(texto: string): HTMLButtonElement {
    return Array.from(document.querySelectorAll<HTMLButtonElement>('.mat-mdc-dialog-container button'))
      .find((item) => item.textContent?.trim() === texto) as HTMLButtonElement;
  }

  function erro(id: string): string | null {
    return painel().querySelector(`#${id}`)?.textContent ?? null;
  }

  it('abre como despesa e troca os campos ao escolher receita ou transferência', () => {
    abrir(null);
    expect(campo('h2').textContent).toBe('Novo lançamento');
    expect(campo<HTMLInputElement>('input[value="DESPESA"]').checked).toBe(true);
    expect(Array.from(painel().querySelectorAll<HTMLInputElement>('input[type="radio"]')).map((radio) => radio.name)).toEqual(['tipo', 'tipo', 'tipo']);
    expect(campo<HTMLInputElement>('#lancamento-valor').value).toBe('');
    expect(campo<HTMLInputElement>('#lancamento-data').value).toBe('2026-11-09');
    expect(campo<HTMLInputElement>('#lancamento-data').max).toBe('2027-11-09');
    expect(textoSelecionado('#lancamento-conta')).toBe('Banco Horizonte');
    expect(opcoes('#lancamento-categoria')).toEqual(['Sem categoria', 'Alimentação', '\u2003Mercado', 'Lazer']);
    expect(botao('Criar lançamento')?.type).toBe('submit');
    expect(botao('Salvar e criar outro')).toBeDefined();

    escolher('#lancamento-categoria', 'Lazer');
    marcarTipo('RECEITA');
    expect(opcoes('#lancamento-categoria')).toEqual(['Sem categoria', 'Salário']);
    expect(textoSelecionado('#lancamento-categoria')).toBe('Sem categoria');

    marcarTipo('TRANSFERENCIA');
    expect(campo('h2').textContent).toBe('Nova transferência');
    expect(campo('#lancamento-conta')).toBeNull();
    expect(textoSelecionado('#lancamento-origem')).toBe('Banco Horizonte');
    expect(textoSelecionado('#lancamento-destino')).toBe('Escolha');
    expect(campo<HTMLInputElement>('#lancamento-descricao').placeholder).toBe('Transferência');
    expect(botao('Criar transferência')).toBeDefined();
    expect(botao('Salvar e criar outra')).toBeDefined();
  });

  it('avisa os erros antes de enviar e não chama a API', () => {
    abrir(null);
    digitar('#lancamento-valor', '-10');
    expect(erro('lancamento-valor-erro')).toBe('Digite o valor sem sinal.');
    digitar('#lancamento-valor', '0');
    expect(erro('lancamento-valor-erro')).toBe('O valor precisa ser maior que zero.');
    digitar('#lancamento-data', '2026-09-20');
    expect(erro('lancamento-data-erro')).toBe('A data precisa ser 01/10/2026 ou depois, a data do saldo inicial de Banco Horizonte.');
    digitar('#lancamento-data', '2027-11-10');
    expect(erro('lancamento-data-erro')).toBe('A data pode ir até 09/11/2027.');
    digitar('#lancamento-data', '2026-11-15');
    expect(erro('lancamento-data-erro')).toBeNull();
    expect(erro('lancamento-data-dica')).toBe('Data futura: entra no saldo em 15/11/2026.');
    botao('Criar lançamento').click();
    TestBed.tick();
    expect(erro('lancamento-descricao-erro')).toBe('Informe a descrição.');

    marcarTipo('TRANSFERENCIA');
    escolher('#lancamento-destino', 'Banco Horizonte');
    botao('Criar transferência').click();
    TestBed.tick();
    expect(erro('lancamento-destino-erro')).toBe('Escolha uma conta diferente da de origem.');
    escolher('#lancamento-destino', 'Poupança');
    digitar('#lancamento-data', '2026-10-10');
    expect(erro('lancamento-data-erro')).toBe('A data precisa ser 20/10/2026 ou depois, a data do saldo inicial de Poupança.');
    expect(erro('lancamento-descricao-erro')).toBeNull();
    httpTesting.expectNone('/api/transferencias');
  });

  it('cria a despesa com o valor positivo e o tipo, e lembra a conta', async () => {
    abrir(null);
    digitar('#lancamento-valor', '287,43');
    expect(campo<HTMLInputElement>('#lancamento-valor').value).toBe('287,43');
    digitar('#lancamento-descricao', '  Mercado Bom Preço ');
    escolher('#lancamento-categoria', 'Mercado');
    digitar('#lancamento-observacao', '   ');
    botao('Criar lançamento').click();
    TestBed.tick();

    const pedido = httpTesting.expectOne({ method: 'POST', url: '/api/lancamentos' });
    expect(pedido.request.body).toEqual({
      tipo: 'DESPESA', valor: 287.43, data: '2026-11-09', descricao: 'Mercado Bom Preço', contaId: 1, categoriaId: 2, observacao: null,
    });
    pedido.flush(item({ id: 20 }));
    TestBed.tick();
    await vi.waitFor(() => expect(resultado).toBe('salvo'));
    expect(TestBed.inject(LancamentosService).ultimaConta).toBe(1);
  });

  it('cria a transferência sem descrição, e "Salvar e criar outra" mantém tipo, data e contas', async () => {
    abrir(null, 1);
    marcarTipo('TRANSFERENCIA');
    escolher('#lancamento-destino', 'Poupança');
    digitar('#lancamento-valor', '1.000');
    digitar('#lancamento-data', '2026-11-05');
    botao('Salvar e criar outra').click();
    TestBed.tick();

    const pedido = httpTesting.expectOne({ method: 'POST', url: '/api/transferencias' });
    expect(pedido.request.body).toEqual({ valor: 1000, data: '2026-11-05', contaOrigemId: 1, contaDestinoId: 2, descricao: null, observacao: null });
    pedido.flush(item({ tipo: 'TRANSFERENCIA', id: 4 }));
    TestBed.tick();

    expect(resultado).toBeNull();
    expect(campo('h2').textContent).toBe('Nova transferência');
    expect(campo<HTMLInputElement>('#lancamento-valor').value).toBe('');
    expect(campo<HTMLInputElement>('#lancamento-data').value).toBe('2026-11-05');
    expect(textoSelecionado('#lancamento-destino')).toBe('Poupança');
    expect(erro('lancamento-valor-erro')).toBeNull();
    botao('Cancelar').click();
    TestBed.tick();
    await vi.waitFor(() => expect(resultado).toBe('salvo'));
  });

  it('na edição, trava a troca para transferência e mantém a categoria arquivada', async () => {
    abrir(item({}));
    expect(campo('h2').textContent).toBe('Editar lançamento');
    expect(campo<HTMLInputElement>('input[value="TRANSFERENCIA"]').disabled).toBe(true);
    expect(campo<HTMLInputElement>('input[value="RECEITA"]').disabled).toBe(false);
    expect(campo('#lancamento-tipo-dica').textContent).toBe('Para virar transferência, exclua o lançamento e crie a transferência.');
    expect(campo<HTMLInputElement>('#lancamento-valor').value).toBe('400,00');
    expect(opcoes('#lancamento-categoria')[1]).toBe('Educação (arquivada)');
    expect(textoSelecionado('#lancamento-categoria')).toBe('Educação (arquivada)');
    expect(botao('Salvar e criar outro')).toBeUndefined();
    digitar('#lancamento-valor', '450');
    botao('Salvar').click();
    TestBed.tick();

    const pedido = httpTesting.expectOne({ method: 'PUT', url: '/api/lancamentos/10' });
    expect(pedido.request.body).toMatchObject({ tipo: 'DESPESA', valor: 450, categoriaId: 9, observacao: 'Parcela 3 de 6.' });
    pedido.flush(item({}));
    TestBed.tick();
    await vi.waitFor(() => expect(resultado).toBe('salvo'));
  });

  it('o lançamento de uma conta arquivada abre só para leitura', () => {
    abrir(item({ contaId: 3, contaNome: 'Conta salário', descricao: 'Tarifa do pacote', valor: -29.9, somenteLeitura: true }));
    expect(campo('h2').textContent).toBe('Tarifa do pacote');
    expect(campo('.flux-alerta__titulo').textContent).toBe('Conta salário está arquivada');
    expect(campo('form')).toBeNull();
    expect(Array.from(painel().querySelectorAll('dt')).map((dt) => dt.textContent)).toEqual(['Valor', 'Data', 'Conta', 'Categoria', 'Observação']);
    expect(Array.from(painel().querySelectorAll('button')).map((botaoDaFicha) => botaoDaFicha.textContent?.trim())).toEqual(['Fechar']);
  });

  it('põe os erros da API no campo ou no alerta', () => {
    abrir(null);
    digitar('#lancamento-valor', '10');
    digitar('#lancamento-descricao', 'Cinema');
    botao('Criar lançamento').click();
    TestBed.tick();
    httpTesting.expectOne('/api/lancamentos').flush({ campos: [{ campo: 'data', mensagem: 'A data pode ir até 09/11/2027.' }] },
      { status: 400, statusText: 'Bad Request' });
    TestBed.tick();
    expect(erro('lancamento-data-erro')).toBe('A data pode ir até 09/11/2027.');

    botao('Criar lançamento').click();
    TestBed.tick();
    httpTesting.expectOne('/api/lancamentos').flush({ detail: 'Escolha uma categoria ativa.', codigo: 'CATEGORIA_INVALIDA' },
      { status: 422, statusText: 'Unprocessable Entity' });
    TestBed.tick();
    expect(erro('lancamento-categoria-erro')).toBe('Escolha uma categoria ativa.');

    botao('Criar lançamento').click();
    TestBed.tick();
    httpTesting.expectOne('/api/lancamentos').flush({ detail: 'Banco Horizonte está arquivada. Reative a conta para lançar nela.', codigo: 'CONTA_ARQUIVADA' },
      { status: 422, statusText: 'Unprocessable Entity' });
    TestBed.tick();
    expect(painel().querySelector('[role="alert"]')?.textContent).toContain('Banco Horizonte está arquivada. Reative a conta para lançar nela.');
    expect(botao('Criar lançamento').disabled).toBe(false);
  });

  it('Excluir abre a confirmação por cima e fecha com "excluido"', async () => {
    abrir(item({ tipo: 'TRANSFERENCIA', id: 4, valor: 1000, descricao: 'Reserva do mês', data: '2026-11-05', categoriaId: null,
      categoriaNome: null, categoriaArquivada: false, contaDestinoId: 2, contaDestinoNome: 'Poupança' }));
    expect(campo('h2').textContent).toBe('Editar transferência');
    expect(campo<HTMLInputElement>('input[value="DESPESA"]').disabled).toBe(true);
    botao('Excluir').click();
    TestBed.tick();

    const confirmacao = document.querySelector('app-confirmacao-dialogo') as HTMLElement;
    expect(confirmacao.querySelector('h2')?.textContent).toBe('Excluir a transferência?');
    expect(confirmacao.querySelector('.confirmacao__texto')?.textContent?.replace(/\s/g, ' '))
      .toBe('Os dois lados saem do Flux: a saída de R$ 1.000,00 de Banco Horizonte e a entrada em Poupança, em 05/11/2026.');
    Array.from(confirmacao.querySelectorAll<HTMLButtonElement>('button')).find((item) => item.textContent?.trim() === 'Excluir transferência')?.click();
    TestBed.tick();
    httpTesting.expectOne({ method: 'DELETE', url: '/api/transferencias/4' }).flush(null);
    TestBed.tick();
    await vi.waitFor(() => expect(resultado).toBe('excluido'));
  });

  it('calcula a data limite e a conta padrão', () => {
    expect(dataLimite('2026-11-09')).toBe('2027-11-09');
    expect(dataLimite('2028-02-29')).toBe('2029-02-28');
    expect(contaPadrao(contas.ativas, 2, 1)).toBe(2);
    expect(contaPadrao(contas.ativas, 3, 1)).toBe(1);
    expect(contaPadrao(contas.ativas, null, null)).toBeNull();
    expect(contaPadrao([contas.ativas[0]], null, null)).toBe(1);
  });
});
