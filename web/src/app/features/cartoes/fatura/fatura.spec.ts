import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, convertToParamMap, ParamMap, provideRouter, Router } from '@angular/router';
import { BehaviorSubject, Observable, of, Subject, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { AGORA } from '../../../core/tempo/agora';
import { CategoriasService, ListaDeCategorias } from '../../categorias/categorias.service';
import { AjusteDialogo } from '../ajuste-dialogo/ajuste-dialogo';
import { CartoesService, Fatura, ItemDaFatura } from '../cartoes.service';
import { CompraDialogo, DadosDoDialogoDeCompra } from '../compra-dialogo/compra-dialogo';
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
    total: 150, depois: 300, depoisAte: '2027-02', primeiroMes: '2026-11', ultimoMes: '2027-12',
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

  it('mostra o mês, a situação, a linha do tempo, os totais e os lançamentos (decisões 9 a 11)', () => {
    const { elemento, servico } = criar();
    expect(servico.fatura).toHaveBeenCalledWith(7, '2026-12');
    expect(elemento.querySelector('h1')?.textContent).toBe('Fatura de dezembro de 2026');
    expect(texto(elemento, '.fatura__trilha')).toBe('Cartões › Cartão Horizonte');
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
    expect(botoes.map((botao) => botao.textContent?.trim())).toEqual(['Ajustar fechamento', 'Novo estorno', 'Mais', 'Nova compra']);
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
});
