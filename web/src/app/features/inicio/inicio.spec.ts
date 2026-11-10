import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { AuthService } from '../../core/auth/auth.service';
import { Usuario } from '../../core/auth/sessao';
import { AGORA } from '../../core/tempo/agora';
import { CategoriasService, ListaDeCategorias } from '../categorias/categorias.service';
import { ContasService, ListaDeContas } from '../contas/contas.service';
import { ItemDaLista, LancamentosService, ListaDoMes } from '../lancamentos/lancamentos.service';
import { Inicio } from './inicio';

const comContas: ListaDeContas = {
  ativas: [
    { id: 1, nome: 'Banco Horizonte', tipo: 'CORRENTE', saldoInicial: 4218.4, dataSaldoInicial: '2026-10-01', cor: 'ROXO', saldoAtual: 4218.4, arquivadaEm: null },
    { id: 2, nome: 'Reserva de emergência', tipo: 'INVESTIMENTO', saldoInicial: 15000, dataSaldoInicial: '2026-08-15', cor: null, saldoAtual: 15000, arquivadaEm: null },
  ],
  arquivadas: [],
  totais: { geral: 19218.4, disponivel: 4218.4, investimentos: 15000 },
};

const categorias: ListaDeCategorias = { despesas: { ativas: [], arquivadas: [] }, receitas: { ativas: [], arquivadas: [] } };

const padaria: ItemDaLista = {
  tipo: 'DESPESA', id: 30, data: '2026-11-06', descricao: 'Padaria Central', valor: -18.5, observacao: null, contaId: 1,
  contaNome: 'Banco Horizonte', categoriaId: null, categoriaNome: null, categoriaMaeNome: null, categoriaArquivada: false,
  contaDestinoId: null, contaDestinoNome: null, direcao: null, futuro: false, somenteLeitura: false,
};

const novembro: ListaDoMes = {
  mes: '2026-11', hoje: '2026-11-06',
  totais: { entradas: 8491.28, quantidadeEntradas: 3, saidas: -3722.15, quantidadeSaidas: 11, resultado: 4769.13 },
  quantidadeLancamentos: 14, quantidadeTransferencias: 2, dias: [],
};

const semNada: ListaDoMes = { ...novembro, quantidadeLancamentos: 0, quantidadeTransferencias: 0,
  totais: { entradas: 0, quantidadeEntradas: 0, saidas: 0, quantidadeSaidas: 0, resultado: 0 } };

describe('Inicio', () => {
  function criar(resposta: () => Observable<ListaDeContas>, mes: ListaDoMes = novembro, ultimos: ItemDaLista[] = [padaria]) {
    const auth = { usuario: signal<Usuario | null>({ id: 1, nome: 'Dev', email: 'dev@flux.local' }) };
    const lancamentos = { listar: vi.fn(() => of(mes)), ultimos: vi.fn(() => of(ultimos)), ultimaConta: null };
    const fechamento = new Subject<unknown>();
    const dialogo = { open: vi.fn(() => ({ afterClosed: () => fechamento.asObservable() })) };
    TestBed.configureTestingModule({
      imports: [Inicio],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: AGORA, useValue: () => new Date('2026-11-07T02:30:00Z') },
        { provide: ContasService, useValue: { listar: resposta } },
        { provide: LancamentosService, useValue: lancamentos },
        { provide: CategoriasService, useValue: { listar: () => of(categorias) } },
        { provide: MatDialog, useValue: dialogo },
      ],
    });
    const fixture = TestBed.createComponent(Inicio);
    fixture.detectChanges();
    return { elemento: fixture.nativeElement as HTMLElement, lancamentos, dialogo, fechamento };
  }

  function textos(elemento: HTMLElement, seletor: string): string[] {
    return [...elemento.querySelectorAll(seletor)].map((no) => no.textContent?.trim().replace(/\s+/g, ' ') ?? '');
  }

  it('mostra a saudação, a data e os dois itens em breve', () => {
    const { elemento } = criar(() => of(comContas));
    expect(elemento.querySelector('h1')?.textContent).toBe('Olá, Dev');
    expect(elemento.querySelector('p.cabecalho__subtitulo')?.textContent).toBe('Sexta-feira, 6 de novembro de 2026');
    expect(textos(elemento, '.em-breve h3')).toEqual(['Importação de extratos', 'Cartões de crédito']);
    expect(textos(elemento, '.em-breve .flux-pilula')).toEqual(['Em breve', 'Em breve']);
  });

  it('com contas, mostra o saldo total, os investimentos e a lista, com cada conta levando aos lançamentos dela', () => {
    const { elemento } = criar(() => of(comContas));
    expect(elemento.querySelector('app-saldo-em-destaque .flux-cartao-destaque__valor .valor__texto')?.textContent).toBe('R$\u00a019.218,40');
    expect(elemento.querySelector('app-saldo-em-destaque .flux-cartao-destaque__rodape')?.textContent).toContain('2 contas ativas');
    expect(elemento.querySelector('.flux-cartao-destaque__pilula')?.textContent).toBe('Investimentos: R$\u00a015.000,00');
    expect(textos(elemento, '.inicio-conta__nome')).toEqual(['Banco Horizonte', 'Reserva de emergência']);
    expect([...elemento.querySelectorAll('a.inicio-conta__nome')].map((link) => link.getAttribute('href')))
      .toEqual(['/lancamentos?conta=1', '/lancamentos?conta=2']);
    expect(elemento.querySelector('a[href="/contas"]')?.textContent).toBe('Ver todas');
    expect(elemento.querySelector('app-estado-vazio')).toBeNull();
  });

  it('total e investimentos negativos passam pelo app-valor, em vermelho', () => {
    const { elemento } = criar(() => of({ ...comContas, totais: { geral: -320, disponivel: -200, investimentos: -120 } }));
    const total = elemento.querySelector('app-saldo-em-destaque .flux-cartao-destaque__valor app-valor');
    expect(total?.classList).toContain('flux-valor--negativo');
    expect(total?.querySelector('.valor__texto')?.textContent).toBe('−R$\u00a0320,00');
    const investimentos = elemento.querySelector('.flux-cartao-destaque__pilula app-valor');
    expect(investimentos?.classList).toContain('flux-valor--negativo');
    expect(investimentos?.querySelector('.valor__texto')?.textContent).toBe('−R$\u00a0120,00');
    expect(investimentos?.querySelector('.flux-visualmente-oculto')?.textContent).toBe('menos R$\u00a0120,00');
  });

  it('sem investimento, não mostra a pílula de investimentos', () => {
    const { elemento } = criar(() => of({ ...comContas, ativas: [comContas.ativas[0]] }));
    expect(elemento.querySelector('.flux-cartao-destaque__pilula')).toBeNull();
    expect(elemento.querySelector('app-saldo-em-destaque .flux-cartao-destaque__rodape')?.textContent).toContain('1 conta ativa');
  });

  it('mostra o mês e os últimos lançamentos, com os totais da API', () => {
    const { elemento, lancamentos } = criar(() => of(comContas));
    expect(lancamentos.listar).toHaveBeenCalledWith({ mes: '2026-11', conta: null, categoria: null, tipo: null, busca: '' });
    expect(textos(elemento, '.inicio-mes .card__titulo')).toEqual(['Novembro']);
    expect(textos(elemento, '.inicio-mes .valor__texto')).toEqual(['+R$ 4.769,13', '+R$ 8.491,28', '−R$ 3.722,15']);
    expect(textos(elemento, '.inicio-mes__rotulo')).toEqual(['Entradas · 3', 'Saídas · 11']);
    expect(textos(elemento, '.inicio-ultimos .card__titulo')).toEqual(['Últimos lançamentos']);
    expect(textos(elemento, '.linha__titulo')).toEqual(['Padaria Central']);
    expect(textos(elemento, '.linha__meta-texto.flux-so-desktop')).toEqual(['Banco Horizonte · hoje']);
    expect(elemento.querySelector('.inicio-ultimos a.flux-link')?.textContent).toBe('Ver todos');
  });

  it('com contas e nenhum lançamento, some o mês e "Últimos lançamentos" convida a lançar', () => {
    const { elemento, dialogo, fechamento, lancamentos } = criar(() => of(comContas), semNada, []);
    expect(elemento.querySelector('.inicio-mes')).toBeNull();
    expect(textos(elemento, '.inicio-ultimos__vazio')).toEqual(['Nenhum lançamento ainda.']);
    (elemento.querySelector('.inicio-ultimos__novo') as HTMLButtonElement).click();
    expect(dialogo.open).toHaveBeenCalledTimes(1);
    fechamento.next('salvo');
    expect(lancamentos.ultimos).toHaveBeenCalledTimes(2);
  });

  it('sem contas ativas, convida a cadastrar a primeira, sem o botão de lançar', () => {
    const { elemento } = criar(() => of({ ativas: [], arquivadas: [], totais: { geral: 0, disponivel: 0, investimentos: 0 } }));
    expect(elemento.querySelector('app-estado-vazio h2')?.textContent).toBe('Comece pelas suas contas');
    expect(elemento.querySelector('app-estado-vazio p')?.textContent).toContain('As categorias padrão já estão prontas para usar.');
    expect(elemento.querySelector('app-estado-vazio a')?.getAttribute('href')).toBe('/contas?nova=conta');
    expect(elemento.querySelector('app-saldo-em-destaque')).toBeNull();
    expect(textos(elemento, '.cabecalho__acoes button')).toEqual([]);
  });

  it('se a lista de contas falhar, avisa e continua mostrando o resto', () => {
    const { elemento } = criar(() => throwError(() => new HttpErrorResponse({ status: 500 })));
    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('Não foi possível carregar o saldo das contas.');
    expect(elemento.querySelectorAll('.em-breve h3')).toHaveLength(2);
  });
});
