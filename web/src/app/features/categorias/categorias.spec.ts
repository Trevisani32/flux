import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, convertToParamMap, ParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, Observable, Subject, of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { ConfirmacaoDialogo, DadosDaConfirmacao } from '../../shared/confirmacao/confirmacao-dialogo';
import { CategoriaDialogo, DadosDoDialogoDeCategoria } from './categoria-dialogo/categoria-dialogo';
import { Categoria, CategoriasService, ListaDeCategorias } from './categorias.service';
import { Categorias } from './categorias';

function categoria(parcial: Partial<Categoria>): Categoria {
  return { id: 1, nome: 'Categoria', natureza: 'DESPESA', categoriaMaeId: null, nomeDaMae: null, arquivadaEm: null, subcategorias: [], ...parcial };
}

const transporte = categoria({
  id: 10, nome: 'Transporte',
  subcategorias: [
    categoria({ id: 11, nome: 'Combustível', categoriaMaeId: 10, nomeDaMae: 'Transporte' }),
    categoria({ id: 12, nome: 'Transporte público', categoriaMaeId: 10, nomeDaMae: 'Transporte' }),
  ],
});
const moradia = categoria({
  id: 20, nome: 'Moradia',
  subcategorias: [
    categoria({ id: 21, nome: 'Aluguel', categoriaMaeId: 20, nomeDaMae: 'Moradia' }),
    categoria({ id: 22, nome: 'Condomínio', categoriaMaeId: 20, nomeDaMae: 'Moradia' }),
  ],
});
const lazer = categoria({ id: 30, nome: 'Lazer' });
const appDeTransporte = categoria({ id: 13, nome: 'App de transporte', categoriaMaeId: 10, nomeDaMae: 'Transporte', arquivadaEm: '2026-11-05T15:00:00Z' });
const educacao = categoria({
  id: 40, nome: 'Educação', arquivadaEm: '2026-11-02T15:00:00Z',
  subcategorias: [categoria({ id: 41, nome: 'Cursos', categoriaMaeId: 40, nomeDaMae: 'Educação', arquivadaEm: '2026-11-02T15:00:00Z' })],
});
const lista: ListaDeCategorias = {
  despesas: { ativas: [lazer, moradia, transporte], arquivadas: [appDeTransporte, educacao] },
  receitas: { ativas: [categoria({ id: 50, nome: 'Salário', natureza: 'RECEITA' })], arquivadas: [] },
};

describe('Categorias', () => {
  function criar(natureza: string | null = null, resposta: () => Observable<ListaDeCategorias> = () => of(lista)) {
    const parametros = new BehaviorSubject<ParamMap>(convertToParamMap(natureza === null ? {} : { natureza }));
    const servico = {
      listar: vi.fn(resposta),
      arquivar: vi.fn(() => of(categoria({}))),
      reativar: vi.fn((): Observable<Categoria> => of(categoria({}))),
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
      imports: [Categorias],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { queryParamMap: parametros.asObservable(), snapshot: { queryParamMap: parametros.value } } },
        { provide: CategoriasService, useValue: servico },
        { provide: MatDialog, useValue: dialogo },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    });
    const fixture = TestBed.createComponent(Categorias);
    fixture.detectChanges();
    const elemento = fixture.nativeElement as HTMLElement;
    return { componente: fixture.componentInstance, elemento, atualizar: () => fixture.detectChanges(), parametros, servico, dialogo, fechamentos, snackBar, acaoDoAviso };
  }

  function textos(elemento: HTMLElement, seletor: string): string[] {
    return Array.from(elemento.querySelectorAll(seletor)).map((item) => item.textContent?.trim() ?? '');
  }

  function confirmacao(dialogo: { open: ReturnType<typeof vi.fn> }, chamada = 0) {
    return dialogo.open.mock.calls[chamada] as unknown as [unknown, { data: DadosDaConfirmacao; panelClass: string }];
  }

  it('mostra as abas com as quantidades e a árvore de despesas', () => {
    const { elemento } = criar();
    const abas = Array.from(elemento.querySelectorAll<HTMLAnchorElement>('.flux-abas__aba'));
    expect(abas.map((aba) => aba.textContent?.replace(/\s+/g, ' ').trim())).toEqual(['Despesas 7', 'Receitas 1']);
    expect(abas[0].getAttribute('aria-current')).toBe('page');
    expect(abas[1].getAttribute('aria-current')).toBeNull();
    expect(abas[1].getAttribute('href')).toBe('/categorias?natureza=receita');
    expect(textos(elemento, '.arvore__nome')).toEqual(['Lazer', 'Moradia', 'Transporte']);
    expect(textos(elemento, '.arvore__principal:nth-child(3) .arvore__nome-filha')).toEqual(['Combustível', 'Transporte público']);
    expect(textos(elemento, '.arvore__quantidade')).toEqual(['2 subcategorias', '2 subcategorias']);
  });

  it('a aba de receitas vem da URL', () => {
    const { elemento, atualizar, parametros } = criar('receita');
    expect(textos(elemento, '.arvore__nome')).toEqual(['Salário']);
    expect(elemento.querySelector('.flux-abas__aba[aria-current="page"]')?.textContent).toContain('Receitas');
    expect(elemento.querySelector('.flux-arquivadas')).toBeNull();
    parametros.next(convertToParamMap({}));
    atualizar();
    expect(textos(elemento, '.arvore__nome')).toEqual(['Lazer', 'Moradia', 'Transporte']);
  });

  it('mostra o estado vazio da natureza sem categorias ativas', () => {
    const vazia: ListaDeCategorias = { ...lista, receitas: { ativas: [], arquivadas: [] } };
    const { elemento, dialogo } = criar('receita', () => of(vazia));
    expect(elemento.querySelector('app-estado-vazio h2')?.textContent).toBe('Nenhuma categoria de receita');
    expect(elemento.querySelector('app-estado-vazio p')?.textContent).toBe('Crie categorias para separar salário, freelas e rendimentos.');
    expect(elemento.querySelector('.flux-abas__aba[aria-current="page"]')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('Receitas 0');
    elemento.querySelector<HTMLButtonElement>('app-estado-vazio button')?.click();
    const dados = (dialogo.open.mock.calls[0] as unknown as [unknown, { data: DadosDoDialogoDeCategoria }])[1].data;
    expect(dados.natureza).toBe('RECEITA');
    expect(dados.categoria).toBeNull();
  });

  it('as arquivadas mostram a principal com as subcategorias dentro e a subcategoria solta com a principal', () => {
    const { elemento, atualizar } = criar();
    elemento.querySelector<HTMLButtonElement>('.flux-arquivadas__alternar')?.click();
    atualizar();
    expect(textos(elemento, '.arquivada__linha .arquivada__nome')).toEqual(['App de transporte', 'Educação']);
    expect(textos(elemento, '.arquivada__meta')[0]).toContain('Em Transporte · arquivada em 05/11/2026');
    expect(textos(elemento, '.arquivada__meta')[1]).toContain('Categoria principal · arquivada em 02/11/2026');
    expect(textos(elemento, '.arquivada .arvore__nome-filha')).toEqual(['Cursos']);
    expect(elemento.querySelectorAll('.arquivada__reativar')).toHaveLength(2);
  });

  it('Nova subcategoria abre o diálogo com a principal escolhida e as principais ativas', () => {
    const { componente, dialogo } = criar();
    componente.novaCategoria(10);
    const [tipo, configuracao] = dialogo.open.mock.calls[0] as unknown as [unknown, { data: DadosDoDialogoDeCategoria; panelClass: string; autoFocus: string }];
    expect(tipo).toBe(CategoriaDialogo);
    expect(configuracao.panelClass).toBe('flux-dialogo');
    expect(configuracao.autoFocus).toBe('#categoria-nome');
    expect(configuracao.data.maeId).toBe(10);
    expect(configuracao.data.natureza).toBe('DESPESA');
    expect(configuracao.data.principais.DESPESA.map((item) => item.nome)).toEqual(['Lazer', 'Moradia', 'Transporte']);
    expect(configuracao.data.principais.RECEITA.map((item) => item.nome)).toEqual(['Salário']);
  });

  it('editar uma principal com subcategoria só arquivada também a trava em "Dentro de"', () => {
    const { componente, dialogo } = criar();
    componente.editar(lazer);
    componente.editar(categoria({ ...transporte, subcategorias: [] }));
    expect((dialogo.open.mock.calls[0] as unknown as [unknown, { data: DadosDoDialogoDeCategoria }])[1].data.temSubcategorias).toBe(false);
    expect((dialogo.open.mock.calls[1] as unknown as [unknown, { data: DadosDoDialogoDeCategoria }])[1].data.temSubcategorias).toBe(true);
  });

  it('arquiva direto a categoria sem subcategorias ativas, com Desfazer', () => {
    const { componente, servico, snackBar, acaoDoAviso, dialogo } = criar();
    componente.arquivar(lazer);
    expect(dialogo.open).not.toHaveBeenCalled();
    expect(servico.arquivar).toHaveBeenCalledWith(30);
    expect(snackBar.open).toHaveBeenCalledWith('Lazer arquivada.', 'Desfazer', { duration: 8000 });
    acaoDoAviso.next();
    expect(servico.reativar).toHaveBeenCalledWith(30);
  });

  it('pede confirmação para arquivar a principal com subcategorias ativas', () => {
    const { componente, dialogo, servico, fechamentos } = criar();
    componente.arquivar(transporte);
    const [tipo, configuracao] = confirmacao(dialogo);
    expect(tipo).toBe(ConfirmacaoDialogo);
    expect(configuracao.data.titulo).toBe('Arquivar Transporte?');
    expect(configuracao.data.paragrafos[0]).toEqual(['As 2 subcategorias ativas também serão arquivadas: ', { forte: 'Combustível' }, ' e ', { forte: 'Transporte público' }, '.']);
    expect(configuracao.data.confirmar).toBe('Arquivar');
    configuracao.data.acao().subscribe();
    expect(servico.arquivar).toHaveBeenCalledWith(10);
    fechamentos[0].next(true);
    expect(servico.listar).toHaveBeenCalledTimes(2);
  });

  it('excluir a principal lista as subcategorias ativas e as arquivadas', () => {
    const { componente, dialogo, servico } = criar();
    componente.excluir(transporte);
    const configuracao = confirmacao(dialogo)[1];
    expect(configuracao.panelClass).toBe('flux-dialogo-perigo');
    expect(configuracao.data.titulo).toBe('Excluir Transporte?');
    expect(configuracao.data.paragrafos[0]).toEqual(['As 3 subcategorias também serão excluídas: ', { forte: 'Combustível' }, ', ', { forte: 'Transporte público' }, ' e ', { forte: 'App de transporte' }, '.']);
    expect(configuracao.data.confirmar).toBe('Excluir categoria');
    configuracao.data.acao().subscribe();
    expect(servico.excluir).toHaveBeenCalledWith(10);
    componente.excluir(lazer);
    expect(confirmacao(dialogo, 1)[1].data.paragrafos).toEqual([['A categoria sai do Flux de vez. ', { forte: 'Não dá para desfazer.' }]]);
  });

  it('o bloqueio da exclusão diz de quem é o uso e oferece arquivar', () => {
    const { componente, dialogo } = criar();
    componente.excluir(transporte);
    const bloqueio = confirmacao(dialogo)[1].data.bloqueio;
    expect(bloqueio?.codigo).toBe('CATEGORIA_EM_USO');
    expect(bloqueio?.titulo).toBe('Esta categoria não pode ser excluída');
    expect(bloqueio?.alternativa).toBe('Arquivar categoria');
    expect(bloqueio?.paragrafo('42 lançamentos')).toEqual([{ forte: 'Transporte' }, ' e as subcategorias têm 42 lançamentos.',
      ' Para tirá-la dos seletores sem perder o histórico, arquive a categoria.']);
    componente.excluir(lazer);
    expect(confirmacao(dialogo, 1)[1].data.bloqueio?.paragrafo('1 lançamento')).toEqual([{ forte: 'Lazer' }, ' tem 1 lançamento.',
      ' Para tirá-la dos seletores sem perder o histórico, arquive a categoria.']);
  });

  it('mostra o erro de reativar nas arquivadas', () => {
    const { componente, elemento, atualizar, servico } = criar();
    servico.reativar.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 422, error: { codigo: 'MAE_ARQUIVADA', detail: 'Reative antes a categoria Transporte.' } })));
    componente.reativar(appDeTransporte);
    atualizar();
    expect(elemento.querySelector('#categorias-arquivadas [role="alert"]')?.textContent).toContain('Reative antes a categoria Transporte.');
  });

  it('mostra o erro de carregamento', () => {
    const { elemento } = criar(null, () => throwError(() => new HttpErrorResponse({ status: 500 })));
    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('Não foi possível carregar as categorias.');
    expect(elemento.querySelector('.flux-abas')).toBeNull();
  });
});
