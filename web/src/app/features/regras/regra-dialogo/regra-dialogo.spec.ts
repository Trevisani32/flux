import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { Categoria, ListaDeCategorias } from '../../categorias/categorias.service';
import { Conta, ListaDeContas } from '../../contas/contas.service';
import { Regra } from '../regras.service';
import { abrirDialogoDeRegra, DadosDoDialogoDeRegra, ResultadoDaRegra } from './regra-dialogo';

function conta(id: number, nome: string, arquivadaEm: string | null = null): Conta {
  return { id, nome, tipo: 'CORRENTE', saldoInicial: 0, dataSaldoInicial: '2026-10-01', cor: null, saldoAtual: 0, arquivadaEm };
}

function categoria(id: number, nome: string, natureza: 'DESPESA' | 'RECEITA', subcategorias: Categoria[] = []): Categoria {
  return { id, nome, natureza, categoriaMaeId: null, nomeDaMae: null, arquivadaEm: null, subcategorias };
}

function regra(parcial: Partial<Regra>): Regra {
  return { id: 30, posicao: 1, condicao: 'CONTEM', texto: 'uber', contaId: null, contaNome: null, contaArquivada: false, natureza: 'DESPESA',
    categoriaId: 5, categoriaNome: 'App de transporte', categoriaMaeNome: 'Transporte', categoriaArquivada: false, descricao: null,
    pausada: false, ...parcial };
}

const contas: ListaDeContas = {
  ativas: [conta(1, 'Banco Horizonte'), conta(2, 'Poupança')],
  arquivadas: [conta(3, 'Poupança antiga', '2026-11-03T15:00:00Z')],
  totais: { geral: 0, disponivel: 0, investimentos: 0 },
};

const categorias: ListaDeCategorias = {
  despesas: { ativas: [categoria(1, 'Alimentação', 'DESPESA', [categoria(2, 'Padaria', 'DESPESA')]),
    categoria(4, 'Transporte', 'DESPESA', [categoria(5, 'App de transporte', 'DESPESA')])], arquivadas: [] },
  receitas: { ativas: [categoria(7, 'Salário', 'RECEITA')], arquivadas: [] },
};

describe('RegraDialogo', () => {
  let httpTesting: HttpTestingController;
  let resultado: ResultadoDaRegra | undefined | null;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    TestBed.inject(MatDialog).closeAll();
  });

  /** Abre o diálogo; na Nova regra, a lista de regras serve ao aviso da posição (decisão 10). */
  function abrir(dados: DadosDoDialogoDeRegra, regras: Regra[] = []): void {
    resultado = null;
    abrirDialogoDeRegra(TestBed.inject(MatDialog), dados).subscribe((valor) => (resultado = valor));
    TestBed.tick();
    httpTesting.expectOne('/api/contas').flush(contas);
    httpTesting.expectOne('/api/categorias').flush(categorias);
    if (dados.regra === null) {
      httpTesting.expectOne({ method: 'GET', url: '/api/regras' }).flush(regras);
    }
    TestBed.tick();
  }

  function painel(): HTMLElement {
    return document.querySelector('app-regra-dialogo') as HTMLElement;
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

  function textoSelecionado(seletor: string): string {
    const lista = campo<HTMLSelectElement>(seletor);
    return lista.options[lista.selectedIndex]?.textContent?.trim() ?? '';
  }

  function opcoes(seletor: string): string[] {
    return Array.from(campo<HTMLSelectElement>(seletor).options).map((opcao) => opcao.textContent?.trim() ?? '');
  }

  function botao(texto: string): HTMLButtonElement {
    return Array.from(document.querySelectorAll<HTMLButtonElement>('.mat-mdc-dialog-container button'))
      .find((item) => item.textContent?.trim() === texto) as HTMLButtonElement;
  }

  function erro(id: string): string | null {
    return painel().querySelector(`#${id}`)?.textContent ?? null;
  }

  function tituloDaPrevia(): string {
    return painel().querySelector('.regra-dialogo__titulo-previa')?.textContent?.trim() ?? '';
  }

  /** A prévia sai 300 ms depois da última mudança (decisão 12). */
  function previa(): Promise<TestRequest> {
    return vi.waitFor(() => httpTesting.expectOne({ method: 'POST', url: '/api/regras/previa' }), { timeout: 2000 });
  }

  it('a Nova regra vinda de "Criar regra" vem preenchida, mostra a prévia e cria com dois cliques num pedido só (decisões 10, 12 e 21)', async () => {
    abrir({ regra: null, sugestao: { condicao: 'CONTEM', texto: 'PIX QRS PADARIA', categoriaId: 2, categoriaNome: 'Padaria',
      categoriaMaeNome: 'Alimentação', natureza: 'DESPESA', descricao: 'Padaria Central', deDescricao: 'PIX QRS PADARIA CE03/12' } },
    [regra({ texto: 'uber eats' }), regra({ id: 31, posicao: 2 })]);
    expect(painel().querySelector('h2')?.textContent).toBe('Nova regra');
    expect(painel().querySelector('.regra-dialogo__texto')?.textContent)
      .toBe('Sugerida a partir de PIX QRS PADARIA CE03/12, que você pôs em Alimentação › Padaria.');
    expect(textoSelecionado('#regra-condicao')).toBe('contém');
    expect(campo<HTMLInputElement>('#regra-texto').value).toBe('PIX QRS PADARIA');
    expect(erro('regra-texto-dica')).toBe('Maiúsculas, acentos e pontuação não contam. No extrato: PIX QRS PADARIA CE03/12');
    expect(textoSelecionado('#regra-categoria')).toBe('Padaria');
    expect(erro('regra-categoria-dica')).toBe('Categoria de despesa: a regra só pega despesas.');
    expect(opcoes('#regra-conta')).toEqual(['Todas as contas', 'Banco Horizonte', 'Poupança']);
    expect(campo<HTMLInputElement>('#regra-descricao').value).toBe('Padaria Central');
    expect(painel().querySelector('.regra-dialogo__posicao')?.textContent)
      .toBe('Entra em 1º lugar, antes de "contém uber eats". Dá para mudar a ordem na lista.');
    expect(Array.from(painel().querySelectorAll('input, select')).every((item) => (item as HTMLInputElement).labels?.length === 1)).toBe(true);

    const pedidoDaPrevia = await previa();
    expect(pedidoDaPrevia.request.body).toEqual({ condicao: 'CONTEM', texto: 'PIX QRS PADARIA', contaId: null, categoriaId: 2 });
    pedidoDaPrevia.flush({ casam: 6, semCategoria: 4, exemplos: [{ data: '2026-12-03', descricao: 'PIX QRS PADARIA CE03/12', valor: -12.5 }] });
    TestBed.tick();
    expect(tituloDaPrevia()).toBe('Casa com 6 lançamentos seus, 4 sem categoria');
    expect(painel().querySelector('.regra-dialogo__exemplo')?.textContent).toBe('PIX QRS PADARIA CE03/12');

    botao('Criar regra').click();
    botao('Criar regra').click();
    TestBed.tick();
    const pedido = httpTesting.expectOne({ method: 'POST', url: '/api/regras' });
    expect(pedido.request.body).toEqual({ condicao: 'CONTEM', texto: 'PIX QRS PADARIA', contaId: null, categoriaId: 2, descricao: 'Padaria Central' });
    pedido.flush(regra({ id: 40 }));
    TestBed.tick();
    await vi.waitFor(() => expect(resultado).toEqual({ tipo: 'criada', regra: regra({ id: 40 }) }));
  });

  it('avisa os erros antes de enviar, sem chamar a API, e Cancelar fecha sem resultado', async () => {
    abrir({ regra: null, sugestao: null });
    expect(tituloDaPrevia()).toBe('Escreva o texto para ver quantos lançamentos ela pega.');
    expect(painel().querySelector('.regra-dialogo__posicao')?.textContent).toBe('Entra em 1º lugar. Dá para mudar a ordem na lista.');
    expect(erro('regra-categoria-dica')).toBe('A categoria diz se a regra pega despesas ou receitas.');
    botao('Criar regra').click();
    TestBed.tick();
    expect(erro('regra-texto-erro')).toBe('Escreva o texto da regra.');
    expect(erro('regra-categoria-erro')).toBe('Escolha a categoria.');
    digitar('#regra-texto', '* -');
    expect(erro('regra-texto-erro')).toBe('Use ao menos 2 letras ou números.');
    digitar('#regra-texto', 'u'.repeat(256));
    expect(erro('regra-texto-erro')).toBe('O texto pode ter até 255 caracteres.');
    escolher('#regra-categoria', 'Salário');
    expect(erro('regra-categoria-dica')).toBe('Categoria de receita: a regra só pega receitas.');
    digitar('#regra-texto', 'a');
    await new Promise((resolver) => setTimeout(resolver, 400));
    httpTesting.expectNone('/api/regras/previa');
    botao('Cancelar').click();
    TestBed.tick();
    await vi.waitFor(() => expect(resultado).toBeUndefined());
  });

  it('os erros da API vão para o campo ou para o alerta, e a prévia que falha diz isso e não trava a próxima', async () => {
    abrir({ regra: null, sugestao: null });
    digitar('#regra-texto', 'uber');
    escolher('#regra-categoria', 'App de transporte');
    (await previa()).flush(null, { status: 500, statusText: 'Erro' });
    TestBed.tick();
    expect(tituloDaPrevia()).toBe('Não foi possível calcular a prévia.');
    escolher('#regra-conta', 'Banco Horizonte');
    (await previa()).flush({ detail: 'Conta não encontrada.', codigo: 'CONTA_INVALIDA' }, { status: 422, statusText: 'Erro' });
    TestBed.tick();
    expect(tituloDaPrevia()).toBe('Conta não encontrada.');
    escolher('#regra-conta', 'Poupança');
    const terceira = await previa();
    expect(terceira.request.body).toEqual({ condicao: 'CONTEM', texto: 'uber', contaId: 2, categoriaId: 5 });
    terceira.flush({ casam: 0, semCategoria: 0, exemplos: [] });
    TestBed.tick();
    expect(tituloDaPrevia()).toBe('Não casa com nenhum lançamento seu ainda');
    escolher('#regra-conta', 'Banco Horizonte');
    (await previa()).flush({ casam: 0, semCategoria: 0, exemplos: [] });
    TestBed.tick();

    const responder = (corpo: object, status: number) => {
      botao('Criar regra').click();
      TestBed.tick();
      httpTesting.expectOne({ method: 'POST', url: '/api/regras' }).flush(corpo, { status, statusText: 'Erro' });
      TestBed.tick();
    };
    responder({ detail: 'Já existe uma regra igual: a 2ª, contém "uber".', codigo: 'REGRA_REPETIDA' }, 422);
    expect(erro('regra-texto-erro')).toBe('Já existe uma regra igual: a 2ª, contém "uber".');
    responder({ detail: 'Escolha uma conta ativa.', codigo: 'CONTA_INVALIDA' }, 422);
    expect(erro('regra-conta-erro')).toBe('Escolha uma conta ativa.');
    expect(erro('regra-texto-erro')).toBeNull();
    responder({ detail: 'Escolha uma categoria ativa.', codigo: 'CATEGORIA_INVALIDA' }, 422);
    expect(erro('regra-categoria-erro')).toBe('Escolha uma categoria ativa.');
    responder({ campos: [{ campo: 'descricao', mensagem: 'A descrição pode ter até 255 caracteres.' }] }, 400);
    expect(erro('regra-descricao-erro')).toBe('A descrição pode ter até 255 caracteres.');
    responder({ detail: 'Você chegou ao limite de 200 regras.', codigo: 'LIMITE_DE_REGRAS' }, 422);
    expect(painel().querySelector('[role="alert"] .flux-alerta__titulo')?.textContent).toBe('Você chegou ao limite de 200 regras.');
    responder({}, 500);
    expect(painel().querySelector('[role="alert"] .flux-alerta__titulo')?.textContent).toBe('Não foi possível salvar a regra. Tente novamente.');
    expect(resultado).toBeNull();
  });

  it('Editar diz a posição, mantém a categoria e a conta arquivadas e salva com PUT (decisão 11)', async () => {
    abrir({ regra: regra({ id: 31, posicao: 3, condicao: 'COMECA_COM', texto: 'rend pago', contaId: 3, contaNome: 'Poupança antiga',
      contaArquivada: true, natureza: 'RECEITA', categoriaId: 9, categoriaNome: 'Rendimentos', categoriaMaeNome: null,
      categoriaArquivada: true, pausada: true }), sugestao: null });
    expect(painel().querySelector('h2')?.textContent).toBe('Editar regra');
    expect(painel().querySelector('.regra-dialogo__texto')?.textContent).toBe('3ª na lista.');
    expect(textoSelecionado('#regra-condicao')).toBe('começa com');
    expect(textoSelecionado('#regra-categoria')).toBe('Rendimentos (arquivada)');
    expect(textoSelecionado('#regra-conta')).toBe('Poupança antiga (arquivada)');
    expect(painel().textContent).not.toContain('Entra em 1º lugar.');
    (await previa()).flush({ casam: 0, semCategoria: 0, exemplos: [] });
    digitar('#regra-texto', 'rend');
    (await previa()).flush({ casam: 2, semCategoria: 1, exemplos: [] });
    TestBed.tick();

    botao('Salvar').click();
    TestBed.tick();
    const pedido = httpTesting.expectOne({ method: 'PUT', url: '/api/regras/31' });
    expect(pedido.request.body).toEqual({ condicao: 'COMECA_COM', texto: 'rend', contaId: 3, categoriaId: 9, descricao: null });
    pedido.flush(regra({ id: 31, texto: 'rend' }));
    TestBed.tick();
    await vi.waitFor(() => expect(resultado).toEqual({ tipo: 'salva', regra: regra({ id: 31, texto: 'rend' }) }));
  });

  it('Excluir abre a confirmação por cima e fecha com "excluida"', async () => {
    abrir({ regra: regra({ id: 32, texto: 'cinema' }), sugestao: null });
    (await previa()).flush({ casam: 0, semCategoria: 0, exemplos: [] });
    botao('Excluir').click();
    TestBed.tick();
    const confirmacao = document.querySelector('app-confirmacao-dialogo') as HTMLElement;
    expect(confirmacao.querySelector('h2')?.textContent?.trim()).toBe('Excluir a regra contém "cinema"?');
    Array.from(confirmacao.querySelectorAll<HTMLButtonElement>('button')).find((item) => item.textContent?.trim() === 'Excluir regra')?.click();
    TestBed.tick();
    httpTesting.expectOne({ method: 'DELETE', url: '/api/regras/32' }).flush(null);
    TestBed.tick();
    await vi.waitFor(() => expect(resultado).toEqual({ tipo: 'excluida' }));
  });
});
