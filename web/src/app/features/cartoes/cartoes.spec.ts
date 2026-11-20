import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { Observable, Subject, of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { ConfirmacaoDialogo, DadosDaConfirmacao } from '../../shared/confirmacao/confirmacao-dialogo';
import { Conta, ContasService, ListaDeContas } from '../contas/contas.service';
import { CartaoDialogo } from './cartao-dialogo/cartao-dialogo';
import { Cartao, CartoesService, ListaDeCartoes } from './cartoes.service';
import { Cartoes } from './cartoes';

function conta(parcial: Partial<Conta>): Conta {
  return { id: 1, nome: 'Banco Horizonte', tipo: 'CORRENTE', saldoInicial: 0, dataSaldoInicial: '2026-10-01', cor: null, saldoAtual: 0, arquivadaEm: null,
    ...parcial };
}

function cartao(parcial: Partial<Cartao>): Cartao {
  return {
    id: 7, nome: 'Cartão Horizonte', limite: 5000, diaFechamento: 3, diaVencimento: 10, contaPagamentoId: 1, contaPagamentoNome: 'Banco Horizonte',
    contaPagamentoArquivada: false, cor: 'OURO', arquivadoEm: null,
    faturaAberta: { mes: '2026-12', inicio: '2026-11-04', fechamento: '2026-12-03', vencimento: '2026-12-10', total: 1113.69, lancamentos: 7 },
    proximaFatura: { mes: '2027-01', inicio: '2026-12-04', fechamento: '2027-01-03', vencimento: '2027-01-10', total: 631.33, lancamentos: 4 },
    emAberto: 4267.35, lancamentosEmAberto: 30, ...parcial,
  };
}

const contas: ListaDeContas = { ativas: [conta({})], arquivadas: [], totais: { geral: 0, disponivel: 0, investimentos: 0 } };

const lista: ListaDeCartoes = {
  ativos: [cartao({}), cartao({ id: 8, nome: 'Cartão Lua', diaFechamento: 25, diaVencimento: 5, contaPagamentoNome: 'Poupança antiga',
    contaPagamentoArquivada: true, lancamentosEmAberto: 0, emAberto: 0 })],
  arquivados: [cartao({ id: 9, nome: 'Cartão Antigo', arquivadoEm: '2026-11-01T15:00:00Z' })],
};

describe('Cartoes', () => {
  function criar(cartoes: ListaDeCartoes = lista, listaDeContas: ListaDeContas = contas, novo: string | null = null) {
    const servico = {
      listar: vi.fn(() => of(cartoes)),
      arquivar: vi.fn(() => of(cartao({}))),
      reativar: vi.fn((): Observable<Cartao> => of(cartao({}))),
      excluir: vi.fn(() => of(undefined)),
    };
    const fechamentos: Subject<unknown>[] = [];
    const dialogo = { open: vi.fn((): { afterClosed: () => Observable<unknown> } => {
      const fechamento = new Subject<unknown>();
      fechamentos.push(fechamento);
      return { afterClosed: () => fechamento.asObservable() };
    }) };
    const acaoDoAviso = new Subject<void>();
    const snackBar = { open: vi.fn(() => ({ onAction: () => acaoDoAviso.asObservable() })) };
    TestBed.configureTestingModule({
      imports: [Cartoes],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(novo === null ? {} : { novo }) } } },
        { provide: CartoesService, useValue: servico },
        { provide: ContasService, useValue: { listar: () => of(listaDeContas) } },
        { provide: MatDialog, useValue: dialogo },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    });
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(Cartoes);
    fixture.detectChanges();
    return { componente: fixture.componentInstance, elemento: fixture.nativeElement as HTMLElement, servico, dialogo, fechamentos, snackBar,
      acaoDoAviso, navegar };
  }

  function textos(elemento: HTMLElement, seletor: string): string[] {
    return Array.from(elemento.querySelectorAll(seletor)).map((item) => item.textContent?.trim().replace(/\s+/g, ' ') ?? '');
  }

  it('mostra um card por cartão ativo, com os dias, a fatura aberta, o limite e o aviso da conta arquivada', () => {
    const { elemento } = criar();
    expect(elemento.querySelector('h1')?.textContent).toBe('Cartões');
    expect(elemento.querySelector('.cabecalho__subtitulo')?.textContent).toBe('2 cartões ativos. A compra entra na fatura pelo dia de fechamento.');
    expect(textos(elemento, '.cartoes__nome')).toEqual(['Cartão Horizonte', 'Cartão Lua']);
    expect(textos(elemento, '.cartoes__meta .flux-so-desktop')).toEqual(['Fecha dia 3 · vence dia 10 · paga com Banco Horizonte',
      'Fecha dia 25 · vence dia 5 · paga com Poupança antiga']);
    expect(textos(elemento, '.cartoes__rotulo')[0]).toBe('Fatura de dezembro Aberta');
    expect(textos(elemento, '.cartoes__datas')[0]).toBe('fecha 03/12 · vence 10/12');
    expect(textos(elemento, '.cartoes__total')[0]).toBe('R$ 1.113,69');
    expect(elemento.querySelector('.cartoes__ver')?.getAttribute('href')).toBe('/cartoes/7/faturas/2026-12');
    expect(textos(elemento, '.cartoes__rodape > span')).toEqual(['Limite R$ 5.000,00', 'Próxima: janeiro, R$ 631,33', 'Limite R$ 5.000,00',
      'Conta de pagamento arquivada: escolha outra']);
    expect(textos(elemento, '.cartoes__aviso')).toEqual(['Conta de pagamento arquivada: escolha outra']);
    expect(textos(elemento, '.flux-arquivadas__alternar')).toEqual(['Arquivados 1']);
    expect(textos(elemento, '.cartoes__nome-arquivado')).toEqual(['Cartão Antigo']);
  });

  it('sem cartão convida a cadastrar; sem conta que pague, manda cadastrar uma conta primeiro', () => {
    const vazio = criar({ ativos: [], arquivados: [] });
    expect(textos(vazio.elemento, '.estado-vazio__titulo')).toEqual(['Nenhum cartão ainda']);
    expect(textos(vazio.elemento, '.cabecalho__acoes button')).toEqual(['Novo cartão']);
    TestBed.resetTestingModule();
    const soInvestimento = criar({ ativos: [], arquivados: [] }, { ...contas, ativas: [conta({ tipo: 'INVESTIMENTO' })] });
    expect(textos(soInvestimento.elemento, '.estado-vazio__titulo')).toEqual(['Cadastre uma conta primeiro']);
    expect(soInvestimento.elemento.querySelector('app-estado-vazio a')?.getAttribute('href')).toBe('/contas?nova=conta');
    expect(textos(soInvestimento.elemento, '.cabecalho__acoes button')).toEqual([]);
  });

  it('abre o Novo cartão pela URL do Início e o diálogo com as contas; salvo, recarrega e avisa', () => {
    const { dialogo, fechamentos, servico, snackBar, navegar } = criar(lista, contas, 'cartao');
    expect(navegar).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { novo: null }, replaceUrl: true }));
    const [componente, configuracao] = dialogo.open.mock.calls[0] as unknown as [unknown, { data: { cartao: Cartao | null; contas: ListaDeContas } }];
    expect(componente).toBe(CartaoDialogo);
    expect(configuracao.data).toEqual({ cartao: null, contas });
    fechamentos[0].next(cartao({}));
    expect(servico.listar).toHaveBeenCalledTimes(2);
    expect(snackBar.open).toHaveBeenCalledWith('Cartão criado.', 'Fechar', { duration: 4000 });
  });

  it('arquivar sem lançamentos em aberto arquiva direto com Desfazer; com eles, a confirmação diz quanto (decisão 5)', () => {
    const { componente, servico, snackBar, acaoDoAviso, dialogo } = criar();
    componente.arquivar(lista.ativos[1]);
    expect(servico.arquivar).toHaveBeenCalledWith(8);
    expect(snackBar.open).toHaveBeenCalledWith('Cartão arquivado.', 'Desfazer', { duration: 8000 });
    acaoDoAviso.next();
    expect(servico.reativar).toHaveBeenCalledWith(8);

    componente.arquivar(lista.ativos[0]);
    const [componenteDoDialogo, configuracao] = dialogo.open.mock.lastCall as unknown as [unknown, { data: DadosDaConfirmacao }];
    expect(componenteDoDialogo).toBe(ConfirmacaoDialogo);
    expect(configuracao.data.titulo).toBe('Arquivar Cartão Horizonte?');
    const [primeiro, quanto, resto] = configuracao.data.paragrafos[0];
    expect(primeiro).toBe('Ele tem ');
    expect((quanto as { forte: string }).forte.replace(/\s/g, ' ')).toBe('R$ 4.267,35');
    expect(resto).toBe(' em faturas abertas e futuras. Arquivado, sai dos seletores, e as compras e faturas dele ficam só para leitura até você reativar.');
    expect(configuracao.data.confirmar).toBe('Arquivar cartão');
  });

  it('o bloqueio da exclusão diz as compras e leva a arquivar', () => {
    const { componente, dialogo, fechamentos } = criar();
    componente.excluir(lista.ativos[0]);
    const bloqueio = (dialogo.open.mock.calls[0] as unknown as [unknown, { data: DadosDaConfirmacao }])[1].data.bloqueio;
    expect(bloqueio?.codigo).toBe('CARTAO_EM_USO');
    expect(bloqueio?.titulo).toBe('Este cartão não pode ser excluído');
    expect(bloqueio?.paragrafo('12 compras e 1 estorno')).toEqual([{ forte: 'Cartão Horizonte' },
      ' tem 12 compras e 1 estorno. Arquive o cartão para tirá-lo dos seletores.']);
    expect(bloqueio?.alternativa).toBe('Arquivar cartão');
    fechamentos[0].next('alternativa');
    expect((dialogo.open.mock.calls[1] as unknown as [unknown, { data: DadosDaConfirmacao }])[1].data.titulo).toBe('Arquivar Cartão Horizonte?');
  });
});
