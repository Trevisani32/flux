import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { MatDialog } from '@angular/material/dialog';
import { describe, expect, it, vi } from 'vitest';

import { AGORA } from '../../core/tempo/agora';
import { FluxoDaOcorrencia } from '../recorrencias/fluxo-da-ocorrencia';
import { projecaoComDias, projecaoDeBancoHorizonte, projecaoDoPadrao, revistaAtrasada } from './dados-de-teste';
import { ProjecaoPagina } from './projecao';
import { ProjecaoService } from './projecao.service';

describe('ProjecaoPagina', () => {
  const parametros = new BehaviorSubject(convertToParamMap({ contas: '1' }));
  const mudou = new Subject<void>();
  const projetar = vi.fn();
  const confirmar = vi.fn();
  const pular = vi.fn();
  const navegar = vi.fn().mockResolvedValue(true);
  const abrir = vi.fn<(...args: unknown[]) => { afterClosed: () => unknown }>(() => ({ afterClosed: () => of(undefined) }));

  function preparar(projecao = projecaoDeBancoHorizonte) {
    TestBed.resetTestingModule();
    projetar.mockReset();
    confirmar.mockReset();
    pular.mockReset();
    navegar.mockClear();
    abrir.mockClear();
    parametros.next(convertToParamMap({ contas: '1' }));
    projetar.mockReturnValue(of(projecao));
    TestBed.configureTestingModule({
      providers: [
        { provide: AGORA, useValue: () => new Date('2026-11-25T15:00:00.000Z') },
        { provide: ProjecaoService, useValue: { projetar, aviso: vi.fn() } },
        { provide: FluxoDaOcorrencia, useValue: { mudou$: mudou, confirmar, pular } },
        { provide: MatDialog, useValue: { open: abrir } },
        { provide: ActivatedRoute, useValue: { queryParamMap: parametros.asObservable() } },
        {
          provide: Router,
          useValue: {
            navigate: navegar,
            createUrlTree: vi.fn(),
            serializeUrl: () => '/cartoes',
            events: of(),
            url: '/',
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(ProjecaoPagina);
    fixture.detectChanges();
    return fixture;
  }

  function texto(elemento: Element): string {
    return elemento.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  }

  it('Banco Horizonte sozinho: os números, o aviso, as pendências e o dia a dia', () => {
    const fixture = preparar();
    const elemento = fixture.nativeElement as HTMLElement;
    expect(projetar).toHaveBeenCalledWith(null, [1]);
    const botaoPadrao = elemento.querySelector<HTMLButtonElement>('.app-projecao__atalhos button');
    expect([botaoPadrao?.textContent?.trim(), botaoPadrao?.getAttribute('aria-pressed')]).toEqual(['Fim do mês que vem', 'true']);
    const chips = Array.from(elemento.querySelectorAll<HTMLButtonElement>('.app-projecao__chip'));
    expect(chips.map((chip) => [chip.textContent?.trim(), chip.getAttribute('aria-pressed')])).toEqual([
      ['Banco Aurora', 'false'],
      ['Banco Horizonte', 'true'],
      ['Carteira', 'false'],
      ['Conta conjunta', 'false'],
      ['Investimentos Lua', 'false'],
      ['Poupança Horizonte', 'false'],
    ]);
    expect(chips[1].classList.contains('app-projecao__chip--negativa')).toBe(true);
    const numeros = Array.from(elemento.querySelectorAll<HTMLElement>('.app-projecao__numero'));
    expect(numeros.map((numero) => numero.querySelector<HTMLElement>('app-valor .valor__texto')?.textContent))
      .toEqual(['R$\u00a01.240,35', '−R$\u00a02.397,95', 'R$\u00a01.485,46']);
    expect([
      numeros[0].querySelector(':scope > span')?.textContent,
      numeros[0].querySelector('small.flux-so-desktop')?.textContent,
      numeros[1].querySelector(':scope > span.flux-so-desktop')?.textContent,
      numeros[1].querySelector('small')?.textContent,
      numeros[2].querySelector(':scope > span')?.textContent,
      numeros[2].querySelector('small.flux-so-desktop')?.textContent,
    ]).toEqual([
      'Saldo hoje',
      'Banco Horizonte · quarta, 25/11',
      'Menor saldo previsto',
      'sexta, 04/12 · negativo por 4 dias',
      'No fim, 31/12',
      '36 dias depois de hoje',
    ]);
    const alerta = elemento.querySelector('.flux-alerta--erro') as HTMLElement;
    expect([alerta.querySelector('.flux-alerta__titulo')?.textContent, texto(alerta.querySelector('p') as HTMLElement)]).toEqual([
      'Banco Horizonte fica no negativo de 01/12 a 04/12.', 'O menor saldo é −R$ 2.397,95, na sexta, 04/12.',
    ]);
    expect(elemento.querySelector('.app-projecao__pendencias [acao] .valor__texto')?.textContent).toBe('−R$\u00a0508,70');
    const pendencias = Array.from(elemento.querySelectorAll<HTMLElement>('.app-projecao__pendencia'));
    expect(pendencias).toHaveLength(2);
    expect(pendencias.map((item) => [
      item.querySelector('.app-projecao__textos b')?.textContent,
      item.querySelector('.flux-pilula')?.textContent,
      item.querySelector('.app-projecao__textos span:not(.flux-pilula)')?.textContent,
      item.querySelector('app-valor .valor__texto')?.textContent,
      Array.from(item.querySelectorAll('button')).map((botao) => botao.textContent?.trim()),
    ])).toEqual([
      ['Fatura de novembro do Cartão Lua', 'Venceu', 'venceu em 05/11 · sai de Banco Horizonte', '−R$\u00a0412,30', ['Pagar fatura']],
      ['Conta de água Fictícia', 'Atrasada', 'desde 20/11', '−R$\u00a096,40', ['Confirmar', 'Pular']],
    ]);
    // "≈" só na que varia: a água nas pendências; no dia a dia, a luz de 30/11 e não a Internet de 26/11.
    expect(pendencias.map((item) => texto(item.querySelector('app-valor')?.parentElement as HTMLElement).startsWith('≈')))
      .toEqual([false, true]);
    expect(texto(elemento.querySelector('.app-projecao__rodape') as HTMLElement))
      .toBe('Saldo de hoje com as pendências: R$ 731,65. É o primeiro ponto do gráfico.');
    const dias = Array.from(elemento.querySelectorAll<HTMLElement>('.app-projecao__dia'));
    expect(dias).toHaveLength(17);
    expect([
      dias[0].querySelector('h3')?.textContent,
      Array.from(dias[0].querySelectorAll('.app-projecao__item > span')).map((item) => item.textContent),
      Array.from(dias[0].querySelectorAll('.app-projecao__item app-valor .valor__texto')).map((item) => item.textContent),
      dias[0].querySelector('.app-projecao__saldo-depois .valor__texto')?.textContent,
    ]).toEqual(['qui, 26/11', ['Previsto', 'Internet Fictícia'], ['−R$\u00a0119,90'], 'R$\u00a0611,75']);
    expect([dias[0], dias[3]].map((dia) => Array.from(dia.querySelectorAll('.app-projecao__valor')).map((valor) => texto(valor))))
      .toEqual([['−R$ 119,90menos R$ 119,90'], ['≈ −R$ 189,90menos R$ 189,90', '−R$ 89,90menos R$ 89,90']]);
    expect(dias[8].querySelector('h3')?.textContent).toBe('qui, 10/12');
    expect(Array.from(dias[8].querySelectorAll('.app-projecao__item > span')).map((item) => item.textContent))
      .toEqual([
        'Fatura',
        'Fatura de dezembro do Cartão Horizonte, com o previsto de Spotify Fictício (R$\u00a021,90)',
      ]);
    expect(elemento.textContent).not.toContain('Por conta');
  });

  it('a fatura vencida só com prevista de cartão: sem Pagar fatura, com Confirmar e Pular da prevista', () => {
    const fixture = preparar({ ...projecaoDeBancoHorizonte, pendencias: [revistaAtrasada] });
    const pendencia = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('.app-projecao__pendencia') as HTMLElement;
    expect([
      pendencia.querySelector('.app-projecao__textos b')?.textContent,
      pendencia.querySelector('.app-projecao__textos span:not(.flux-pilula)')?.textContent,
      Array.from(pendencia.querySelectorAll('.app-projecao__prevista-da-fatura > span')).map((item) => item.textContent),
      Array.from(pendencia.querySelectorAll('.app-projecao__prevista-da-fatura .valor__texto')).map((item) => item.textContent),
    ]).toEqual([
      'Fatura de novembro do Cartão Horizonte, com o previsto de Revista Fictícia (R$\u00a015,00)',
      'venceu em 10/11 · sai de Banco Horizonte',
      ['Revista Fictícia · compra prevista em 01/11'],
      ['−R$\u00a015,00'],
    ]);
    expect(pendencia.textContent).not.toContain('Venceu');
    expect(Array.from(pendencia.querySelectorAll('button')).map((botao) => botao.textContent?.trim())).toEqual(['Confirmar', 'Pular']);
    pendencia.querySelectorAll<HTMLButtonElement>('button')[0].click();
    pendencia.querySelectorAll<HTMLButtonElement>('button')[1].click();
    expect(confirmar).toHaveBeenCalledWith(revistaAtrasada.previstos[0].ocorrencia, '2026-11-25');
    expect(pular).toHaveBeenCalledWith(revistaAtrasada.previstos[0].ocorrencia);
  });

  it('os botões do período e as contas navegam', () => {
    const fixture = preparar();
    const elemento = fixture.nativeElement as HTMLElement;
    Array.from(elemento.querySelectorAll<HTMLButtonElement>('.app-projecao__atalhos button'))
      .find((botao) => botao.textContent?.trim() === '3 meses')?.click();
    expect(navegar).toHaveBeenLastCalledWith([], { relativeTo: expect.anything(), queryParams: { ate: '2027-02-25', contas: '1' } });
    Array.from(elemento.querySelectorAll<HTMLButtonElement>('.app-projecao__chip'))
      .find((botao) => botao.textContent?.trim() === 'Banco Aurora')?.click();
    expect(navegar).toHaveBeenLastCalledWith([], { relativeTo: expect.anything(), queryParams: { ate: null, contas: '2,1' } });
    Array.from(elemento.querySelectorAll<HTMLButtonElement>('.app-projecao__chip'))
      .find((botao) => botao.textContent?.trim() === 'Banco Horizonte')?.click();
    expect(navegar).toHaveBeenLastCalledWith([], { relativeTo: expect.anything(), queryParams: { ate: null, contas: '' } });
    projetar.mockReturnValueOnce(of({ ...projecaoDeBancoHorizonte, escolhidas: [], porConta: [], pendencias: [], dias: [] }));
    parametros.next(convertToParamMap({ contas: '' }));
    fixture.detectChanges();
    (elemento.querySelector('.app-projecao__sem-escolha button') as HTMLButtonElement).click();
    expect(navegar).toHaveBeenLastCalledWith([], { relativeTo: expect.anything(), queryParams: { ate: null, contas: null } });
    const data = elemento.querySelector<HTMLInputElement>('input[type="date"]') as HTMLInputElement;
    const chamadasAntes = navegar.mock.calls.length;
    data.value = '2027-11-26';
    data.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(elemento.querySelector('.app-projecao__erro')?.textContent).toBe('A projeção vai até 25/11/2027.');
    expect(navegar.mock.calls.length).toBe(chamadasAntes);
    parametros.next(convertToParamMap({ ate: '2027-02-25', contas: '1' }));
    fixture.detectChanges();
    (elemento.querySelector('.app-projecao__padrao') as HTMLButtonElement).click();
    expect(navegar).toHaveBeenLastCalledWith([], { relativeTo: expect.anything(), queryParams: { ate: '2027-02-25', contas: null } });
  });

  it('várias contas: Por conta, o aviso de cada conta e o rodapé', () => {
    const fixture = preparar({
      ...projecaoDoPadrao,
      contas: projecaoDoPadrao.contas.map((conta) => ({ ...conta, escolhida: conta.padrao })),
      resumo: { ...projecaoDoPadrao.resumo, menor: -2397.95, trechosNegativos: [{ de: '2026-12-01', ate: '2026-12-04' }] },
    });
    const elemento = fixture.nativeElement as HTMLElement;
    const linhas = Array.from(elemento.querySelectorAll('.app-projecao__por-conta tbody tr'));
    expect(linhas).toHaveLength(5);
    expect(Array.from(linhas[1].querySelectorAll('td')).map((celula) => [
      celula.querySelector('.flux-link')?.textContent ?? celula.querySelector('.valor__texto')?.textContent,
      celula.querySelectorAll('.valor__texto')[0]?.textContent,
      celula.querySelectorAll('.valor__texto')[1]?.textContent,
      celula.querySelector('small')?.textContent,
    ])).toEqual([
      ['Banco Horizonte', undefined, undefined, undefined],
      ['R$\u00a01.240,35', 'R$\u00a01.240,35', undefined, undefined],
      ['−R$\u00a02.397,95', '−R$\u00a02.397,95', undefined, '04/12'],
      ['R$\u00a01.485,46', 'R$\u00a01.485,46', undefined, undefined],
    ]);
    expect(linhas[1].querySelectorAll('td')[2].querySelector('app-valor')?.classList.contains('flux-valor--negativo')).toBe(true);
    expect(texto(elemento.querySelector('.app-projecao__por-conta .app-projecao__rodape') as HTMLElement))
      .toBe('O menor de cada conta é o dela; o total não é a soma dos menores. Investimentos Lua fica fora: marque para incluir.');
    const aviso = Array.from(elemento.querySelectorAll<HTMLElement>('.flux-alerta--atencao'))
      .find((item) => item.textContent?.includes('Banco Horizonte')) as HTMLElement;
    expect([
      aviso.querySelector('.flux-alerta__titulo')?.textContent,
      texto(aviso.querySelector('button') as HTMLElement),
    ]).toEqual(['Banco Horizonte fica no negativo de 01/12 a 04/12.', 'Ver só Banco Horizonte']);
    aviso.querySelector<HTMLButtonElement>('button')?.click();
    expect(navegar).toHaveBeenLastCalledWith([], { relativeTo: expect.anything(), queryParams: { ate: null, contas: '1' } });
  });

  it('Ver tabela alterna o gráfico e a tabela', () => {
    const fixture = preparar();
    const elemento = fixture.nativeElement as HTMLElement;
    const botao = elemento.querySelector<HTMLButtonElement>('.app-projecao__alternar') as HTMLButtonElement;
    expect(botao.getAttribute('aria-pressed')).toBe('false');
    botao.click();
    fixture.detectChanges();
    expect(botao.getAttribute('aria-pressed')).toBe('true');
    const linhas = Array.from(elemento.querySelectorAll<HTMLElement>('.app-projecao__grafico tbody tr'));
    expect(linhas.slice(0, 2).map((linha) => {
      return Array.from(linha.querySelectorAll('td'))
        .map((celula) => celula.querySelector('.valor__texto')?.textContent ?? celula.textContent?.trim());
    })).toEqual([
      ['Hoje, 25/11', '−R$\u00a0508,70 (pendências)', 'R$\u00a0731,65'],
      ['qui, 26/11', '−R$\u00a0119,90', 'R$\u00a0611,75'],
    ]);
    botao.click();
    fixture.detectChanges();
    expect(botao.getAttribute('aria-pressed')).toBe('false');
    expect(elemento.querySelector('app-grafico-degraus')).not.toBeNull();
  });

  it('Pagar fatura, Confirmar e Pular chamam os fluxos e recarregam', () => {
    abrir.mockReturnValue({ afterClosed: () => of('pago') });
    const fixture = preparar();
    const elemento = fixture.nativeElement as HTMLElement;
    Array.from(elemento.querySelectorAll<HTMLButtonElement>('button'))
      .find((botao) => botao.textContent?.includes('Pagar fatura'))?.click();
    expect(abrir).toHaveBeenCalledWith(expect.anything(), expect.objectContaining({
      data: { cartaoId: 7, mes: '2026-11', pagamentoId: null, hoje: '2026-11-25' },
    }));
    expect(projetar).toHaveBeenCalledTimes(2);
    const agua = Array.from(elemento.querySelectorAll<HTMLElement>('.app-projecao__pendencia'))
      .find((item) => item.textContent?.includes('Conta de água')) as HTMLElement;
    agua.querySelectorAll<HTMLButtonElement>('button')[0].click();
    agua.querySelectorAll<HTMLButtonElement>('button')[1].click();
    expect(confirmar).toHaveBeenCalledWith(projecaoDeBancoHorizonte.pendencias[1].ocorrencia, '2026-11-25');
    expect(pular).toHaveBeenCalledWith(projecaoDeBancoHorizonte.pendencias[1].ocorrencia);
    mudou.next();
    expect(projetar).toHaveBeenCalledTimes(3);
  });

  it('sem conta escolhida e sem conta nenhuma', () => {
    const vazio = preparar({ ...projecaoDeBancoHorizonte, escolhidas: [], porConta: [], pendencias: [], dias: [] });
    const vazioElemento = vazio.nativeElement as HTMLElement;
    expect([
      vazioElemento.querySelector('.app-projecao__sem-escolha h2')?.textContent,
      vazioElemento.querySelector('.app-projecao__sem-escolha p')?.textContent,
      vazioElemento.querySelector('.app-projecao__sem-escolha button')?.textContent?.trim(),
    ]).toEqual(['Nenhuma conta escolhida', 'Escolha ao menos uma conta.', 'Voltar ao padrão']);
    TestBed.resetTestingModule();
    const semConta = preparar({ ...projecaoDeBancoHorizonte, contas: [], escolhidas: [], porConta: [], pendencias: [], dias: [] });
    const semContaElemento = semConta.nativeElement as HTMLElement;
    expect([
      semContaElemento.querySelector('.estado-vazio__titulo')?.textContent,
      semContaElemento.querySelector('.estado-vazio__texto')?.textContent,
      semContaElemento.querySelector('a')?.textContent?.trim(),
    ]).toEqual([
      'Nenhuma conta para projetar',
      'Cadastre uma conta para ver o saldo dos próximos dias.',
      'Cadastrar conta',
    ]);
  });

  it('o 400 e o erro', () => {
    const fixture = preparar();
    const elemento = fixture.nativeElement as HTMLElement;
    projetar.mockReturnValueOnce(throwError(() => new HttpErrorResponse({
      status: 400,
      error: { campos: [{ campo: 'ate', mensagem: 'A projeção vai até 25/11/2027.' }] },
    }))).mockReturnValue(of(projecaoDeBancoHorizonte));
    parametros.next(convertToParamMap({ ate: '2027-11-25', contas: '1' }));
    fixture.detectChanges();
    expect([
      elemento.querySelector('.flux-alerta__titulo')?.textContent,
      elemento.querySelector('.flux-alerta button')?.textContent?.trim(),
    ]).toEqual(['A projeção vai até 25/11/2027.', 'Voltar ao padrão']);
    elemento.querySelector<HTMLButtonElement>('.flux-alerta button')?.click();
    expect(navegar).toHaveBeenCalledWith([], { relativeTo: expect.anything(), queryParams: {} });
    projetar.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 }))).mockReturnValue(of(projecaoDeBancoHorizonte));
    parametros.next(convertToParamMap({ contas: '1' }));
    fixture.detectChanges();
    expect([
      elemento.querySelector('.flux-alerta__titulo')?.textContent,
      elemento.querySelector('.flux-alerta button')?.textContent?.trim(),
    ]).toEqual(['Não foi possível calcular a projeção.', 'Tentar de novo']);
    elemento.querySelector<HTMLButtonElement>('.flux-alerta button')?.click();
    expect(projetar).toHaveBeenCalledTimes(4);
  });

  it('a fatura que ficou fora', () => {
    const fixture = preparar({
      ...projecaoDeBancoHorizonte,
      faturasFora: [{ cartaoId: 9, cartaoNome: 'Cartão Sol', contaId: 8, contaNome: 'Conta antiga', meses: ['2026-12'], valor: 300 }],
    });
    const alerta = Array.from((fixture.nativeElement as HTMLElement)
      .querySelectorAll<HTMLElement>('.flux-alerta--atencao'))[0];
    expect([
      alerta.querySelector('.flux-alerta__titulo')?.textContent,
      texto(alerta.querySelector('p') as HTMLElement),
      alerta.querySelector('a')?.getAttribute('href'),
    ]).toEqual([
      'Uma fatura ficou fora',
      'A fatura de dezembro do Cartão Sol (R$ 300,00) sai de Conta antiga, que está arquivada: '
        + 'ela ficou fora da projeção. Troque a conta de pagamento do cartão.',
      '/cartoes',
    ]);
  });

  it('o dia a dia corta em 6 no celular e em 31 no desktop', () => {
    const fixture = preparar();
    const elemento = fixture.nativeElement as HTMLElement;
    const dias = Array.from(elemento.querySelectorAll<HTMLElement>('.app-projecao__dia'));
    expect(dias).toHaveLength(17);
    expect(dias.map((dia) => dia.classList.contains('app-projecao__dia--no-celular')))
      .toEqual([...Array<boolean>(6).fill(false), ...Array<boolean>(11).fill(true)]);
    expect([
      texto(elemento.querySelector('.flux-so-celular.flux-link') as HTMLElement),
      elemento.querySelector('.flux-so-desktop.flux-link'),
    ]).toEqual(['e mais 11 dias até 31/12', null]);
    elemento.querySelector<HTMLButtonElement>('.flux-so-celular.flux-link')?.click();
    fixture.detectChanges();
    expect(Array.from(elemento.querySelectorAll('.app-projecao__dia--no-celular'))).toEqual([]);
    projetar.mockReturnValue(of(projecaoComDias(130)));
    parametros.next(convertToParamMap({ ate: '2027-11-25', contas: '1' }));
    fixture.detectChanges();
    expect(elemento.querySelectorAll('.app-projecao__dia')).toHaveLength(31);
    expect([
      texto(elemento.querySelector('.flux-so-desktop.flux-link') as HTMLElement),
      texto(elemento.querySelector('.flux-so-celular.flux-link') as HTMLElement),
    ]).toEqual(['e mais 99 dias até 25/11/2027', 'e mais 124 dias até 25/11/2027']);
    elemento.querySelector<HTMLButtonElement>('.flux-so-desktop.flux-link')?.click();
    fixture.detectChanges();
    expect(elemento.querySelectorAll('.app-projecao__dia')).toHaveLength(130);
    expect(elemento.querySelectorAll('.app-projecao__dia--no-celular')).toHaveLength(0);
    expect(elemento.querySelector('.flux-so-desktop.flux-link')).toBeNull();
  });
});
