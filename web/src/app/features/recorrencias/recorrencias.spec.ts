import { convertToParamMap } from '@angular/router';
import { ActivatedRoute, Router } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AGORA } from '../../core/tempo/agora';
import { CartoesService } from '../cartoes/cartoes.service';
import { CategoriasService } from '../categorias/categorias.service';
import { ContasService } from '../contas/contas.service';
import { FluxoDaOcorrencia } from './fluxo-da-ocorrencia';
import { ListaDeRecorrencias, Ocorrencia, Recorrencia, RecorrenciasService } from './recorrencias.service';
import { Recorrencias } from './recorrencias';

function recorrencia(id: number, descricao: string, parcial: Partial<Recorrencia> = {}): Recorrencia {
  return {
    id,
    descricao,
    tipo: 'DESPESA',
    valor: 100,
    valorComSinal: -100,
    valorVaria: false,
    categoriaId: 12,
    categoriaNome: 'Energia',
    categoriaMaeNome: 'Casa',
    categoriaArquivada: false,
    contaId: 1,
    contaNome: 'Banco Horizonte',
    cartaoId: null,
    cartaoNome: null,
    cartaoCor: null,
    frequencia: 'MENSAL',
    diaDoMes: 30,
    comecaEm: '2026-11-25',
    terminaEm: null,
    ocorrenciasDesde: '2026-11-25',
    situacao: 'ATIVA',
    motivoDaPausa: null,
    proxima: '2026-11-26',
    atrasadas: 0,
    confirmadas: 0,
    ...parcial,
  };
}

const lista: ListaDeRecorrencias = {
  ativas: [
    ...Array.from({ length: 12 }, (_, indice) => recorrencia(indice + 1, `Ativa ${indice + 1}`)),
    recorrencia(13, 'Água Fictícia', { atrasadas: 1, proxima: '2026-11-26' }),
  ],
  pausadas: [recorrencia(14, 'Streaming antigo Fictício', { situacao: 'PAUSADA', motivoDaPausa: 'A categoria está arquivada.' })],
  encerradas: [recorrencia(15, 'Curso Fictício', { situacao: 'ENCERRADA', terminaEm: '2026-11-15', proxima: null })],
  aConfirmar: {
    ate: '2026-12-02',
    recorrencias: 2,
    ocorrencias: [{
      recorrenciaId: 13,
      periodo: '2026-11-01',
      data: '2026-11-20',
      dataNaLista: '2026-11-20',
      descricao: 'Água Fictícia',
      tipo: 'DESPESA',
      valor: -98.7,
      valorPrevisto: 98.7,
      valorVaria: false,
      contaId: 1,
      contaNome: 'Banco Horizonte',
      cartaoId: null,
      cartaoNome: null,
      cartaoCor: null,
      categoriaId: 12,
      categoriaNome: 'Energia',
      categoriaMaeNome: 'Casa',
      atrasada: true,
      visivelAte: '2027-01-19',
    } satisfies Ocorrencia],
  },
};

describe('Recorrencias', () => {
  const servico = { listar: vi.fn(), encerrar: vi.fn(), excluir: vi.fn(), retomar: vi.fn() };
  const dialogo = { open: vi.fn<(...args: unknown[]) => unknown>(() => ({ afterClosed: () => of(undefined) })) };
  const snackBar = { open: vi.fn() };
  const mudou = new Subject<void>();
  let parametros = convertToParamMap({});

  beforeEach(() => {
    servico.listar.mockReset();
    servico.encerrar.mockReset();
    servico.excluir.mockReset();
    servico.retomar.mockReset();
    dialogo.open.mockClear();
    snackBar.open.mockClear();
    servico.listar.mockReturnValue(of(lista));
    servico.encerrar.mockReturnValue(of(lista.encerradas[0]));
    servico.excluir.mockReturnValue(of(undefined));
    servico.retomar.mockReturnValue(of(lista.ativas[0]));
    parametros = convertToParamMap({});
    TestBed.configureTestingModule({
      providers: [
        { provide: AGORA, useValue: () => new Date('2026-11-25T15:00:00.000Z') },
        { provide: RecorrenciasService, useValue: servico },
        {
          provide: ContasService,
          useValue: { listar: () => of({ ativas: [], arquivadas: [], totais: { geral: 0, disponivel: 0, investimentos: 0 } }) },
        },
        { provide: CartoesService, useValue: { listar: () => of({ ativos: [], arquivados: [] }) } },
        {
          provide: CategoriasService,
          useValue: { listar: () => of({ despesas: { ativas: [], arquivadas: [] }, receitas: { ativas: [], arquivadas: [] } }) },
        },
        { provide: FluxoDaOcorrencia, useValue: { mudou$: mudou } },
        { provide: MatDialog, useValue: dialogo },
        { provide: MatSnackBar, useValue: snackBar },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: parametros } } },
        { provide: Router, useValue: { navigate: vi.fn().mockResolvedValue(true) } },
      ],
    });
  });

  function criar() {
    const fixture = TestBed.createComponent(Recorrencias);
    fixture.detectChanges();
    return fixture;
  }

  function texto(fixture: ReturnType<typeof criar>): string {
    return fixture.nativeElement.textContent.replace(/\s+/g, ' ').trim();
  }

  it('mostra A confirmar, as ativas na ordem da API, as pausadas com o motivo e as encerradas fechadas', () => {
    const fixture = criar();
    const elemento = fixture.nativeElement as HTMLElement;
    const textoDaPagina = texto(fixture);
    expect(elemento.querySelector('#a-confirmar')?.textContent).toBe('A confirmar');
    expect(elemento.querySelector('.app-recorrencias__titulo p')?.textContent).toBe('A atrasada e o que vence até quarta, 02/12');
    expect(elemento.querySelector('.app-recorrencias__titulo p.flux-so-celular')?.textContent).toBe('Até quarta, 02/12');
    expect(textoDaPagina).toContain('Ativas 13');
    expect(textoDaPagina).toContain('Pausadas 1');
    const titulos = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('.app-recorrencias__linha b'));
    expect(titulos.slice(0, 13).map((elemento) => elemento.textContent))
      .toEqual([...Array.from({ length: 12 }, (_, indice) => `Ativa ${indice + 1}`), 'Água Fictícia']);
    const agua = Array.from(elemento.querySelectorAll<HTMLElement>('.app-recorrencias__linha'))
      .find((linha) => linha.textContent?.includes('Água Fictícia')) as HTMLElement;
    expect(agua.querySelector('.flux-pilula--atrasada')?.textContent).toBe('1 atrasada');
    expect(agua.querySelector('.app-recorrencias__proxima')?.getAttribute('aria-label')).toBe('Próxima qui, 26/11');
    const pausada = Array.from(elemento.querySelectorAll<HTMLElement>('.app-recorrencias__linha--pausada'))
      .find((linha) => linha.textContent?.includes('Streaming antigo Fictício')) as HTMLElement;
    expect(pausada.querySelector('.flux-pilula')?.textContent).toBe('Pausada');
    expect(pausada.querySelector('.app-recorrencias__meta')?.textContent?.replace(/\s+/g, ' ').trim())
      .toBe('Pausada A categoria está arquivada. · Todo dia 30 · Casa › Energia');
    const encerradas = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.app-recorrencias__alternar');
    expect(encerradas?.textContent?.trim()).toContain('Encerradas 1');
    expect(encerradas?.getAttribute('aria-expanded')).toBe('false');
  });

  it('sem recorrência mostra o estado vazio', () => {
    servico.listar.mockReturnValue(of({
      ativas: [], pausadas: [], encerradas: [],
      aConfirmar: { ate: '2026-12-02', recorrencias: 0, ocorrencias: [] },
    }));
    const fixture = criar();
    expect(texto(fixture)).toContain('Nada se repete por aqui ainda');
    expect(texto(fixture)).toContain('Cadastre o salário, o aluguel e as assinaturas para ver o que vem por aí e projetar o saldo.');
    const botoes = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('button'));
    expect(botoes.map((botao) => botao.textContent?.trim())).toContain('Nova recorrência');
  });

  it('?nova=recorrencia abre o diálogo', () => {
    parametros = convertToParamMap({ nova: 'recorrencia' });
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        { provide: AGORA, useValue: () => new Date('2026-11-25T15:00:00.000Z') },
        { provide: RecorrenciasService, useValue: servico },
        {
          provide: ContasService,
          useValue: { listar: () => of({ ativas: [], arquivadas: [], totais: { geral: 0, disponivel: 0, investimentos: 0 } }) },
        },
        { provide: CartoesService, useValue: { listar: () => of({ ativos: [], arquivados: [] }) } },
        {
          provide: CategoriasService,
          useValue: { listar: () => of({ despesas: { ativas: [], arquivadas: [] }, receitas: { ativas: [], arquivadas: [] } }) },
        },
        { provide: FluxoDaOcorrencia, useValue: { mudou$: mudou } },
        { provide: MatDialog, useValue: dialogo },
        { provide: MatSnackBar, useValue: snackBar },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: parametros } } },
        { provide: Router, useValue: { navigate: vi.fn().mockResolvedValue(true) } },
      ],
    });
    criar();
    expect(dialogo.open).toHaveBeenCalled();
    const chamada = dialogo.open.mock.calls[0] as unknown as [unknown, { data: { recorrencia: Recorrencia | null } }];
    expect(chamada[1].data.recorrencia).toBeNull();
  });

  it('o menu da ativa tem Editar, Histórico, Encerrar e Excluir, e o da encerrada Histórico e Excluir', async () => {
    const fixture = criar();
    const elemento = fixture.nativeElement as HTMLElement;
    async function abrirMenu(rotulo: string): Promise<HTMLElement[]> {
      elemento.querySelector<HTMLButtonElement>(`[aria-label="${rotulo}"]`)?.click();
      fixture.detectChanges();
      await fixture.whenStable();
      const paineis = Array.from(document.querySelectorAll<HTMLElement>('.mat-mdc-menu-panel'));
      return Array.from(paineis[paineis.length - 1].querySelectorAll<HTMLElement>('.mat-mdc-menu-item'));
    }
    const nomes = (itens: HTMLElement[]) => itens.map((item) => item.textContent?.trim());
    const daAtiva = await abrirMenu('Ações de Ativa 1');
    expect(nomes(daAtiva)).toEqual(['Editar', 'Histórico', 'Encerrar', 'Excluir']);
    daAtiva[0].click();
    expect(dialogo.open).toHaveBeenCalledTimes(1);
    expect((dialogo.open.mock.calls[0] as unknown as [unknown, { data: Record<string, unknown> }])[1].data['recorrencia'])
      .toEqual(lista.ativas[0]);
    (await abrirMenu('Ações de Ativa 1'))[1].click();
    expect(dialogo.open).toHaveBeenCalledTimes(2);
    expect((dialogo.open.mock.calls[1] as unknown as [unknown, { data: Record<string, unknown> }])[1].data)
      .toEqual({ recorrenciaId: 1, hoje: '2026-11-25' });
    expect(elemento.querySelector('[aria-label="Ações de Curso Fictício"]')).toBeNull();
    elemento.querySelector<HTMLButtonElement>('.app-recorrencias__alternar')?.click();
    fixture.detectChanges();
    expect(elemento.querySelector('.app-recorrencias__alternar')?.getAttribute('aria-expanded')).toBe('true');
    expect(nomes(await abrirMenu('Ações de Curso Fictício'))).toEqual(['Histórico', 'Excluir']);
  });

  it('encerrar e excluir confirmam com as frases e avisam', () => {
    dialogo.open.mockReturnValue({ afterClosed: () => of(true) });
    const fixture = criar();
    fixture.componentInstance.encerrar(lista.ativas[0]);
    fixture.componentInstance.excluir(lista.ativas[0]);
    const dadosDeEncerrar = (dialogo.open.mock.calls[0] as unknown as [unknown, { data: Record<string, unknown> }])[1].data;
    const dadosDeExcluir = (dialogo.open.mock.calls[1] as unknown as [unknown, { data: Record<string, unknown> }])[1].data;
    expect(dadosDeEncerrar['titulo']).toBe('Encerrar Ativa 1?');
    expect(dadosDeEncerrar['paragrafos']).toEqual([['As previstas depois de hoje saem.']]);
    expect(dadosDeExcluir['titulo']).toBe('Excluir Ativa 1?');
    expect(dadosDeExcluir['paragrafos']).toEqual([['Sai a recorrência e o histórico dela.']]);
    (dadosDeEncerrar['acao'] as () => ReturnType<typeof of>)().subscribe();
    (dadosDeExcluir['acao'] as () => ReturnType<typeof of>)().subscribe();
    expect(servico.encerrar).toHaveBeenCalledWith(1);
    expect(servico.excluir).toHaveBeenCalledWith(1);
    expect(snackBar.open).toHaveBeenCalledWith('Recorrência encerrada.', 'Fechar', { duration: 5000 });
    expect(snackBar.open).toHaveBeenCalledWith('Recorrência excluída.', 'Fechar', { duration: 5000 });
  });
});
