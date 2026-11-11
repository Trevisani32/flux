import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { Categoria, ListaDeCategorias } from '../categorias/categorias.service';
import { Conta, ListaDeContas } from '../contas/contas.service';
import { abrirDialogoDeLancamento } from '../lancamentos/lancamento-dialogo/lancamento-dialogo';
import { ItemDaLista } from '../lancamentos/lancamentos.service';
import { AplicarDialogo } from './aplicar-dialogo/aplicar-dialogo';
import { FluxoDeRegras } from './fluxo-de-regras';
import { RegraDialogo } from './regra-dialogo/regra-dialogo';
import { Regra, SugestaoDeRegra } from './regras.service';

function regra(parcial: Partial<Regra>): Regra {
  return { id: 40, posicao: 1, condicao: 'CONTEM', texto: 'PIX QRS PADARIA', contaId: null, contaNome: null, contaArquivada: false,
    natureza: 'DESPESA', categoriaId: 2, categoriaNome: 'Padaria', categoriaMaeNome: 'Alimentação', categoriaArquivada: false,
    descricao: null, pausada: false, ...parcial };
}

const sugestao: SugestaoDeRegra = { condicao: 'CONTEM', texto: 'PIX QRS PADARIA', categoriaId: 2, categoriaNome: 'Padaria',
  categoriaMaeNome: 'Alimentação', natureza: 'DESPESA', descricao: null, deDescricao: 'PIX QRS PADARIA CE26/11' };

/** O texto do aviso curto aberto e o botão dele. */
async function aviso(): Promise<{ texto: string; acao: HTMLButtonElement }> {
  return vi.waitFor(() => {
    const container = document.querySelector('.mat-mdc-snack-bar-container') as HTMLElement;
    expect(container).not.toBeNull();
    return { texto: container.querySelector('simple-snack-bar .mat-mdc-snack-bar-label')?.textContent?.trim() ?? '',
      acao: container.querySelector('button') as HTMLButtonElement };
  });
}

describe('FluxoDeRegras', () => {
  let httpTesting: HttpTestingController;

  afterEach(() => {
    httpTesting.verify();
    TestBed.inject(MatSnackBar).dismiss();
  });

  /** O MatDialog falso devolve, a cada abertura, o próximo resultado da fila. */
  function criar(resultados: unknown[]) {
    const dialogo = { open: vi.fn(() => ({ afterClosed: () => of(resultados.shift()) })) };
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), { provide: MatDialog, useValue: dialogo }] });
    httpTesting = TestBed.inject(HttpTestingController);
    return { fluxo: TestBed.inject(FluxoDeRegras), dialogo };
  }

  it('criada a regra, o aviso diz quantos sem categoria casam; "Aplicar" abre a prévia só dela e, aplicada, avisa e emite (decisão 19)', async () => {
    const { fluxo, dialogo } = criar([{ tipo: 'criada', regra: regra({}) }, 4]);
    const aplicadas: number[] = [];
    fluxo.aplicadas$.subscribe((quantos) => aplicadas.push(quantos));
    let criada: Regra | undefined;
    fluxo.novaRegra(sugestao).subscribe((valor) => (criada = valor));
    expect(dialogo.open).toHaveBeenCalledWith(RegraDialogo, expect.objectContaining({ data: { regra: null, sugestao } }));
    expect(criada).toEqual(regra({}));
    httpTesting.expectOne((req) => req.url === '/api/regras/aplicacao' && req.params.get('regra') === '40').flush({ total: 4, grupos: [] });

    const criadaAviso = await aviso();
    expect(criadaAviso.texto).toBe('Regra criada. 4 lançamentos sem categoria casam com ela.');
    expect(criadaAviso.acao.textContent?.trim()).toBe('Aplicar');
    criadaAviso.acao.click();
    await vi.waitFor(() => expect(dialogo.open).toHaveBeenCalledTimes(2));
    expect(dialogo.open).toHaveBeenLastCalledWith(AplicarDialogo, expect.objectContaining({ data: { regraId: 40 } }));
    await vi.waitFor(async () => expect((await aviso()).texto).toBe('4 lançamentos categorizados.'));
    expect(aplicadas).toEqual([4]);
  });

  it('sem nenhum casando, só "Regra criada."; se a contagem falha, o aviso diz; editar avisa "Regra salva." e "Regra excluída."', async () => {
    const { fluxo } = criar([{ tipo: 'criada', regra: regra({}) }, { tipo: 'criada', regra: regra({ id: 41 }) }, { tipo: 'salva', regra: regra({}) },
      { tipo: 'excluida' }, undefined]);
    fluxo.novaRegra().subscribe();
    httpTesting.expectOne((req) => req.url === '/api/regras/aplicacao').flush({ total: 0, grupos: [] });
    expect((await aviso()).texto).toBe('Regra criada.');
    fluxo.novaRegra().subscribe();
    httpTesting.expectOne((req) => req.url === '/api/regras/aplicacao').flush(null, { status: 500, statusText: 'Erro' });
    await vi.waitFor(async () => expect((await aviso()).texto)
      .toBe('Regra criada. Não foi possível contar os lançamentos sem categoria que casam com ela.'));
    expect((await aviso()).acao.textContent?.trim()).toBe('Fechar');
    fluxo.editar(regra({})).subscribe();
    await vi.waitFor(async () => expect((await aviso()).texto).toBe('Regra salva.'));
    fluxo.editar(regra({})).subscribe();
    await vi.waitFor(async () => expect((await aviso()).texto).toBe('Regra excluída.'));
    let fechouSemNada: unknown = 'nada';
    fluxo.aplicar(null).subscribe((valor) => (fechouSemNada = valor));
    expect(fechouSemNada).toBeUndefined();
  });

  it('oferecerRegra: com sugestão, o aviso oferece "Criar regra", que abre a Nova regra preenchida; sem sugestão ou com erro, nada', async () => {
    const { fluxo, dialogo } = criar([undefined]);
    const escolha = { contaId: 1, descricaoBanco: 'PIX QRS PADARIA CE26/11', descricao: 'PIX QRS PADARIA CE26/11', categoriaId: 2 };
    fluxo.oferecerRegra(escolha);
    const pedido = httpTesting.expectOne({ method: 'POST', url: '/api/regras/sugestoes' });
    expect(pedido.request.body).toEqual({ itens: [escolha] });
    pedido.flush([sugestao]);
    const oferta = await aviso();
    expect(oferta.texto).toBe('Lançamento salvo. Criar uma regra para PIX QRS PADARIA?');
    expect(oferta.acao.textContent?.trim()).toBe('Criar regra');
    oferta.acao.click();
    await vi.waitFor(() => expect(dialogo.open).toHaveBeenCalledWith(RegraDialogo, expect.objectContaining({ data: { regra: null, sugestao } })));
    TestBed.inject(MatSnackBar).dismiss();
    await vi.waitFor(() => expect(document.querySelector('.mat-mdc-snack-bar-container')).toBeNull());

    fluxo.oferecerRegra(escolha);
    httpTesting.expectOne({ method: 'POST', url: '/api/regras/sugestoes' }).flush([]);
    fluxo.oferecerRegra(escolha);
    httpTesting.expectOne({ method: 'POST', url: '/api/regras/sugestoes' }).flush(null, { status: 500, statusText: 'Erro' });
    await new Promise((resolver) => setTimeout(resolver, 50));
    expect(document.querySelector('.mat-mdc-snack-bar-container')).toBeNull();
  });
});

describe('FluxoDeRegras no diálogo do lançamento (decisões 20 a 22)', () => {
  let httpTesting: HttpTestingController;

  afterEach(() => {
    httpTesting.verify();
    TestBed.inject(MatDialog).closeAll();
    TestBed.inject(MatSnackBar).dismiss();
  });

  function conta(id: number, nome: string): Conta {
    return { id, nome, tipo: 'CORRENTE', saldoInicial: 0, dataSaldoInicial: '2026-10-01', cor: null, saldoAtual: 0, arquivadaEm: null };
  }

  function categoria(id: number, nome: string, subcategorias: Categoria[] = []): Categoria {
    return { id, nome, natureza: 'DESPESA', categoriaMaeId: null, nomeDaMae: null, arquivadaEm: null, subcategorias };
  }

  const contas: ListaDeContas = { ativas: [conta(1, 'Banco Horizonte')], arquivadas: [], totais: { geral: 0, disponivel: 0, investimentos: 0 } };
  const categorias: ListaDeCategorias = { despesas: { ativas: [categoria(1, 'Alimentação', [categoria(2, 'Padaria')])], arquivadas: [] },
    receitas: { ativas: [], arquivadas: [] } };

  function item(parcial: Partial<ItemDaLista>): ItemDaLista {
    return { tipo: 'DESPESA', id: 10, data: '2026-11-26', descricao: 'PIX QRS PADARIA CE26/11', valor: -9.8, observacao: null, contaId: 1,
      contaNome: 'Banco Horizonte', categoriaId: null, categoriaNome: null, categoriaMaeNome: null, categoriaArquivada: false,
      contaDestinoId: null, contaDestinoNome: null, direcao: null, futuro: false, somenteLeitura: false, origem: 'IMPORTADO',
      descricaoBanco: 'PIX QRS PADARIA CE26/11', ...parcial };
  }

  /** Abre o diálogo de verdade, escolhe a categoria e salva; devolve o espião do oferecerRegra. */
  function salvarComCategoria(aberto: ItemDaLista, rotulo: string) {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    httpTesting = TestBed.inject(HttpTestingController);
    const oferecer = vi.spyOn(TestBed.inject(FluxoDeRegras), 'oferecerRegra').mockImplementation(() => undefined);
    abrirDialogoDeLancamento(TestBed.inject(MatDialog), { item: aberto, hoje: '2026-11-30', contas, categorias, contaPadrao: 1 }).subscribe();
    TestBed.tick();
    const lista = document.querySelector<HTMLSelectElement>('#lancamento-categoria') as HTMLSelectElement;
    lista.value = (Array.from(lista.options).find((opcao) => opcao.textContent?.trim() === rotulo) as HTMLOptionElement).value;
    lista.dispatchEvent(new Event('change'));
    TestBed.tick();
    (Array.from(document.querySelectorAll<HTMLButtonElement>('.mat-mdc-dialog-container button'))
      .find((botao) => botao.textContent?.trim() === 'Salvar') as HTMLButtonElement).click();
    TestBed.tick();
    httpTesting.expectOne({ method: 'PUT', url: '/api/lancamentos/10' }).flush(aberto);
    TestBed.tick();
    return oferecer;
  }

  it('o importado sem categoria que ganhou uma oferece criar regra', () => {
    const oferecer = salvarComCategoria(item({}), 'Padaria');
    expect(oferecer).toHaveBeenCalledWith({ contaId: 1, descricaoBanco: 'PIX QRS PADARIA CE26/11', descricao: 'PIX QRS PADARIA CE26/11',
      categoriaId: 2 });
  });

  it('o importado que já tinha categoria e o lançamento manual não oferecem', () => {
    expect(salvarComCategoria(item({ categoriaId: 1, categoriaNome: 'Alimentação' }), 'Padaria')).not.toHaveBeenCalled();
    TestBed.inject(MatDialog).closeAll();
    TestBed.resetTestingModule();
    expect(salvarComCategoria(item({ origem: 'MANUAL', descricaoBanco: null }), 'Padaria')).not.toHaveBeenCalled();
  });
});
