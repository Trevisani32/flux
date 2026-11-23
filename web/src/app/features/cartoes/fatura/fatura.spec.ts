import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, convertToParamMap, ParamMap, provideRouter, Router } from '@angular/router';
import { BehaviorSubject, Observable, of, Subject, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { AGORA } from '../../../core/tempo/agora';
import { ConfirmacaoDialogo, DadosDaConfirmacao } from '../../../shared/confirmacao/confirmacao-dialogo';
import { CategoriasService, ListaDeCategorias } from '../../categorias/categorias.service';
import { AjusteDialogo } from '../ajuste-dialogo/ajuste-dialogo';
import { CartoesService, Fatura, ItemDaFatura } from '../cartoes.service';
import { CompraDialogo, DadosDoDialogoDeCompra } from '../compra-dialogo/compra-dialogo';
import { DadosDoDialogoDePagamento, PagamentoDialogo } from '../pagamento-dialogo/pagamento-dialogo';
import { PaginaDaFatura } from './fatura';

const categorias: ListaDeCategorias = { despesas: { ativas: [], arquivadas: [] }, receitas: { ativas: [], arquivadas: [] } };

function item(parcial: Partial<ItemDaFatura>): ItemDaFatura {
  return { lancamentoId: 1, compraId: 10, tipo: 'COMPRA', dataCompra: '2026-11-09', descricao: 'Sapataria Passo Firme', valor: -150, parcela: 1,
    parcelas: 3, parcelaInicial: 1, valorTotal: 450, categoriaId: null, categoriaNome: null, categoriaMaeNome: null, ...parcial };
}

function fatura(parcial: Partial<Fatura>): Fatura {
  return {
    cartaoId: 7, cartaoNome: 'Cartão Horizonte', cartaoCor: 'OURO', cartaoArquivado: false, mes: '2026-12', inicio: '2026-11-04',
    fechamento: '2026-12-03', vencimento: '2026-12-10', gravada: true, fechamentoAjustado: false, fechamentoNormal: '2026-12-03', ajusteDe: '2026-11-04',
    ajusteAte: '2026-12-09', ajusteAteOVencimento: true, situacao: 'ABERTA', compras: 200, quantidadeCompras: 2, estornos: 50, quantidadeEstornos: 1,
    total: 150, depois: 300, depoisAte: '2027-02', primeiroMes: '2026-11', ultimoMes: '2027-12', pago: 0, quantidadePagamentos: 0, pendente: 150,
    pagoAMais: 0, pagaFora: false, limite: 5000, disponivel: 732.65, usoDoLimite: 85, contaPagamentoId: 1, pagamentos: [],
    itens: [item({}), item({ lancamentoId: 2, compraId: 11, descricao: 'Padaria Pão Quente', valor: -50, parcela: 1, parcelas: 1, valorTotal: 50,
      categoriaNome: 'Padaria', categoriaMaeNome: 'Alimentação' }),
    item({ lancamentoId: 3, compraId: 12, tipo: 'ESTORNO', descricao: 'Estorno Sapataria', valor: 50, parcelas: 1, valorTotal: 50 })],
    ...parcial,
  };
}

describe('PaginaDaFatura', () => {
  function criar(resposta: Observable<Fatura> = of(fatura({})), mes = '2026-12', id = '7') {
    const parametros = new BehaviorSubject<ParamMap>(convertToParamMap({ id, mes }));
    const servico = {
      fatura: vi.fn(() => resposta),
      voltarAoFechamentoNormal: vi.fn(() => of(fatura({}))),
      desmarcarPagaFora: vi.fn(() => of(fatura({}))),
    };
    const fechamento = new Subject<unknown>();
    const dialogo = { open: vi.fn(() => ({ afterClosed: () => fechamento.asObservable() })) };
    const snackBar = { open: vi.fn() };
    TestBed.configureTestingModule({
      imports: [PaginaDaFatura],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: parametros.asObservable() } },
        { provide: CartoesService, useValue: servico },
        { provide: CategoriasService, useValue: { listar: () => of(categorias) } },
        { provide: MatDialog, useValue: dialogo },
        { provide: MatSnackBar, useValue: snackBar },
        { provide: AGORA, useValue: () => new Date('2026-11-12T15:00:00Z') },
      ],
    });
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(PaginaDaFatura);
    fixture.detectChanges();
    return { fixture, elemento: fixture.nativeElement as HTMLElement, servico, dialogo, fechamento, snackBar, navegar, parametros };
  }

  function texto(elemento: HTMLElement, seletor: string): string {
    return (elemento.querySelector(seletor)?.textContent ?? '').trim().replace(/\s+/g, ' ');
  }

  function textos(elemento: HTMLElement, seletor: string): string[] {
    return Array.from(elemento.querySelectorAll(seletor)).map((item) => (item.textContent ?? '').trim().replace(/\s+/g, ' '));
  }

  /** Um botão do topo da página pelo texto. */
  function doTopo(elemento: HTMLElement, rotulo: string): HTMLButtonElement {
    return Array.from(elemento.querySelectorAll<HTMLButtonElement>('app-cabecalho-pagina button'))
      .find((botao) => botao.textContent?.trim() === rotulo) as HTMLButtonElement;
  }

  it('mostra o mês, a situação, a linha do tempo, os totais e os lançamentos (decisões 9 a 11)', () => {
    const { elemento, servico } = criar();
    expect(servico.fatura).toHaveBeenCalledWith(7, '2026-12');
    expect(elemento.querySelector('h1')?.textContent).toBe('Fatura de dezembro de 2026');
    expect(texto(elemento, '.fatura__trilha')).toBe('Cartões › Cartão Horizonte · disponível R$ 732,65 de R$ 5.000,00');
    expect(texto(elemento, '.fatura__nome-do-mes')).toBe('Dezembro de 2026');
    expect(texto(elemento, '.fatura__situacao')).toBe('Aberta');
    expect(texto(elemento, '.fatura__nota-da-situacao')).toBe('Fecha em 21 dias');
    expect(textos(elemento, '.fatura__marco')).toEqual(['04/11começa', '03/12fecha', '10/12vence']);
    expect(textos(elemento, '.fatura__totais .fatura__nota')).toEqual(['2 compras e parcelas', '1 estorno', 'até agora; fecha em 03/12',
      'parcelas de janeiro a fevereiro']);
    expect(texto(elemento, '.fatura__totais div:nth-child(3) .fatura__numero')).toBe('R$ 150,00');
    expect(textos(elemento, '.fatura__tabela tbody .fatura__abrir b')).toEqual(['Sapataria Passo Firme', 'Padaria Pão Quente', 'Estorno Sapataria']);
    expect(textos(elemento, '.fatura__tabela tbody .fatura__c-parcela')).toEqual(['1/3', '—', 'Estorno']);
    expect(textos(elemento, '.fatura__tabela tbody .fatura__meta')).toEqual(['R$ 450,00 em 3x', 'À vista', 'Crédito na fatura']);
    expect(textos(elemento, '.fatura__tabela tbody .fatura__c-categoria')).toEqual(['Sem categoria', 'Alimentação › Padaria', 'Sem categoria']);
    expect(texto(elemento, '.fatura__cabecalho-da-lista .fatura__nota')).toBe('Em Lançamentos, todos aparecem em 10/12, o vencimento.');
    expect(elemento.querySelector('.fatura__voltar-ao-normal')).toBeNull();
  });

  it('as setas levam ao mês vizinho e param no primeiro e no último mês que a API diz (decisão 13)', () => {
    const { elemento, navegar } = criar(of(fatura({ mes: '2026-11', situacao: 'FECHADA', fechamento: '2026-11-03', vencimento: '2026-11-10' })), '2026-11');
    const [anterior, seguinte] = Array.from(elemento.querySelectorAll<HTMLButtonElement>('.fatura__seta'));
    expect(anterior.getAttribute('aria-label')).toBe('Fatura de outubro');
    expect(anterior.disabled).toBe(true);
    expect(seguinte.getAttribute('aria-label')).toBe('Fatura de dezembro');
    expect(texto(elemento, '.fatura__nota-da-situacao')).toBe('Fechou em 03/11 · venceu em 10/11');
    expect(texto(elemento, '.fatura__totais div:nth-child(3) .fatura__nota')).toBe('fechou em 03/11');
    seguinte.click();
    expect(navegar).toHaveBeenCalledWith(['/cartoes', 7, 'faturas', '2026-12']);
  });

  it('o fechamento ajustado mostra "ajustado" e volta ao normal com aviso (decisão 12)', () => {
    const { fixture, elemento, servico, snackBar } = criar(of(fatura({ fechamento: '2026-12-04', fechamentoAjustado: true })));
    expect(textos(elemento, '.fatura__marco')[1]).toBe('04/12fecha · ajustado');
    const voltar = elemento.querySelector<HTMLButtonElement>('.fatura__voltar-ao-normal') as HTMLButtonElement;
    expect(voltar.textContent?.trim()).toBe('Voltar ao fechamento normal (03/12)');
    voltar.click();
    fixture.detectChanges();
    expect(servico.voltarAoFechamentoNormal).toHaveBeenCalledWith(7, '2026-12');
    expect(snackBar.open).toHaveBeenCalledWith('O fechamento voltou ao normal.', 'Fechar', { duration: 5000 });
    expect(servico.fatura).toHaveBeenCalledTimes(2);
  });

  it('abre a compra do item, a Nova compra, o Novo estorno e o ajuste; o que mudou recarrega a fatura', () => {
    const { elemento, dialogo, fechamento, servico } = criar();
    elemento.querySelector<HTMLButtonElement>('.fatura__tabela .fatura__abrir')?.click();
    const [componente, configuracao] = dialogo.open.mock.calls[0] as unknown as [unknown, { data: DadosDoDialogoDeCompra }];
    expect(componente).toBe(CompraDialogo);
    expect(configuracao.data).toEqual({ tipo: 'COMPRA', id: 10, cartaoId: 7, hoje: '2026-11-12', categorias });
    fechamento.next('salvo');
    expect(servico.fatura).toHaveBeenCalledTimes(2);
    const botoes = Array.from(elemento.querySelectorAll<HTMLButtonElement>('app-cabecalho-pagina button'));
    expect(botoes.map((botao) => botao.textContent?.trim())).toEqual(['Ajustar fechamento', 'Novo estorno', 'Pagar fatura', 'Mais',
      'Nova compra']);
    botoes[1].click();
    expect((dialogo.open.mock.calls[1] as unknown as [unknown, { data: DadosDoDialogoDeCompra }])[1].data).toMatchObject({ tipo: 'ESTORNO', id: null });
    botoes[0].click();
    expect((dialogo.open.mock.calls[2] as unknown as [unknown])[0]).toBe(AjusteDialogo);
  });

  it('"Depois desta" diz o crédito e leva o ano quando a fatura não é deste ano ou a faixa cruza anos (decisão 11)', () => {
    const credito = criar(of(fatura({ depois: -1000, depoisAte: '2027-01' })));
    expect(texto(credito.elemento, '.fatura__numero--depois')).toBe('Crédito de R$ 1.000,00');
    expect(textos(credito.elemento, '.fatura__totais .fatura__nota')[3]).toBe('nas faturas de janeiro');
    TestBed.resetTestingModule();
    const antiga = criar(of(fatura({ mes: '2016-11', situacao: 'FECHADA', fechamento: '2016-11-03', vencimento: '2016-11-10', depoisAte: '2027-08' })),
      '2016-11');
    expect(texto(antiga.elemento, '.fatura__numero--depois')).toBe('R$ 300,00');
    expect(textos(antiga.elemento, '.fatura__totais .fatura__nota')[3]).toBe('parcelas de dezembro de 2016 a agosto de 2027');
    TestBed.resetTestingModule();
    const cruza = criar(of(fatura({ mes: '2026-11', depoisAte: '2027-03' })), '2026-11');
    expect(textos(cruza.elemento, '.fatura__totais .fatura__nota')[3]).toBe('parcelas de dezembro de 2026 a março de 2027');
  });

  it('com o cartão arquivado, só leitura; sem nada, o vazio diz as datas, e "pelas datas do cartão" só sem a fatura gravada (decisão 9)', () => {
    const arquivado = criar(of(fatura({ cartaoArquivado: true, gravada: false, itens: [] })));
    expect(texto(arquivado.elemento, 'app-alerta .flux-alerta__titulo')).toBe('Cartão Horizonte está arquivado');
    expect(arquivado.elemento.querySelectorAll('app-cabecalho-pagina button')).toHaveLength(0);
    expect(texto(arquivado.elemento, '.estado-vazio__titulo')).toBe('Nada na fatura de dezembro de 2026');
    expect(texto(arquivado.elemento, '.estado-vazio__texto')).toBe('Fecha em 03/12 e vence em 10/12, pelas datas do cartão.');
    TestBed.resetTestingModule();
    const ajustada = criar(of(fatura({ fechamento: '2026-11-11', fechamentoAjustado: true, itens: [] })));
    expect(texto(ajustada.elemento, '.estado-vazio__texto')).toBe('Fecha em 11/11 e vence em 10/12.');
  });

  it('o 404, o mês e o cartão inválidos viram alerta com o caminho de volta; os inválidos nem chegam à API', () => {
    const { elemento } = criar(throwError(() => new HttpErrorResponse({ status: 404, error: { detail: 'Cartão não encontrado.' } })));
    expect(texto(elemento, 'app-alerta .flux-alerta__titulo')).toBe('Cartão não encontrado.');
    expect(textos(elemento, 'app-alerta .flux-alerta__acoes a')).toEqual(['Ver cartões']);
    TestBed.resetTestingModule();
    const mesInvalido = criar(of(fatura({})), '2026-13');
    expect(mesInvalido.servico.fatura).not.toHaveBeenCalled();
    expect(mesInvalido.elemento.querySelector('h1')?.textContent).toBe('Fatura');
    expect(texto(mesInvalido.elemento, 'app-alerta .flux-alerta__titulo')).toBe('Mês inválido.');
    TestBed.resetTestingModule();
    const cartaoInvalido = criar(of(fatura({})), 'xyz', 'abc');
    expect(cartaoInvalido.servico.fatura).not.toHaveBeenCalled();
    expect(cartaoInvalido.elemento.querySelector('h1')?.textContent).toBe('Fatura');
    expect(texto(cartaoInvalido.elemento, 'app-alerta .flux-alerta__titulo')).toBe('Cartão não encontrado.');
  });

  it('o card do pagamento diz o pago, o pendente vencido, o disponível e os pagamentos; "Pagar fatura" avisa quando ela fica paga (decisões 30 a 34 e 38)',
    () => {
      const fechada = fatura({ mes: '2026-11', situacao: 'FECHADA', fechamento: '2026-11-03', vencimento: '2026-11-10', total: 265.2, pago: 100,
        quantidadePagamentos: 1, pendente: 165.2, disponivel: -120, usoDoLimite: 100,
        pagamentos: [{ id: 5, data: '2026-11-10', contaId: 1, contaNome: 'Banco Horizonte', valor: -100, origem: 'MANUAL' }] });
      const { fixture, elemento, servico, dialogo, fechamento, snackBar } = criar(of(fechada), '2026-11');
      expect(texto(elemento, '.fatura__nota-da-situacao')).toBe('Fechou em 03/11 · venceu em 10/11');
      expect(texto(elemento, '.fatura__nota-da-situacao .fatura__venceu')).toBe('venceu em 10/11');
      expect(textos(elemento, '.fatura__valores-do-pagamento .fatura__rotulo')).toEqual(['Pago', 'Pendente', 'Limite disponível']);
      expect(textos(elemento, '.fatura__valores-do-pagamento .fatura__nota')).toEqual(['1 pagamento', 'venceu em 10/11',
        'acima do limite em R$ 120,00']);
      expect(texto(elemento, '.fatura__valores-do-pagamento div:nth-child(2) .valor__texto')).toBe('R$ 165,20');
      const barra = elemento.querySelector('.fatura__barra') as HTMLElement;
      expect(barra.classList.contains('fatura__barra--estourada')).toBe(true);
      expect((barra.firstElementChild as HTMLElement).style.width).toBe('100%');
      // O topo (com pendente) tem "Pagar fatura" e "Marcar como paga fora do Flux"; a trilha, o disponível negativo.
      expect(textos(elemento, 'app-cabecalho-pagina button')).toEqual(['Ajustar fechamento', 'Novo estorno', 'Marcar como paga fora do Flux',
        'Pagar fatura', 'Mais', 'Nova compra']);
      expect(elemento.querySelector('.fatura__acoes-do-pagamento')).toBeNull();
      expect(texto(elemento, '.fatura__trilha')).toBe('Cartões › Cartão Horizonte · acima do limite em R$ 120,00');
      expect(texto(elemento, '.fatura__trilha .fatura__acima-do-limite')).toBe('acima do limite em R$ 120,00');
      expect(textos(elemento, '.fatura__pagamentos .fatura__cabecalho-da-lista > *')).toEqual(['Pagamentos', 'Saem da conta; não são despesa.']);
      expect(textos(elemento, '.fatura__pagamento-item .fatura__textos')).toEqual(['10/11 · Banco HorizonteLançado à mão']);
      expect(texto(elemento, '.fatura__pagamento-item app-valor .valor__texto')).toBe('−R$ 100,00');
      expect(elemento.querySelector('.fatura__pagamento-item app-valor')?.classList.contains('flux-valor--neutro')).toBe(true);

      elemento.querySelector<HTMLButtonElement>('.fatura__pagamento-item')?.click();
      const [componente, configuracao] = dialogo.open.mock.calls[0] as unknown as [unknown, { data: DadosDoDialogoDePagamento }];
      expect(componente).toBe(PagamentoDialogo);
      expect(configuracao.data).toEqual({ cartaoId: null, mes: null, pagamentoId: 5, hoje: '2026-11-12' });

      doTopo(elemento, 'Pagar fatura').click();
      expect((dialogo.open.mock.calls[1] as unknown as [unknown, { data: DadosDoDialogoDePagamento }])[1].data)
        .toEqual({ cartaoId: 7, mes: '2026-11', pagamentoId: null, hoje: '2026-11-12' });
      servico.fatura.mockReturnValue(of(fatura({ ...fechada, situacao: 'PAGA', pago: 265.2, pendente: 0 })));
      fechamento.next('pago');
      fixture.detectChanges();
      expect(snackBar.open).toHaveBeenCalledWith('Pagamento registrado. A fatura de novembro está paga.', 'Fechar', { duration: 5000 });
      expect(texto(elemento, '.fatura__situacao')).toBe('Paga');
    });

  it('a paga não se ajusta nem se paga; marcada fora do Flux, "Desmarcar"; com pago a mais, o alerta (decisões 31, 35 e 36)', () => {
    const { fixture, elemento, servico, snackBar } = criar(of(fatura({ mes: '2026-10', situacao: 'PAGA', fechamento: '2026-10-03',
      vencimento: '2026-10-10', total: 410, pago: 460, quantidadePagamentos: 1, pendente: 0, pagoAMais: 50, pagaFora: true })), '2026-10');
    expect(texto(elemento, '.fatura__situacao')).toBe('Paga');
    expect(textos(elemento, 'app-cabecalho-pagina button')).toEqual(['Novo estorno', 'Mais', 'Nova compra']);
    expect(elemento.querySelector('.fatura__nota-da-situacao .fatura__venceu')).toBeNull();
    expect(textos(elemento, '.fatura__valores-do-pagamento .fatura__nota').slice(0, 2)).toEqual(['paga fora do Flux', 'nada a pagar']);
    expect(texto(elemento, '.fatura__pagamento app-alerta .flux-alerta__titulo')).toBe('Pago a mais: R$ 50,00.');
    expect(texto(elemento, '.fatura__pagamento app-alerta .flux-alerta__texto')).toBe('Falta lançar alguma compra desta fatura?');
    expect(texto(elemento, '.fatura__lista .fatura__cabecalho-da-lista .fatura__nota')).toBe('Paga: só descrição, categoria e observação mudam.');
    expect(textos(elemento, '.fatura__acoes-do-pagamento button')).toEqual(['Desmarcar paga fora do Flux']);
    elemento.querySelector<HTMLButtonElement>('.fatura__acoes-do-pagamento button')?.click();
    fixture.detectChanges();
    expect(servico.desmarcarPagaFora).toHaveBeenCalledWith(7, '2026-10');
    expect(snackBar.open).toHaveBeenCalledWith('A fatura voltou a ter o pendente.', 'Fechar', { duration: 4000 });
  });

  it('a situação nos totais e na pílula: a paga diz "fechou em", com a pílula verde; as outras ficam como na 05a (decisão 34)', () => {
    const casos = [
      { parcial: { mes: '2026-10', situacao: 'PAGA', fechamento: '2026-10-03', vencimento: '2026-10-10', pago: 150, quantidadePagamentos: 1,
        pendente: 0 }, mes: '2026-10', nota: 'fechou em 03/10', pilula: 'Paga' },
      { parcial: { mes: '2026-11', situacao: 'FECHADA', fechamento: '2026-11-03', vencimento: '2026-11-10' }, mes: '2026-11', nota: 'fechou em 03/11',
        pilula: 'Fechada' },
      { parcial: {}, mes: '2026-12', nota: 'até agora; fecha em 03/12', pilula: 'Aberta' },
      { parcial: { mes: '2027-01', situacao: 'FUTURA', inicio: '2026-12-04', fechamento: '2027-01-03', vencimento: '2027-01-10', pendente: 0 },
        mes: '2027-01', nota: 'até agora; fecha em 03/01', pilula: 'Futura' },
    ] as const;
    for (const caso of casos) {
      TestBed.resetTestingModule();
      const { elemento } = criar(of(fatura(caso.parcial)), caso.mes);
      expect(textos(elemento, '.fatura__totais .fatura__nota')[2]).toBe(caso.nota);
      const pilula = elemento.querySelector('.fatura__situacao') as HTMLElement;
      expect(pilula.textContent?.trim()).toBe(caso.pilula);
      expect(pilula.classList.contains(`fatura__situacao--${caso.pilula.toLowerCase()}`)).toBe(true);
    }
  });

  it('"Desmarcar paga fora do Flux" e "Voltar ao fechamento normal" ficam travados durante o pedido: um pedido só (Correções do QA)',
    () => {
      const { fixture, elemento, servico } = criar(of(fatura({ mes: '2026-10', situacao: 'PAGA', fechamento: '2026-10-03',
        vencimento: '2026-10-10', total: 410, pago: 0, pendente: 0, pagaFora: true, fechamentoAjustado: true, fechamentoNormal: '2026-10-05' })),
      '2026-10');
      const desmarcar = new Subject<Fatura>();
      servico.desmarcarPagaFora.mockReturnValue(desmarcar.asObservable());
      const botao = elemento.querySelector<HTMLButtonElement>('.fatura__acoes-do-pagamento button') as HTMLButtonElement;
      botao.click();
      botao.click();
      fixture.detectChanges();
      expect(servico.desmarcarPagaFora).toHaveBeenCalledTimes(1);
      expect(botao.disabled).toBe(true);
      const voltar = elemento.querySelector<HTMLButtonElement>('.fatura__voltar-ao-normal') as HTMLButtonElement;
      expect(voltar.disabled).toBe(true);
      voltar.click();
      expect(servico.voltarAoFechamentoNormal).not.toHaveBeenCalled();
      desmarcar.next(fatura({}));
      desmarcar.complete();
      fixture.detectChanges();
      expect(servico.fatura).toHaveBeenCalledTimes(2);
    });

  it('a nota do limite disponível negativo fica vermelha, como a trilha; a positiva, não (decisão 38, Correções do QA)', () => {
    const acima = criar(of(fatura({ disponivel: -120, usoDoLimite: 100 })));
    const nota = Array.from(acima.elemento.querySelectorAll('.fatura__valores-do-pagamento .fatura__nota'))[2];
    expect(nota.textContent?.trim().replace(/\s+/g, ' ')).toBe('acima do limite em R$ 120,00');
    expect(nota.classList.contains('fatura__nota--acima')).toBe(true);
    TestBed.resetTestingModule();
    const dentro = criar();
    expect(dentro.elemento.querySelectorAll('.fatura__nota--acima')).toHaveLength(0);
  });

  it('"Marcar como paga fora do Flux" pede a confirmação com o pendente; na aberta, o antecipado entra na nota; a futura sem pagamento não tem o card',
    () => {
      const fechada = criar(of(fatura({ mes: '2026-09', situacao: 'FECHADA', fechamento: '2026-09-03', vencimento: '2026-09-10', total: 312,
        pago: 200, quantidadePagamentos: 1, pendente: 112 })), '2026-09');
      doTopo(fechada.elemento, 'Marcar como paga fora do Flux').click();
      const [componente, configuracao] = fechada.dialogo.open.mock.calls[0] as unknown as [unknown, { data: DadosDaConfirmacao }];
      expect(componente).toBe(ConfirmacaoDialogo);
      expect(configuracao.data.titulo).toBe('Marcar a fatura de setembro como paga?');
      expect(configuracao.data.confirmar).toBe('Marcar como paga');
      expect(JSON.stringify(configuracao.data.paragrafos).replace(/\u00a0| /g, ' ')).toBe('[["Use para faturas pagas antes de você usar o Flux: '
        + 'nenhuma conta muda, e os ",{"forte":"R$ 112,00"}," pendentes deixam de contar no limite. Dá para desmarcar depois."]]');
      fechada.fechamento.next(true);
      expect(fechada.snackBar.open).toHaveBeenCalledWith('Fatura marcada como paga fora do Flux.', 'Fechar', { duration: 4000 });
      expect(fechada.servico.fatura).toHaveBeenCalledTimes(2);
      TestBed.resetTestingModule();
      const antecipada = criar(of(fatura({ pago: 300, quantidadePagamentos: 1, pendente: 0, pagoAMais: 150 })));
      expect(texto(antecipada.elemento, '.fatura__nota-da-situacao')).toBe('Fecha em 21 dias · R$ 300,00 pagos');
      expect(textos(antecipada.elemento, '.fatura__acoes-do-pagamento button')).toEqual([]);
      expect(doTopo(antecipada.elemento, 'Pagar fatura')).toBeUndefined();
      expect(antecipada.elemento.querySelector('.fatura__pagamentos')).toBeNull();
      TestBed.resetTestingModule();
      const futura = criar(of(fatura({ mes: '2027-01', situacao: 'FUTURA', inicio: '2026-12-04' })), '2027-01');
      expect(futura.elemento.querySelector('.fatura__pagamento')).toBeNull();
    });
});
