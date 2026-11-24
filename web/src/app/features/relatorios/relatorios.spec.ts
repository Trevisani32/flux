import { BreakpointObserver } from '@angular/cdk/layout';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRoute,
  convertToParamMap,
  ParamMap,
  provideRouter,
  Router,
} from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AGORA } from '../../core/tempo/agora';
import { RelatorioDoMes } from './relatorios.service';
import { Relatorios } from './relatorios';

const RELATORIO: RelatorioDoMes = {
  mes: '2026-11',
  mesAtual: '2026-11',
  primeiroMes: '2026-09',
  hoje: '2026-11-24',
  mesesDaMedia: ['2026-09', '2026-10'],
  saldo: {
    data: '2026-11-24',
    hoje: true,
    total: 900,
    investimentos: 100,
    contas: 2,
    contasDeInvestimento: 1,
  },
  receitas: {
    valor: 1000,
    quantidade: 1,
    noCartao: 0,
    media: 900,
    comparacao: { tipo: 'ACIMA', percentual: 11 },
  },
  despesas: {
    valor: 100,
    quantidade: 1,
    noCartao: 1,
    media: 80,
    comparacao: { tipo: 'ACIMA', percentual: 25 },
  },
  resultado: 900,
  categorias: [
    {
      id: 4,
      nome: 'Moradia',
      arquivada: false,
      direto: false,
      valor: 100,
      media: 80,
      comparacao: { tipo: 'ACIMA', percentual: 25 },
      subcategorias: [
        {
          id: 5,
          nome: 'Aluguel',
          arquivada: false,
          direto: false,
          valor: 80,
          media: 70,
          comparacao: { tipo: 'ACIMA', percentual: 14 },
          subcategorias: [],
        },
        {
          id: 4,
          nome: 'Direto em Moradia',
          arquivada: false,
          direto: true,
          valor: 20,
          media: 10,
          comparacao: { tipo: 'ACIMA', percentual: 100 },
          subcategorias: [],
        },
      ],
    },
    {
      id: null,
      nome: 'Sem categoria',
      arquivada: false,
      direto: false,
      valor: 10,
      media: 0,
      comparacao: { tipo: 'NOVO', percentual: 0 },
      subcategorias: [],
    },
    {
      id: 9,
      nome: 'Lazer',
      arquivada: true,
      direto: false,
      valor: 5,
      media: 4,
      comparacao: { tipo: 'ACIMA', percentual: 25 },
      subcategorias: [],
    },
  ],
  meses: [
    {
      mes: '2026-09',
      receitas: 800,
      despesas: 90,
      resultado: 710,
      dataDoSaldo: '2026-09-30',
      saldo: 600,
    },
    {
      mes: '2026-10',
      receitas: 900,
      despesas: 95,
      resultado: 805,
      dataDoSaldo: '2026-10-31',
      saldo: 705,
    },
    {
      mes: '2026-11',
      receitas: 1000,
      despesas: 100,
      resultado: 900,
      dataDoSaldo: '2026-11-24',
      saldo: 900,
    },
  ],
};

describe('Relatorios', () => {
  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    TestBed.resetTestingModule();
  });

  function criar(parametros: Record<string, string> = {}) {
    const url = new BehaviorSubject<ParamMap>(convertToParamMap(parametros));
    TestBed.configureTestingModule({
      imports: [Relatorios],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { queryParamMap: url.asObservable() } },
        { provide: BreakpointObserver, useValue: { observe: () => of({ matches: true }) } },
        { provide: AGORA, useValue: () => new Date('2026-11-24T12:00:00-03:00') },
      ],
    });
    const fixture = TestBed.createComponent(Relatorios);
    fixture.detectChanges();
    return {
      fixture,
      elemento: fixture.nativeElement as HTMLElement,
      http: TestBed.inject(HttpTestingController),
      router: TestBed.inject(Router),
      url,
    };
  }

  function responder(
    http: HttpTestingController,
    relatorio: RelatorioDoMes = RELATORIO,
    mes: string | null = null,
  ) {
    const pedido = http.expectOne((requisicao) => {
      return requisicao.url === '/api/relatorios/mes' && requisicao.params.get('mes') === mes;
    });
    pedido.flush(relatorio);
  }

  function botao(elemento: HTMLElement, texto: string): HTMLButtonElement {
    return Array.from(elemento.querySelectorAll<HTMLButtonElement>('button')).find((item) => {
      return item.textContent?.includes(texto) || item.getAttribute('aria-label') === texto;
    })!;
  }

  it('mostra os cards do mês, as categorias e os dois gráficos, com os links da lista', () => {
    const { fixture, elemento, http } = criar();
    responder(http);
    fixture.detectChanges();

    expect(elemento.querySelector('.relatorios__mes')?.getAttribute('role')).toBe('group');
    expect(elemento.querySelector('.relatorios__mes app-icone[nome="calendario"]')).not.toBeNull();
    expect(elemento.querySelector<HTMLAnchorElement>('.relatorios__kpi--receitas')?.href).toContain(
      'mes=2026-11',
    );
    expect(elemento.querySelector<HTMLAnchorElement>('.relatorios__kpi--receitas')?.href).toContain(
      'tipo=receita',
    );
    expect(elemento.querySelector<HTMLAnchorElement>('.relatorios__kpi--despesas')?.href).toContain(
      'tipo=despesa',
    );
    expect(elemento.querySelector<HTMLAnchorElement>('.categorias__nome')?.href).toContain(
      'categoria=4',
    );
    expect(elemento.textContent).toContain('Receitas');
    expect(elemento.textContent).toContain('Moradia');
    expect(elemento.querySelector('app-grafico-colunas')).not.toBeNull();
    expect(elemento.querySelector('app-grafico-linha')).not.toBeNull();
  });

  it('as setas andam entre o primeiro mês com dados e o mês atual, e o mês da URL fora da faixa mostra o atual', () => {
    const { fixture, elemento, http, router, url } = criar({ mes: '2026-08' });
    responder(http, RELATORIO, '2026-08');
    fixture.detectChanges();
    const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    expect(botao(elemento, 'Próximo mês').disabled).toBe(true);
    botao(elemento, 'Mês anterior').click();
    expect(navegar).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { mes: '2026-10' } }),
    );

    url.next(convertToParamMap({ mes: '2026-09' }));
    responder(http, { ...RELATORIO, mes: '2026-09' }, '2026-09');
    fixture.detectChanges();
    expect(botao(elemento, 'Mês anterior').disabled).toBe(true);
  });

  it('carregando outro mês, mantém o anterior esmaecido e descarta a resposta antiga', () => {
    const { fixture, elemento, http, url } = criar();
    responder(http);
    fixture.detectChanges();
    url.next(convertToParamMap({ mes: '2026-10' }));
    const pedidoAntigo = http.expectOne((requisicao) => requisicao.params.get('mes') === '2026-10');
    fixture.detectChanges();
    expect(elemento.querySelector('.relatorios__conteudo--carregando')).not.toBeNull();
    expect(elemento.textContent).toContain('R$\u00a01.000,00');

    url.next(convertToParamMap({ mes: '2026-09' }));
    const pedidoAtual = http.expectOne((requisicao) => requisicao.params.get('mes') === '2026-09');
    expect(pedidoAntigo.cancelled).toBe(true);
    pedidoAtual.flush({
      ...RELATORIO,
      mes: '2026-09',
      receitas: { ...RELATORIO.receitas, valor: 800 },
    });
    fixture.detectChanges();
    expect(elemento.textContent).toContain('R$\u00a0800,00');
    expect(elemento.querySelector('.relatorios__conteudo--carregando')).toBeNull();
  });

  it("o erro mostra 'Tentar de novo', que pede o mês de novo", () => {
    const { fixture, elemento, http, router } = criar({ mes: '2026-10' });
    const pedido = http.expectOne((requisicao) => requisicao.params.get('mes') === '2026-10');
    pedido.flush('falhou', { status: 500, statusText: 'Erro' });
    fixture.detectChanges();
    const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    expect(elemento.textContent).toContain('Tentar de novo');
    expect(elemento.querySelector('.relatorios__nome-do-mes')?.textContent).toContain(
      'outubro de 2026',
    );
    botao(elemento, 'Mês anterior').click();
    expect(navegar).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { mes: '2026-09' } }),
    );
    botao(elemento, 'Tentar de novo').click();
    responder(http, RELATORIO, '2026-10');
  });

  it('sem conta, mostra o estado vazio; o mês sem despesa mostra "Nada gasto em …"', () => {
    const { fixture, elemento, http } = criar();
    responder(http, {
      ...RELATORIO,
      primeiroMes: null,
      categorias: [],
      meses: [],
    });
    fixture.detectChanges();
    expect(elemento.textContent).toContain('Comece pelas suas contas');
    expect(elemento.querySelector('.relatorios__mes')).toBeNull();

    TestBed.resetTestingModule();
    const vazio = criar();
    responder(vazio.http, {
      ...RELATORIO,
      despesas: { ...RELATORIO.despesas, valor: 0, quantidade: 0, noCartao: 0 },
      categorias: [],
    });
    vazio.fixture.detectChanges();
    expect(vazio.elemento.textContent).toContain('Nada gasto em novembro');
  });

  it('antes do primeiro saldo inicial, o saldo é "—" e o gráfico do saldo avisa', () => {
    const { fixture, elemento, http } = criar();
    responder(http, {
      ...RELATORIO,
      saldo: {
        ...RELATORIO.saldo,
        data: '2026-09-30',
        hoje: false,
        total: null,
        investimentos: null,
        contas: 0,
      },
      meses: [{ ...RELATORIO.meses[0], saldo: null }],
    });
    fixture.detectChanges();
    expect(elemento.textContent).toContain('—');
    expect(elemento.textContent).toContain('Nenhuma conta até este mês.');
  });

  it("'Ver tabela' troca o gráfico pela tabela com os mesmos números, e 'Ver gráfico' volta", () => {
    const { fixture, elemento, http } = criar();
    responder(http);
    fixture.detectChanges();

    botao(elemento, 'Ver tabela').click();
    fixture.detectChanges();
    const tabela = elemento.querySelector('.relatorios__tabela')!;
    expect(tabela.textContent).toContain('R$\u00a01.000,00');
    expect(tabela.textContent).toContain('R$\u00a0100,00');
    expect(tabela.textContent).toContain('R$\u00a0900,00');
    expect(elemento.querySelector('app-grafico-colunas')).toBeNull();
    botao(elemento, 'Ver gráfico').click();
    fixture.detectChanges();
    expect(elemento.querySelector('app-grafico-colunas')).not.toBeNull();
  });

  it("as categorias expandem as subcategorias, com 'Direto em' sem link, 'Sem categoria' e a pílula 'Arquivada'", () => {
    const { fixture, elemento, http } = criar();
    responder(http);
    fixture.detectChanges();
    const controle = elemento.querySelector<HTMLButtonElement>('.categorias__abrir')!;
    const id = controle.getAttribute('aria-controls')!;
    const subcategorias = elemento.querySelector<HTMLUListElement>(`#${id}`)!;

    expect(subcategorias.hidden).toBe(true);
    expect(
      Array.from(subcategorias.querySelectorAll('a')).map((link) => link.textContent),
    ).not.toContain('Direto em Moradia');
    expect(elemento.textContent).toContain('Sem categoria');
    expect(elemento.textContent).toContain('Arquivada');
    expect(elemento.querySelector('.categorias__comparacao [aria-hidden="true"]')).not.toBeNull();
    controle.click();
    fixture.detectChanges();
    expect(subcategorias.hidden).toBe(false);
  });
});
