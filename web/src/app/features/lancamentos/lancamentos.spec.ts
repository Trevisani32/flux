import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, convertToParamMap, ParamMap, Params, provideRouter, Router } from '@angular/router';
import { BehaviorSubject, Observable, Subject, of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AGORA } from '../../core/tempo/agora';
import { Cartao, CartoesService, ListaDeCartoes } from '../cartoes/cartoes.service';
import { CompraDialogo } from '../cartoes/compra-dialogo/compra-dialogo';
import { Categoria, CategoriasService, ListaDeCategorias } from '../categorias/categorias.service';
import { Conta, ContasService, ListaDeContas } from '../contas/contas.service';
import { FluxoDeRegras } from '../regras/fluxo-de-regras';
import { LancamentoDialogo } from './lancamento-dialogo/lancamento-dialogo';
import { ItemDaLista, LancamentosService, ListaDoMes } from './lancamentos.service';
import { Lancamentos } from './lancamentos';

function conta(id: number, nome: string, arquivadaEm: string | null = null): Conta {
  return { id, nome, tipo: 'CORRENTE', saldoInicial: 0, dataSaldoInicial: '2026-09-01', cor: null, saldoAtual: 0, arquivadaEm };
}

function categoria(id: number, nome: string, subcategorias: Categoria[] = []): Categoria {
  return { id, nome, natureza: 'DESPESA', categoriaMaeId: null, nomeDaMae: null, arquivadaEm: null, subcategorias };
}

function item(parcial: Partial<ItemDaLista>): ItemDaLista {
  return {
    tipo: 'DESPESA', id: 10, data: '2026-10-06', descricao: 'Mercado Bom Preço', valor: -287.43, observacao: null,
    contaId: 1, contaNome: 'Banco Horizonte', categoriaId: 2, categoriaNome: 'Mercado', categoriaMaeNome: 'Alimentação',
    categoriaArquivada: false, contaDestinoId: null, contaDestinoNome: null, direcao: null, futuro: false, somenteLeitura: false, origem: 'MANUAL', descricaoBanco: null,
    ...parcial,
  };
}

const contas: ListaDeContas = {
  ativas: [conta(1, 'Banco Horizonte'), conta(2, 'Poupança')],
  arquivadas: [conta(3, 'Conta salário', '2026-11-03T15:00:00Z')],
  totais: { geral: 0, disponivel: 0, investimentos: 0 },
};

function cartao(id: number, nome: string, arquivadoEm: string | null = null): Cartao {
  const fatura = { mes: '2026-12', inicio: '2026-11-04', fechamento: '2026-12-03', vencimento: '2026-12-10', total: 0, lancamentos: 0 };
  return { id, nome, limite: 5000, diaFechamento: 3, diaVencimento: 10, contaPagamentoId: 1, contaPagamentoNome: 'Banco Horizonte',
    contaPagamentoArquivada: false, cor: 'ROXO', arquivadoEm, faturaAberta: fatura, proximaFatura: fatura, emAberto: 0, lancamentosEmAberto: 0 };
}

const cartoes: ListaDeCartoes = { ativos: [cartao(7, 'Cartão Horizonte')], arquivados: [cartao(8, 'Cartão Lua', '2026-11-01T10:00:00Z')] };

const categorias: ListaDeCategorias = {
  despesas: { ativas: [categoria(1, 'Alimentação', [categoria(2, 'Mercado')])], arquivadas: [categoria(9, 'Educação')] },
  receitas: { ativas: [categoria(5, 'Salário')], arquivadas: [] },
};

const reserva = item({
  tipo: 'TRANSFERENCIA', id: 4, data: '2026-10-05', descricao: 'Reserva do mês', valor: 1000, categoriaId: null, categoriaNome: null,
  categoriaMaeNome: null, contaDestinoId: 2, contaDestinoNome: 'Poupança', direcao: 'SAIDA',
});

const outubro: ListaDoMes = {
  mes: '2026-10',
  hoje: '2026-11-09',
  totais: { entradas: 7200, quantidadeEntradas: 1, saidas: -287.43, quantidadeSaidas: 1, resultado: 6912.57 },
  quantidadeLancamentos: 2,
  quantidadeTransferencias: 1,
  dias: [
    { data: '2026-10-06', saldoNoFimDoDia: 5778.2, itens: [item({})] },
    {
      data: '2026-10-05', saldoNoFimDoDia: 6065.63,
      itens: [reserva, item({ tipo: 'RECEITA', id: 11, data: '2026-10-05', descricao: 'Salário', valor: 7200, categoriaId: 5, categoriaNome: 'Salário', categoriaMaeNome: null })],
    },
  ],
};

function vazia(mes: string): ListaDoMes {
  return { ...outubro, mes, quantidadeLancamentos: 0, quantidadeTransferencias: 0, dias: [],
    totais: { entradas: 0, quantidadeEntradas: 0, saidas: 0, quantidadeSaidas: 0, resultado: 0 } };
}

describe('Lancamentos', () => {
  afterEach(() => vi.useRealTimers());

  function criar(parametros: Params, resposta: () => Observable<ListaDoMes> = () => of(outubro), listaDeContas: ListaDeContas = contas) {
    const url = new BehaviorSubject<ParamMap>(convertToParamMap(parametros));
    const restaurado = new Subject<void>();
    const aplicadas = new Subject<number>();
    const fluxo = { aplicadas$: aplicadas.asObservable(), aplicar: vi.fn(() => of(3)) };
    const servico = { listar: vi.fn(resposta), ultimaConta: null as number | null, restaurado$: restaurado.asObservable() };
    // Um Subject por diálogo aberto: fechamentos[n] fecha o n-ésimo.
    const fechamentos: Subject<unknown>[] = [];
    const dialogo = { open: vi.fn(() => {
      const fechamento = new Subject<unknown>();
      fechamentos.push(fechamento);
      return { afterClosed: () => fechamento.asObservable() };
    }) };
    TestBed.configureTestingModule({
      imports: [Lancamentos],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { queryParamMap: url.asObservable() } },
        { provide: LancamentosService, useValue: servico },
        { provide: ContasService, useValue: { listar: () => of(listaDeContas) } },
        { provide: CartoesService, useValue: { listar: () => of(cartoes) } },
        { provide: CategoriasService, useValue: { listar: () => of(categorias) } },
        { provide: MatDialog, useValue: dialogo },
        { provide: FluxoDeRegras, useValue: fluxo },
        { provide: AGORA, useValue: () => new Date('2026-11-09T15:00:00Z') },
      ],
    });
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(Lancamentos);
    fixture.detectChanges();
    const elemento = fixture.nativeElement as HTMLElement;
    const atualizar = () => fixture.detectChanges();
    return { elemento, atualizar, servico, dialogo, fechamentos, navegar, url, restaurado, fluxo, aplicadas };
  }

  function textos(elemento: HTMLElement, seletor: string): string[] {
    return Array.from(elemento.querySelectorAll(seletor)).map((no) => no.textContent?.trim().replace(/\s+/g, ' ') ?? '');
  }

  function botao(elemento: HTMLElement, texto: string): HTMLButtonElement {
    return Array.from(elemento.querySelectorAll<HTMLButtonElement>('button')).find((item) => item.textContent?.trim() === texto) as HTMLButtonElement;
  }

  function parametrosNavegados(navegar: ReturnType<typeof criar>['navegar']): Params {
    return (navegar.mock.lastCall?.[1] as { queryParams: Params }).queryParams;
  }

  it('lê o mês e os filtros da URL, chama a API com eles e mostra totais, dias e linhas', () => {
    const { elemento, servico } = criar({ mes: '2026-10', conta: '1' });

    expect(servico.listar).toHaveBeenCalledWith({ mes: '2026-10', conta: 1, cartao: null, categoria: null, tipo: null, busca: '' });
    expect(textos(elemento, '.lancamentos__subtitulo')).toEqual(['Banco Horizonte · 2 lançamentos e 1 transferência · Limpar filtros']);
    expect(textos(elemento, '.lancamentos__nome-do-mes')).toEqual(['outubro de 2026']);
    expect(textos(elemento, '.lancamentos__totais .lancamentos__rotulo')).toEqual(['Entradas · 1', 'Saídas · 1', 'Resultado · sem transferências']);
    expect(textos(elemento, '.lancamentos__totais .valor__texto')).toEqual(['+R$ 7.200,00', '−R$ 287,43', '+R$ 6.912,57']);
    expect(textos(elemento, '.lancamentos__data .flux-so-desktop')).toEqual(['Terça, 6 de outubro', 'Segunda, 5 de outubro']);
    expect(textos(elemento, '.lancamentos__saldo-do-dia .valor__texto')).toEqual(['R$ 5.778,20', 'R$ 6.065,63']);
    expect(textos(elemento, '.linha__titulo')).toEqual(['Mercado Bom Preço', 'Reserva do mês', 'Salário']);
    expect(textos(elemento, '.linha__meta-texto.flux-so-desktop')).toEqual(['Alimentação › Mercado', 'Para Poupança · fora dos totais', 'Salário']);
    const valores = Array.from(elemento.querySelectorAll('.linha__valor'));
    expect(valores.map((valor) => valor.querySelector('.valor__texto')?.textContent?.replace(/\s/g, ' '))).toEqual(['−R$ 287,43', 'R$ 1.000,00', '+R$ 7.200,00']);
    expect(valores[1].classList).toContain('flux-valor--neutro');
    expect(elemento.querySelector<HTMLSelectElement>('.lancamentos__filtro select')?.value).toBe('1');
    expect(Array.from(elemento.querySelectorAll('optgroup')).map((grupo) => grupo.label))
      .toEqual(['Contas', 'Cartões', 'Arquivados', 'Despesas', 'Receitas', 'Arquivadas']);
    expect(textos(elemento, '.lancamentos__filtro select option').slice(0, 6))
      .toEqual(['Todas as contas e cartões', 'Banco Horizonte', 'Poupança', 'Cartão Horizonte', 'Conta salário', 'Cartão Lua']);
    expect(elemento.querySelector('.lancamentos__fatura')).toBeNull();
  });

  it('mudar mês, tipo, conta e categoria navega com os parâmetros, e o mês de hoje sai da URL', () => {
    const { elemento, atualizar, servico, navegar, url } = criar({});
    expect(servico.listar).toHaveBeenCalledWith({ mes: '2026-11', conta: null, cartao: null, categoria: null, tipo: null, busca: '' });

    elemento.querySelector<HTMLButtonElement>('button[aria-label="Mês anterior"]')?.click();
    expect(parametrosNavegados(navegar)).toEqual({ mes: '2026-10', conta: null, cartao: null, categoria: null, tipo: null, busca: null });

    url.next(convertToParamMap({ mes: '2026-10' }));
    atualizar();
    expect(servico.listar).toHaveBeenLastCalledWith({ mes: '2026-10', conta: null, cartao: null, categoria: null, tipo: null, busca: '' });

    botao(elemento, 'Despesas').click();
    expect(parametrosNavegados(navegar)).toEqual({ mes: '2026-10', conta: null, cartao: null, categoria: null, tipo: 'despesa', busca: null });

    const [filtroDeConta, filtroDeCategoria] = Array.from(elemento.querySelectorAll<HTMLSelectElement>('.lancamentos__filtro select'));
    filtroDeConta.value = '3';
    filtroDeConta.dispatchEvent(new Event('change'));
    expect(parametrosNavegados(navegar)).toEqual({ mes: '2026-10', conta: 3, cartao: null, categoria: null, tipo: null, busca: null });
    filtroDeConta.value = 'cartao-7';
    filtroDeConta.dispatchEvent(new Event('change'));
    expect(parametrosNavegados(navegar)).toEqual({ mes: '2026-10', conta: null, cartao: 7, categoria: null, tipo: null, busca: null });
    filtroDeCategoria.value = 'sem';
    filtroDeCategoria.dispatchEvent(new Event('change'));
    expect(parametrosNavegados(navegar)).toMatchObject({ categoria: 'sem' });

    elemento.querySelector<HTMLButtonElement>('button[aria-label="Próximo mês"]')?.click();
    expect(parametrosNavegados(navegar)).toMatchObject({ mes: null });
  });

  it('a busca espera 300 ms sem digitar antes de mudar a URL', () => {
    vi.useFakeTimers();
    const { elemento, navegar } = criar({ mes: '2026-10' });
    const busca = elemento.querySelector<HTMLInputElement>('#lancamentos-busca') as HTMLInputElement;

    busca.value = 'merc';
    busca.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(200);
    busca.value = 'mercado ';
    busca.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(299);
    expect(navegar).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1);
    expect(parametrosNavegados(navegar)).toEqual({ mes: '2026-10', conta: null, cartao: null, categoria: null, tipo: null, busca: 'mercado' });
  });

  it('limpa os filtros, volta ao mês atual e não passa de 12 meses à frente', () => {
    const { elemento, navegar } = criar({ mes: '2027-11', tipo: 'receita', busca: 'salário' }, () => of(vazia('2027-11')));

    expect(elemento.querySelector<HTMLButtonElement>('button[aria-label="Próximo mês"]')?.disabled).toBe(true);
    botao(elemento, 'Mês atual').click();
    expect(parametrosNavegados(navegar)).toEqual({ mes: null, conta: null, cartao: null, categoria: null, tipo: 'receita', busca: 'salário' });
    expect(textos(elemento, '.estado-vazio__titulo')).toEqual(['Nada encontrado']);
    botao(elemento, 'Limpar filtros').click();
    expect(parametrosNavegados(navegar)).toEqual({ mes: '2027-11', conta: null, cartao: null, categoria: null, tipo: null, busca: null });
  });

  it('mostra o mês vazio, o aviso das transferências e o convite sem conta', () => {
    const vazio = criar({ mes: '2026-12' }, () => of(vazia('2026-12')));
    expect(textos(vazio.elemento, '.estado-vazio__titulo')).toEqual(['Nenhum lançamento em dezembro']);
    expect(vazio.elemento.querySelector('.lancamentos__filtros')).toBeNull();
    expect(textos(vazio.elemento, '.lancamentos__mes-atual')).toEqual(['Mês atual']);
    TestBed.resetTestingModule();

    const transferencias = criar({ mes: '2026-10', tipo: 'transferencia' });
    expect(textos(transferencias.elemento, '.lancamentos__aviso')).toEqual(['Transferências ficam fora das entradas, das saídas e do resultado.']);
    expect(transferencias.elemento.querySelector('.lancamentos__totais')).toBeNull();
    TestBed.resetTestingModule();

    const semConta = criar({}, () => of(vazia('2026-11')), { ativas: [], arquivadas: [], totais: contas.totais });
    expect(textos(semConta.elemento, '.estado-vazio__titulo')).toEqual(['Cadastre uma conta primeiro']);
    expect(semConta.elemento.querySelector('.lancamentos__mes')).toBeNull();
    expect(botao(semConta.elemento, 'Novo lançamento')).toBeUndefined();
  });

  it('Novo lançamento e o clique na linha abrem o diálogo, e a lista recarrega quando ele salva', () => {
    const { elemento, servico, dialogo, fechamentos } = criar({ mes: '2026-10', conta: '2' });

    botao(elemento, 'Novo lançamento').click();
    const [componente, configuracao] = dialogo.open.mock.lastCall as unknown as [unknown, { data: { item: ItemDaLista | null; contaPadrao: number | null; hoje: string } }];
    expect(componente).toBe(LancamentoDialogo);
    expect(configuracao.data).toMatchObject({ item: null, contaPadrao: 2, hoje: '2026-11-09' });

    elemento.querySelectorAll<HTMLButtonElement>('.linha')[1].click();
    expect((dialogo.open.mock.lastCall as unknown as [unknown, { data: { item: ItemDaLista } }])[1].data.item).toBe(reserva);
    expect(servico.listar).toHaveBeenCalledTimes(1);
    fechamentos[1].next('salvo');
    expect(servico.listar).toHaveBeenCalledTimes(2);
    fechamentos[0].next(undefined);
    expect(servico.listar).toHaveBeenCalledTimes(2);
  });

  it('com o filtro de um cartão, mostra a fatura do mês; a parcela abre a compra (Fatia 05a, decisões 23 e 25)', () => {
    const parcela = item({ id: 30, data: '2026-12-10', descricao: 'Sapataria Passo Firme', valor: -150, contaId: null as unknown as number,
      contaNome: null as unknown as string, categoriaId: null, categoriaNome: null, categoriaMaeNome: null, cartaoId: 7,
      cartaoNome: 'Cartão Horizonte', cartaoCor: 'ROXO', compraId: 12, estorno: false, parcela: 1, parcelas: 3, dataCompra: '2026-11-05' });
    const dezembro: ListaDoMes = { ...vazia('2026-12'), quantidadeLancamentos: 1, dias: [{ data: '2026-12-10', saldoNoFimDoDia: null, itens: [parcela] }],
      fatura: { cartaoId: 7, cartaoNome: 'Cartão Horizonte', cartaoCor: 'ROXO', mes: '2026-12', fechamento: '2026-12-03',
        vencimento: '2026-12-10', situacao: 'ABERTA', total: 1113.69 } };
    const { elemento, servico, dialogo, fechamentos } = criar({ mes: '2026-12', cartao: '7' }, () => of(dezembro));

    expect(servico.listar).toHaveBeenCalledWith({ mes: '2026-12', conta: null, cartao: 7, categoria: null, tipo: null, busca: '' });
    expect(textos(elemento, '.lancamentos__subtitulo')).toEqual(['Cartão Horizonte · 1 lançamento · Limpar filtros']);
    expect(elemento.querySelector<HTMLSelectElement>('.lancamentos__filtro select')?.value).toBe('cartao-7');
    expect(elemento.querySelector('.lancamentos__fatura span')?.textContent?.replace(/\s+/g, ' '))
      .toBe('Fatura de dezembro · fecha 03/12 · vence 10/12 · R$ 1.113,69');
    expect(elemento.querySelector('.lancamentos__fatura a')?.getAttribute('href')).toBe('/cartoes/7/faturas/2026-12');
    expect(textos(elemento, '.linha__meta-texto.flux-so-desktop')).toEqual(['compra em 05/11']);
    expect(textos(elemento, '.linha__pilula--parcela')).toEqual(['1/3']);
    expect(elemento.querySelector('.linha__selo--cartao')).not.toBeNull();

    elemento.querySelector<HTMLButtonElement>('.linha')?.click();
    const [componente, configuracao] = dialogo.open.mock.lastCall as unknown as [unknown, { data: { tipo: string; id: number; hoje: string } }];
    expect(componente).toBe(CompraDialogo);
    expect(configuracao.data).toMatchObject({ tipo: 'COMPRA', id: 12, hoje: '2026-11-09' });
    fechamentos[0].next('excluido');
    expect(servico.listar).toHaveBeenCalledTimes(2);
  });

  it('um importado restaurado pelo aviso curto recarrega a lista, e "Importar" leva à importação (Fatia 04a)', () => {
    const { elemento, servico, restaurado } = criar({ mes: '2026-10' });
    expect(elemento.querySelector('.cabecalho__acoes a[href="/importacao"]')?.textContent?.trim()).toBe('Importar');
    restaurado.next();
    expect(servico.listar).toHaveBeenCalledTimes(2);
  });

  it('com o filtro "Sem categoria", oferece "Aplicar regras"; aplicadas, a lista recarrega (Fatia 04b, decisão 17)', () => {
    const { elemento, servico, fluxo, aplicadas } = criar({ mes: '2026-10', categoria: 'sem' });
    expect(elemento.querySelector('.lancamentos__regras p')?.textContent).toBe('As regras podem pôr categoria nos lançamentos sem categoria, '
      + 'de qualquer mês.');
    botao(elemento, 'Aplicar regras').click();
    expect(fluxo.aplicar).toHaveBeenCalledWith(null);
    aplicadas.next(3);
    expect(servico.listar).toHaveBeenCalledTimes(2);
    TestBed.resetTestingModule();

    const semFiltro = criar({ mes: '2026-10' });
    expect(semFiltro.elemento.querySelector('.lancamentos__regras')).toBeNull();
  });
});
