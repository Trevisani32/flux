import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { Categoria, ListaDeCategorias } from '../../categorias/categorias.service';
import { Cartao, ListaDeCartoes } from '../cartoes.service';
import { Compra, ParcelaDaCompra, PreviaDaCompra } from '../compras.service';
import { abrirCompra, DadosDoDialogoDeCompra, ResultadoDaCompra } from './compra-dialogo';

function categoria(id: number, nome: string, natureza: 'DESPESA' | 'RECEITA'): Categoria {
  return { id, nome, natureza, categoriaMaeId: null, nomeDaMae: null, arquivadaEm: null, subcategorias: [] };
}

const categorias: ListaDeCategorias = {
  despesas: { ativas: [categoria(1, 'Compras', 'DESPESA')], arquivadas: [] },
  receitas: { ativas: [categoria(5, 'Reembolsos', 'RECEITA')], arquivadas: [] },
};

function cartao(id: number, nome: string): Cartao {
  const fatura = { mes: '2026-12', inicio: '2026-11-04', fechamento: '2026-12-03', vencimento: '2026-12-10', total: 0, lancamentos: 0 };
  return { id, nome, limite: 5000, diaFechamento: 3, diaVencimento: 10, contaPagamentoId: 1, contaPagamentoNome: 'Banco Horizonte',
    contaPagamentoArquivada: false, cor: null, arquivadoEm: null, faturaAberta: fatura, proximaFatura: fatura, emAberto: 0, lancamentosEmAberto: 0 };
}

const cartoes: ListaDeCartoes = { ativos: [cartao(7, 'Cartão Horizonte'), cartao(8, 'Cartão Lua')], arquivados: [] };

/** As parcelas de um em um mês a partir de `desde` ("2026-07"); as de antes de `primeiraNoFlux` ficam fora do Flux. */
function parcelas(desde: string, quantas: number, valor: number, primeiraNoFlux = 1): ParcelaDaCompra[] {
  const [ano, mes] = desde.split('-').map(Number);
  return Array.from({ length: quantas }, (_, indice) => {
    const contagem = ano * 12 + mes - 1 + indice;
    const mesDaParcela = `${Math.floor(contagem / 12)}-${String((contagem % 12) + 1).padStart(2, '0')}`;
    return { numero: indice + 1, mes: mesDaParcela, vencimento: `${mesDaParcela}-10`, valor, noFlux: indice + 1 >= primeiraNoFlux };
  });
}

function previaDe(lista: readonly ParcelaDaCompra[], situacao: 'FUTURA' | 'ABERTA' | 'FECHADA' = 'ABERTA'): PreviaDaCompra {
  const mes = lista[0].mes;
  return { parcelas: lista, primeiraFatura: { mes, fechamento: `${mes}-03`, vencimento: `${mes}-10`, situacao } };
}

const loja: Compra = {
  id: 5, tipo: 'COMPRA', cartaoId: 7, cartaoNome: 'Cartão Horizonte', somenteLeitura: false, data: '2026-11-09', descricao: 'Loja Eletro Fictícia',
  valorTotal: 1990, parcelas: 10, parcelaInicial: 1, categoriaId: 1, categoriaNome: 'Compras', categoriaMaeNome: null, categoriaArquivada: false,
  observacao: null, parcelasNoFlux: parcelas('2026-12', 10, 199),
};

describe('CompraDialogo', () => {
  let http: HttpTestingController;
  let resultado: ResultadoDaCompra | undefined | null;
  let acaoDoAviso: Subject<void>;
  let snackBar: { open: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    acaoDoAviso = new Subject<void>();
    snackBar = { open: vi.fn(() => ({ onAction: () => acaoDoAviso.asObservable() })) };
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: MatSnackBar, useValue: snackBar }],
    });
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

  function abrir(dados: Partial<DadosDoDialogoDeCompra>, compra: Compra | null = null): void {
    resultado = null;
    abrirCompra(TestBed.inject(MatDialog), { tipo: 'COMPRA', id: null, cartaoId: 7, hoje: '2026-11-12', categorias, ...dados })
      .subscribe((valor) => (resultado = valor));
    TestBed.tick();
    http.expectOne({ method: 'GET', url: '/api/cartoes' }).flush(cartoes);
    if (compra !== null) {
      http.expectOne({ method: 'GET', url: `${compra.tipo === 'ESTORNO' ? '/api/estornos' : '/api/compras'}/${compra.id}` }).flush(compra);
    }
    TestBed.tick();
  }

  function painel(): HTMLElement {
    return document.querySelector('app-compra-dialogo') as HTMLElement;
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

  function escolher(seletor: string, rotulo: string): void {
    const lista = campo<HTMLSelectElement>(seletor);
    lista.value = (Array.from(lista.options).find((opcao) => opcao.textContent?.trim() === rotulo) as HTMLOptionElement).value;
    lista.dispatchEvent(new Event('change'));
    TestBed.tick();
  }

  function botao(texto: string, dentro: ParentNode = painel()): HTMLButtonElement {
    return Array.from(dentro.querySelectorAll<HTMLButtonElement>('button')).find((item) => item.textContent?.trim() === texto) as HTMLButtonElement;
  }

  /** A prévia sai 300 ms depois da última mudança (decisão 18). */
  function previa(): Promise<TestRequest> {
    return vi.waitFor(() => http.expectOne({ method: 'POST', url: '/api/compras/previa' }), { timeout: 2000 });
  }

  /** O Intl escreve "R$" com espaço não separável: o texto volta com espaço comum. */
  function normal(texto: string | null | undefined): string {
    return (texto ?? '').trim().replace(/\s+/g, ' ');
  }

  it('a Nova compra mostra a prévia da API, aceita a compra em andamento e lança as parcelas (decisões 16 a 18)', async () => {
    abrir({});
    expect(painel().querySelector('h2')?.textContent).toBe('Nova compra');
    expect(campo<HTMLSelectElement>('#compra-cartao').selectedOptions[0].textContent?.trim()).toBe('Cartão Horizonte');
    expect(campo('#compra-data').value).toBe('2026-11-12');
    const opcoes = Array.from(campo<HTMLSelectElement>('#compra-parcelas').options).map((opcao) => opcao.textContent?.trim());
    expect(opcoes).toHaveLength(48);
    expect(opcoes.slice(0, 3)).toEqual(['À vista (1x)', '2x', '3x']);
    expect(campo('#compra-andamento')).toBeNull();
    digitar('#compra-descricao', 'Geladeira Fria Mais');
    digitar('#compra-data', '2026-06-20');
    digitar('#compra-valor', '2.490');
    escolher('#compra-parcelas', '10x');
    const primeira = await previa();
    expect(primeira.request.body).toEqual({ cartaoId: 7, data: '2026-06-20', valorTotal: 2490, parcelas: 10, parcelaInicial: 1 });
    primeira.flush(previaDe(parcelas('2026-07', 10, 249), 'FECHADA'));
    TestBed.tick();
    expect(normal(painel().querySelector('.compra__titulo-previa')?.textContent)).toBe('10x de R$ 249,00');
    expect(botao('Lançar 10 parcelas')).toBeDefined();

    // Decisão 18: marcar "Compra em andamento" com a primeira parcela em 1 não muda o que a prévia usa, e ela não é pedida.
    campo('#compra-andamento').click();
    TestBed.tick();
    await new Promise((resolver) => setTimeout(resolver, 400));
    http.expectNone({ method: 'POST', url: '/api/compras/previa' });
    digitar('#compra-inicial', '6');
    const segunda = await previa();
    expect(segunda.request.body).toMatchObject({ parcelaInicial: 6 });
    segunda.flush(previaDe(parcelas('2026-07', 10, 249, 6), 'FECHADA'));
    TestBed.tick();
    expect(normal(painel().querySelector('.compra__titulo-previa')?.textContent)).toBe('5 parcelas de R$ 249,00 no Flux');
    expect(Array.from(painel().querySelectorAll('.compra__parcela--fora span')).map((item) => item.textContent)).toEqual(['1ª a 5ª', 'julho a novembro']);
    expect(painel().querySelector('.compra__nota')?.textContent).toBe('A 1ª caiu na fatura de julho. As de antes da 6ª ficam fora do Flux.');
    botao('Lançar 5 parcelas').click();
    TestBed.tick();
    const pedido = http.expectOne({ method: 'POST', url: '/api/compras' });
    expect(pedido.request.body).toEqual({ cartaoId: 7, data: '2026-06-20', descricao: 'Geladeira Fria Mais', categoriaId: null, observacao: null,
      valorTotal: 2490, parcelas: 10, parcelaInicial: 6 });
    pedido.flush({ ...loja, parcelasNoFlux: parcelas('2026-07', 10, 249, 6).filter((parcela) => parcela.noFlux) });
    await vi.waitFor(() => expect(resultado).toBe('salvo'));
    expect(snackBar.open).toHaveBeenCalledWith('Compra lançada em 5 parcelas.', 'Ver fatura', { duration: 8000 });
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    acaoDoAviso.next();
    expect(navegar).toHaveBeenCalledWith(['/cartoes', 7, 'faturas', '2026-12']);
  });

  it('a primeira parcela fora de 1 a N e o mínimo da API (R$ 0,47 em 48x) viram erro do campo (decisões 15 e 16)', async () => {
    abrir({});
    digitar('#compra-descricao', 'Loja');
    digitar('#compra-valor', '0,47');
    escolher('#compra-parcelas', '48x');
    (await previa()).flush({ campos: [{ campo: 'valorTotal', mensagem: 'Em 48x, o valor precisa ser de pelo menos R$ 0,48.' }] },
      { status: 400, statusText: 'Bad Request' });
    TestBed.tick();
    expect(painel().querySelector('#compra-valor-erro')?.textContent).toBe('Em 48x, o valor precisa ser de pelo menos R$ 0,48.');
    expect(painel().querySelector('.compra__previa')).toBeNull();
    campo('#compra-andamento').click();
    TestBed.tick();
    digitar('#compra-inicial', '49');
    expect(painel().querySelector('#compra-inicial-erro')?.textContent).toBe('A primeira parcela no Flux vai da 1ª à 48ª.');
    botao('Lançar 48 parcelas').click();
    TestBed.tick();
    http.expectNone({ method: 'POST', url: '/api/compras' });
    // Decisão 16: a data vai até 10 anos para trás.
    expect(campo('#compra-data').min).toBe('2016-11-12');
    digitar('#compra-data', '2016-11-11');
    expect(painel().querySelector('#compra-data-erro')?.textContent).toBe('A data precisa ser 12/11/2016 ou depois.');
  });

  it('Editar compra preenche os campos e "Excluir" diz de quais faturas as parcelas saem (decisão 19)', async () => {
    abrir({ id: 5 }, loja);
    expect(painel().querySelector('h2')?.textContent).toBe('Editar compra');
    expect(campo('#compra-descricao').value).toBe('Loja Eletro Fictícia');
    expect(campo('#compra-valor').value).toBe('1.990,00');
    expect(campo<HTMLSelectElement>('#compra-parcelas').selectedOptions[0].textContent?.trim()).toBe('10x');
    expect(campo<HTMLSelectElement>('#compra-categoria').selectedOptions[0].textContent?.trim()).toBe('Compras');
    expect(botao('Salvar')).toBeDefined();
    (await previa()).flush(previaDe(loja.parcelasNoFlux));
    TestBed.tick();
    botao('Excluir').click();
    TestBed.tick();
    const confirmacao = document.querySelector('app-confirmacao-dialogo') as HTMLElement;
    expect(confirmacao.querySelector('h2')?.textContent).toBe('Excluir Loja Eletro Fictícia?');
    expect(normal(confirmacao.querySelector('.confirmacao__texto')?.textContent))
      .toBe('As 10 parcelas saem das faturas de dezembro de 2026 a setembro de 2027. Isso não pode ser desfeito.');
    botao('Excluir compra', confirmacao).click();
    TestBed.tick();
    http.expectOne({ method: 'DELETE', url: '/api/compras/5' }).flush(null);
    await vi.waitFor(() => expect(resultado).toBe('excluido'));
    expect(snackBar.open).toHaveBeenCalledWith('Compra excluída.', 'Fechar', { duration: 4000 });
  });

  it('a compra com mais de 10 anos se edita com a data que tem, sem prévia; só a data nova passa pela faixa (decisão 16)', async () => {
    abrir({ id: 5 }, { ...loja, data: '2016-11-10', parcelasNoFlux: parcelas('2016-12', 10, 199) });
    expect(campo('#compra-data').min).toBe('2016-11-10');
    digitar('#compra-descricao', 'Loja Eletro Antiga');
    await new Promise((resolver) => setTimeout(resolver, 400));
    http.expectNone({ method: 'POST', url: '/api/compras/previa' });
    digitar('#compra-data', '2016-11-09');
    expect(painel().querySelector('#compra-data-erro')?.textContent).toBe('A data precisa ser 12/11/2016 ou depois.');
    digitar('#compra-data', '2016-11-10');
    expect(painel().querySelector('#compra-data-erro')).toBeNull();
    botao('Salvar').click();
    TestBed.tick();
    const pedido = http.expectOne({ method: 'PUT', url: '/api/compras/5' });
    expect(pedido.request.body).toMatchObject({ data: '2016-11-10', descricao: 'Loja Eletro Antiga' });
    pedido.flush({ ...loja, data: '2016-11-10', descricao: 'Loja Eletro Antiga' });
    await vi.waitFor(() => expect(resultado).toBe('salvo'));
  });

  it('com o cartão arquivado, a compra abre só para leitura e não pede prévia (decisão 5)', () => {
    abrir({ id: 5 }, { ...loja, somenteLeitura: true });
    expect(painel().querySelector('h2')?.textContent).toBe('Loja Eletro Fictícia');
    expect(painel().querySelector('.flux-alerta__titulo')?.textContent).toBe('Cartão Horizonte está arquivado');
    expect(painel().querySelector('form')).toBeNull();
    expect(botao('Fechar')).toBeDefined();
  });

  it('o Novo estorno usa as categorias de receita, diz a fatura pela prévia e lança o valor (decisão 20)', async () => {
    abrir({ tipo: 'ESTORNO' });
    expect(painel().querySelector('h2')?.textContent).toBe('Novo estorno');
    expect(painel().querySelector('label[for="compra-data"]')?.textContent).toBe('Data do estorno');
    expect(Array.from(campo<HTMLSelectElement>('#compra-categoria').options).map((opcao) => opcao.textContent?.trim()))
      .toEqual(['Sem categoria', 'Reembolsos']);
    expect(campo('#compra-parcelas')).toBeNull();
    expect(painel().querySelector('.flux-dica')?.textContent)
      .toBe('Cai na fatura da data do estorno e abate o total. Devolveu a compra inteira antes de pagar? Exclua a compra.');
    digitar('#compra-descricao', 'Estorno Sapataria');
    digitar('#compra-valor', '50');
    escolher('#compra-categoria', 'Reembolsos');
    const pedidoDaPrevia = await previa();
    expect(pedidoDaPrevia.request.body).toEqual({ cartaoId: 7, data: '2026-11-12', valorTotal: 50, parcelas: 1, parcelaInicial: 1 });
    pedidoDaPrevia.flush(previaDe(parcelas('2026-12', 1, 50)));
    TestBed.tick();
    expect(painel().querySelector('.flux-dica')?.textContent)
      .toBe('Cai na fatura de dezembro (vence 10/12) e abate o total. Devolveu a compra inteira antes de pagar? Exclua a compra.');
    botao('Lançar estorno').click();
    TestBed.tick();
    const pedido = http.expectOne({ method: 'POST', url: '/api/estornos' });
    expect(pedido.request.body).toEqual({ cartaoId: 7, data: '2026-11-12', descricao: 'Estorno Sapataria', categoriaId: 5, observacao: null, valor: 50 });
    pedido.flush({ ...loja, tipo: 'ESTORNO', parcelas: 1, parcelasNoFlux: parcelas('2026-12', 1, 50) });
    TestBed.tick();
    expect(snackBar.open).toHaveBeenCalledWith('Estorno lançado.', 'Ver fatura', { duration: 8000 });
  });

  it('a compra que não existe mais mostra o 404', () => {
    abrirCompra(TestBed.inject(MatDialog), { tipo: 'COMPRA', id: 99, cartaoId: null, hoje: '2026-11-12', categorias }).subscribe();
    TestBed.tick();
    http.expectOne('/api/cartoes').flush(cartoes);
    http.expectOne('/api/compras/99').flush({ detail: 'Compra não encontrada.' }, { status: 404, statusText: 'Not Found' });
    TestBed.tick();
    expect(painel().querySelector('[role="alert"] .flux-alerta__titulo')?.textContent).toBe('Compra não encontrada.');
    expect(botao('Tentar de novo')).toBeDefined();
  });

  it('a categoria que deixou de valer volta como erro do campo', async () => {
    abrir({});
    digitar('#compra-descricao', 'Loja');
    digitar('#compra-valor', '100');
    (await previa()).flush(previaDe(parcelas('2026-12', 1, 100)));
    // Decisão 18: os textos não pedem prévia.
    digitar('#compra-descricao', 'Loja Nova');
    digitar('#compra-observacao', 'Presente');
    await new Promise((resolver) => setTimeout(resolver, 400));
    http.expectNone({ method: 'POST', url: '/api/compras/previa' });
    botao('Lançar compra').click();
    TestBed.tick();
    http.expectOne({ method: 'POST', url: '/api/compras' }).flush({ codigo: 'CATEGORIA_INVALIDA', detail: 'Escolha uma categoria ativa.' },
      { status: 422, statusText: 'Unprocessable' });
    TestBed.tick();
    expect(painel().querySelector('#compra-categoria-erro')?.textContent).toBe('Escolha uma categoria ativa.');
  });
});
