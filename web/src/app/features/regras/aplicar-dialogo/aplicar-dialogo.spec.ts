import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { Aplicacao, GrupoDaAplicacao } from '../regras.service';
import { abrirAplicarRegras } from './aplicar-dialogo';

function grupo(parcial: Partial<GrupoDaAplicacao>): GrupoDaAplicacao {
  return { regraId: 31, posicao: 2, condicao: 'CONTEM', texto: 'uber', categoriaId: 5, categoriaNome: 'App de transporte',
    categoriaMaeNome: 'Transporte', descricao: null, quantidade: 1, manuais: 0, renomeados: 0, exemplos: [], ...parcial };
}

const aplicacao: Aplicacao = {
  total: 37,
  grupos: [
    grupo({ descricao: 'Uber', quantidade: 21, manuais: 1, renomeados: 19,
      exemplos: [{ data: '2026-12-02', descricao: 'PAG*UBERTRIP 1290', contaNome: 'Banco Horizonte', valor: -18.4 }] }),
    grupo({ regraId: 32, posicao: 3, texto: 'ifood', categoriaId: 3, categoriaNome: 'Delivery', categoriaMaeNome: 'Alimentação', quantidade: 16 }),
  ],
};

describe('AplicarDialogo', () => {
  let httpTesting: HttpTestingController;
  let resultado: number | undefined | null;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    TestBed.inject(MatDialog).closeAll();
  });

  function abrir(regraId: number | null): void {
    resultado = null;
    abrirAplicarRegras(TestBed.inject(MatDialog), regraId).subscribe((valor) => (resultado = valor));
    TestBed.tick();
  }

  function painel(): HTMLElement {
    return document.querySelector('app-aplicar-dialogo') as HTMLElement;
  }

  function textos(seletor: string): string[] {
    return Array.from(painel().querySelectorAll(seletor)).map((no) => no.textContent?.trim().replace(/\s+/g, ' ') ?? '');
  }

  function botoes(): HTMLButtonElement[] {
    return Array.from(painel().querySelectorAll<HTMLButtonElement>('button'));
  }

  function botao(texto: string): HTMLButtonElement {
    return botoes().find((item) => item.textContent?.trim() === texto) as HTMLButtonElement;
  }

  it('mostra os grupos marcados, conta pelos marcados e aplica só as regras marcadas, com dois cliques num pedido só', async () => {
    abrir(null);
    httpTesting.expectOne({ method: 'GET', url: '/api/regras/aplicacao' }).flush(aplicacao);
    TestBed.tick();
    expect(textos('.aplicar__texto')).toEqual(['37 lançamentos sem categoria casam com 2 regras. Transferências, excluídos e lançamentos de '
      + 'contas arquivadas ficam de fora, e nenhuma categoria escolhida é trocada.']);
    expect(textos('.aplicar__regra')).toEqual(['2ª · Contém uber → Transporte › App de transporte', '3ª · Contém ifood → Alimentação › Delivery']);
    expect(textos('.aplicar__nota')).toEqual(['e a descrição vira "Uber" · 1 lançado à mão: só a categoria muda']);
    expect(textos('.aplicar__quantidade')).toEqual(['21', '16']);
    expect(textos('.aplicar__mais')).toEqual(['… e mais 20', '… e mais 16']);
    const caixas = Array.from(painel().querySelectorAll<HTMLInputElement>('.aplicar__marcar input'));
    expect(caixas.map((caixa) => caixa.checked)).toEqual([true, true]);
    expect(caixas.every((caixa) => caixa.closest('label') !== null)).toBe(true);
    expect(botao('Aplicar a 37 lançamentos')).toBeDefined();

    caixas[1].click();
    TestBed.tick();
    expect(botao('Aplicar a 21 lançamentos')).toBeDefined();
    caixas[0].click();
    TestBed.tick();
    expect(botao('Escolha ao menos uma regra.').disabled).toBe(true);
    caixas[0].click();
    TestBed.tick();

    botao('Aplicar a 21 lançamentos').click();
    botao('Aplicar a 21 lançamentos').click();
    TestBed.tick();
    const pedido = httpTesting.expectOne({ method: 'POST', url: '/api/regras/aplicacao' });
    expect(pedido.request.body).toEqual({ regras: [31] });
    pedido.flush({ categorizados: 21 });
    TestBed.tick();
    await vi.waitFor(() => expect(resultado).toBe(21));
  });

  it('com a regra, pede só a dela; o erro ao carregar tem "Tentar de novo"; sem nada, avisa e só tem "Fechar"', async () => {
    abrir(31);
    httpTesting.expectOne((req) => req.url === '/api/regras/aplicacao' && req.params.get('regra') === '31')
      .flush(null, { status: 500, statusText: 'Erro' });
    TestBed.tick();
    expect(textos('.flux-alerta__titulo')).toEqual(['Não foi possível carregar a prévia.']);
    botao('Tentar de novo').click();
    httpTesting.expectOne((req) => req.url === '/api/regras/aplicacao').flush({ total: 0, grupos: [] });
    TestBed.tick();
    expect(textos('.flux-alerta__titulo')).toEqual(['Nada para aplicar']);
    expect(textos('.flux-alerta__texto')).toEqual(['Nenhum lançamento sem categoria casa com as suas regras.']);
    expect(botoes().map((item) => item.textContent?.trim())).toEqual(['Fechar']);
    botao('Fechar').click();
    TestBed.tick();
    await vi.waitFor(() => expect(resultado).toBeUndefined());
  });

  it('o erro ao aplicar aparece no diálogo, que continua aberto; a regra que sumiu (422) recarrega a prévia; "Cancelar" fecha sem aplicar',
    async () => {
      abrir(null);
      httpTesting.expectOne({ method: 'GET', url: '/api/regras/aplicacao' }).flush(aplicacao);
      TestBed.tick();
      botao('Aplicar a 37 lançamentos').click();
      TestBed.tick();
      httpTesting.expectOne({ method: 'POST', url: '/api/regras/aplicacao' }).flush(null, { status: 500, statusText: 'Erro' });
      TestBed.tick();
      expect(textos('[role="alert"] .flux-alerta__titulo')).toEqual(['Não foi possível aplicar as regras. Tente novamente.']);
      expect(textos('[role="alert"] .flux-alerta__texto')).toEqual([]);
      expect(botao('Aplicar a 37 lançamentos').disabled).toBe(false);
      expect(resultado).toBeNull();

      botao('Aplicar a 37 lançamentos').click();
      TestBed.tick();
      httpTesting.expectOne({ method: 'POST', url: '/api/regras/aplicacao' })
        .flush({ detail: 'Regra não encontrada.', codigo: 'REGRA_INVALIDA' }, { status: 422, statusText: 'Erro' });
      TestBed.tick();
      httpTesting.expectOne({ method: 'GET', url: '/api/regras/aplicacao' }).flush({ total: 16, grupos: [aplicacao.grupos[1]] });
      TestBed.tick();
      expect(textos('[role="alert"] .flux-alerta__titulo')).toEqual(['Regra não encontrada.']);
      expect(textos('[role="alert"] .flux-alerta__texto')).toEqual(['A prévia foi atualizada. Confira e aplique de novo.']);
      expect(textos('.aplicar__regra')).toEqual(['3ª · Contém ifood → Alimentação › Delivery']);
      expect(botao('Aplicar a 16 lançamentos').disabled).toBe(false);

      botao('Cancelar').click();
      TestBed.tick();
      await vi.waitFor(() => expect(resultado).toBeUndefined());
    });

  it('a recarga depois do 422 que volta vazia fica com "Nada para aplicar" e "Fechar", sem "A prévia foi atualizada"', async () => {
    abrir(null);
    httpTesting.expectOne({ method: 'GET', url: '/api/regras/aplicacao' }).flush({ total: 21, grupos: [aplicacao.grupos[0]] });
    TestBed.tick();
    botao('Aplicar a 21 lançamentos').click();
    TestBed.tick();
    httpTesting.expectOne({ method: 'POST', url: '/api/regras/aplicacao' })
      .flush({ detail: 'Regra não encontrada.', codigo: 'REGRA_INVALIDA' }, { status: 422, statusText: 'Erro' });
    TestBed.tick();
    httpTesting.expectOne({ method: 'GET', url: '/api/regras/aplicacao' }).flush({ total: 0, grupos: [] });
    TestBed.tick();
    expect(textos('.flux-alerta__titulo')).toEqual(['Nada para aplicar', 'Regra não encontrada.']);
    expect(painel().textContent).not.toContain('A prévia foi atualizada');
    expect(botoes().map((item) => item.textContent?.trim())).toEqual(['Fechar']);
    botao('Fechar').click();
    TestBed.tick();
    await vi.waitFor(() => expect(resultado).toBeUndefined());
  });

  it('a regra única excluída em outra aba: o 422 ao aplicar e o 422 na recarga deixam só o alerta da recarga e "Cancelar", sem a prévia velha',
    async () => {
      abrir(31);
      httpTesting.expectOne((req) => req.url === '/api/regras/aplicacao' && req.params.get('regra') === '31')
        .flush({ total: 21, grupos: [aplicacao.grupos[0]] });
      TestBed.tick();
      botao('Aplicar a 21 lançamentos').click();
      TestBed.tick();
      httpTesting.expectOne({ method: 'POST', url: '/api/regras/aplicacao' })
        .flush({ detail: 'Regra não encontrada.', codigo: 'REGRA_INVALIDA' }, { status: 422, statusText: 'Erro' });
      TestBed.tick();
      httpTesting.expectOne((req) => req.url === '/api/regras/aplicacao' && req.params.get('regra') === '31')
        .flush({ detail: 'Regra não encontrada.', codigo: 'REGRA_INVALIDA' }, { status: 422, statusText: 'Erro' });
      TestBed.tick();
      expect(textos('.flux-alerta__titulo')).toEqual(['Não foi possível carregar a prévia.']);
      expect(textos('.flux-alerta__texto')).toEqual(['Regra não encontrada.']);
      expect(painel().textContent).not.toContain('A prévia foi atualizada');
      expect(textos('.aplicar__texto')).toEqual([]);
      expect(botoes().map((item) => item.textContent?.trim())).toEqual(['Cancelar']);
      botao('Cancelar').click();
      TestBed.tick();
      await vi.waitFor(() => expect(resultado).toBeUndefined());
    });

  it('a regra que não existe mais: o 422 ao carregar mostra a mensagem da API, sem "Tentar de novo"', () => {
    abrir(99);
    httpTesting.expectOne((req) => req.url === '/api/regras/aplicacao' && req.params.get('regra') === '99')
      .flush({ detail: 'Regra não encontrada.', codigo: 'REGRA_INVALIDA' }, { status: 422, statusText: 'Erro' });
    TestBed.tick();
    expect(textos('.flux-alerta__titulo')).toEqual(['Não foi possível carregar a prévia.']);
    expect(textos('.flux-alerta__texto')).toEqual(['Regra não encontrada.']);
    expect(botoes().map((item) => item.textContent?.trim())).toEqual(['Cancelar']);
  });
});
