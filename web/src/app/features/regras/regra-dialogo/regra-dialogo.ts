import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { Observable, catchError, debounceTime, forkJoin, map, of, startWith, switchMap } from 'rxjs';

import { Alerta } from '../../../shared/alerta/alerta';
import { abrirConfirmacao } from '../../../shared/confirmacao/confirmacao-dialogo';
import { dataCurta } from '../../../shared/datas/data-curta';
import { Icone } from '../../../shared/icone/icone';
import { Valor } from '../../../shared/valor/valor';
import { camposDoErro, codigoDoErro, detalheDoErro, statusDoErro } from '../../auth/erros-http';
import { CategoriasService, ListaDeCategorias } from '../../categorias/categorias.service';
import { ContasService, ListaDeContas } from '../../contas/contas.service';
import { opcoesDeCategoria } from '../../lancamentos/apresentacao';
import { CONDICOES, fraseDaCondicao, nomeDaCategoria, posicaoDaNova, tituloDaPrevia } from '../apresentacao';
import { CondicaoDaRegra, DadosDaRegra, PreviaDaRegra, Regra, RegrasService, SugestaoDeRegra } from '../regras.service';

export interface DadosDoDialogoDeRegra {
  /** A regra a editar; nula = Nova regra. */
  readonly regra: Regra | null;
  /** Na Nova regra vinda de "Criar regra" (decisão 21): o que vem preenchido. */
  readonly sugestao: SugestaoDeRegra | null;
}

/** Como o diálogo fecha quando algo mudou; sem mudança, fecha sem resultado. */
export type ResultadoDaRegra = { readonly tipo: 'criada' | 'salva'; readonly regra: Regra } | { readonly tipo: 'excluida' };

type CampoDaRegra = 'condicao' | 'texto' | 'categoria' | 'conta' | 'descricao';

interface OpcaoDoSelect {
  readonly id: number;
  readonly rotulo: string;
}

/** Campo do 400 da API → campo do formulário. */
const CAMPOS_DA_API: Readonly<Record<string, CampoDaRegra>> = {
  condicao: 'condicao',
  texto: 'texto',
  categoriaId: 'categoria',
  contaId: 'conta',
  descricao: 'descricao',
};

const FALHA = 'Não foi possível salvar a regra. Tente novamente.';
const FALHA_DA_PREVIA = 'Não foi possível calcular a prévia.';

/** A prévia pronta, ou o erro dela; os dois nulos enquanto o texto não serve. */
interface EstadoDaPrevia {
  readonly previa: PreviaDaRegra | null;
  readonly erro: string | null;
}

/**
 * Quantas letras ou números o texto tem depois da comparação da regra (decisões 4 e 5): pontuação e
 * símbolos não contam, e as letras acentuadas contam uma vez.
 */
export function letrasOuNumeros(texto: string): number {
  return texto.normalize('NFD').replace(/\p{M}/gu, '').match(/[\p{L}\p{N}]/gu)?.length ?? 0;
}

/**
 * "Nova regra" e "Editar regra" (decisões 5 a 12). Carrega as contas e as categorias; a natureza vem
 * da categoria escolhida. A prévia (decisão 12) é pedida 300 ms depois de cada mudança; um erro nela
 * aparece no lugar dela (a mensagem do 422 ou uma neutra) e não impede salvar. Na Nova regra, a 1ª
 * regra atual entra no aviso da posição; se a lista não vier, o aviso fica sem ela.
 */
@Component({
  selector: 'app-regra-dialogo',
  imports: [MatButtonModule, MatDialogModule, ReactiveFormsModule, Alerta, Icone, Valor],
  templateUrl: './regra-dialogo.html',
  styleUrl: './regra-dialogo.scss',
})
export class RegraDialogo {
  private readonly servico = inject(RegrasService);
  private readonly referencia = inject<MatDialogRef<RegraDialogo, ResultadoDaRegra>>(MatDialogRef);
  private readonly dialogo = inject(MatDialog);

  readonly dados = inject<DadosDoDialogoDeRegra>(MAT_DIALOG_DATA);
  readonly regra = this.dados.regra;
  readonly editando = this.regra !== null;
  readonly condicoes = CONDICOES;
  readonly dataCurta = dataCurta;
  readonly formulario = new FormGroup({
    condicao: new FormControl<CondicaoDaRegra>(this.regra?.condicao ?? this.dados.sugestao?.condicao ?? 'CONTEM', { nonNullable: true }),
    texto: new FormControl(this.regra?.texto ?? this.dados.sugestao?.texto ?? '', { nonNullable: true }),
    categoria: new FormControl<number | null>(this.regra?.categoriaId ?? this.dados.sugestao?.categoriaId ?? null),
    conta: new FormControl<number | null>(this.regra?.contaId ?? null),
    descricao: new FormControl(this.regra?.descricao ?? this.dados.sugestao?.descricao ?? '', { nonNullable: true }),
  });
  readonly contas = signal<ListaDeContas | null>(null);
  readonly categorias = signal<ListaDeCategorias | null>(null);
  readonly falhaAoCarregar = signal(false);
  readonly enviando = signal(false);
  readonly erroGeral = signal<string | null>(null);
  /** Erros que vieram da API, por campo; somem quando o campo muda. */
  readonly errosDaApi = signal<Partial<Record<CampoDaRegra, string>>>({});
  readonly previa = signal<PreviaDaRegra | null>(null);
  readonly erroDaPrevia = signal<string | null>(null);
  readonly tituloDaPrevia = tituloDaPrevia;
  readonly nomeDaCategoria = nomeDaCategoria;
  /** A 1ª regra da lista, para "Entra em 1º lugar, antes de …" (só na Nova regra). */
  private readonly primeira = signal<Regra | null>(null);
  readonly textoDaPosicao = computed(() => posicaoDaNova(this.primeira()));
  private readonly categoriaEscolhida = toSignal(this.formulario.controls.categoria.valueChanges,
    { initialValue: this.formulario.controls.categoria.value });
  /** Despesas e receitas ativas; na edição, a arquivada que a regra já tem (decisão 11). */
  readonly opcoesDeDespesa = computed(() => this.opcoes('DESPESA'));
  readonly opcoesDeReceita = computed(() => this.opcoes('RECEITA'));
  /** As contas ativas; na edição, a arquivada que a regra já tem. */
  readonly opcoesDeConta = computed<OpcaoDoSelect[]>(() => {
    const lista = this.contas();
    const ativas = (lista?.ativas ?? []).map((conta) => ({ id: conta.id, rotulo: conta.nome }));
    const regra = this.regra;
    if (regra !== null && regra.contaArquivada && regra.contaId !== null) {
      return [{ id: regra.contaId, rotulo: `${regra.contaNome} (arquivada)` }, ...ativas];
    }
    return ativas;
  });
  /** "Categoria de despesa: a regra só pega despesas." (decisão 6). */
  readonly dicaDaCategoria = computed(() => {
    const id = this.categoriaEscolhida();
    if (id === null) {
      return 'A categoria diz se a regra pega despesas ou receitas.';
    }
    return this.opcoesDeReceita().some((opcao) => opcao.id === id) ? 'Categoria de receita: a regra só pega receitas.'
      : 'Categoria de despesa: a regra só pega despesas.';
  });

  constructor() {
    forkJoin([inject(ContasService).listar(), inject(CategoriasService).listar()]).subscribe({
      next: ([contas, categorias]) => {
        this.contas.set(contas);
        this.categorias.set(categorias);
      },
      error: () => this.falhaAoCarregar.set(true),
    });
    if (!this.editando) {
      // Só enriquece o aviso da posição: sem a lista, ele continua certo ("Entra em 1º lugar.").
      this.servico.listar().subscribe({ next: (regras) => this.primeira.set(regras[0] ?? null), error: () => undefined });
    }
    const campos: CampoDaRegra[] = ['condicao', 'texto', 'categoria', 'conta', 'descricao'];
    for (const campo of campos) {
      const controle: AbstractControl = this.formulario.controls[campo];
      controle.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
        this.errosDaApi.update(({ [campo]: removido, ...outros }) => {
          void removido;
          return outros;
        });
      });
    }
    // A prévia: 300 ms depois da última mudança; o erro fica dentro do switchMap, para a próxima mudança pedir de novo.
    this.formulario.valueChanges.pipe(
      startWith(null),
      debounceTime(300),
      map(() => this.pedidoDePrevia()),
      switchMap((pedido) => pedido === null ? of<EstadoDaPrevia>({ previa: null, erro: null }) : this.servico.previa(pedido).pipe(
        map((previa): EstadoDaPrevia => ({ previa, erro: null })),
        catchError((erro: unknown) => of<EstadoDaPrevia>({ previa: null,
          erro: statusDoErro(erro) === 422 ? detalheDoErro(erro, FALHA_DA_PREVIA) : FALHA_DA_PREVIA })),
      )),
      takeUntilDestroyed(),
    ).subscribe(({ previa, erro }) => {
      this.previa.set(previa);
      this.erroDaPrevia.set(erro);
    });
  }

  erro(campo: CampoDaRegra): string | null {
    const daApi = this.errosDaApi()[campo];
    if (daApi !== undefined) {
      return daApi;
    }
    return this.formulario.controls[campo].touched ? (this.errosLocais()[campo] ?? null) : null;
  }

  cancelar(): void {
    this.referencia.close();
  }

  salvar(): void {
    if (this.enviando()) {
      return;
    }
    this.formulario.markAllAsTouched();
    this.errosDaApi.set({});
    this.erroGeral.set(null);
    if (Object.keys(this.errosLocais()).length > 0) {
      return;
    }
    const valores = this.formulario.getRawValue();
    const dados: DadosDaRegra = {
      condicao: valores.condicao,
      texto: valores.texto.trim(),
      contaId: valores.conta,
      categoriaId: valores.categoria as number,
      descricao: valores.descricao.trim() === '' ? null : valores.descricao.trim(),
    };
    const regra = this.regra;
    const chamada: Observable<Regra> = regra === null ? this.servico.criar(dados) : this.servico.atualizar(regra.id, dados);
    this.enviando.set(true);
    this.referencia.disableClose = true;
    chamada.subscribe({
      next: (salva) => this.referencia.close({ tipo: regra === null ? 'criada' : 'salva', regra: salva }),
      error: (erro: unknown) => {
        this.tratarErro(erro);
        this.enviando.set(false);
        this.referencia.disableClose = false;
      },
    });
  }

  /** Abre a confirmação por cima; confirmada, fecha com "excluida". */
  excluir(): void {
    const regra = this.regra;
    if (regra === null || this.enviando()) {
      return;
    }
    abrirConfirmacao(this.dialogo, {
      titulo: `Excluir a regra ${fraseDaCondicao(regra.condicao, regra.texto)}?`,
      icone: 'lixeira',
      perigo: true,
      paragrafos: [['Os lançamentos que ela já categorizou continuam como estão. As regras de baixo sobem uma posição.']],
      confirmar: 'Excluir regra',
      falha: 'Não foi possível excluir a regra. Tente novamente.',
      acao: () => this.servico.excluir(regra.id),
    }).subscribe((excluida) => {
      if (excluida === true) {
        this.referencia.close({ tipo: 'excluida' });
      }
    });
  }

  /** O pedido da prévia, ou nulo enquanto o texto não serve. */
  private pedidoDePrevia(): { condicao: CondicaoDaRegra; texto: string; contaId: number | null; categoriaId: number | null } | null {
    const valores = this.formulario.getRawValue();
    const texto = valores.texto.trim();
    if (letrasOuNumeros(texto) < 2 || texto.length > 255) {
      return null;
    }
    return { condicao: valores.condicao, texto, contaId: valores.conta, categoriaId: valores.categoria };
  }

  /** As ativas da natureza, como no diálogo de lançamento (subcategoria recuada); na edição, a arquivada que a regra já tem. */
  private opcoes(natureza: 'DESPESA' | 'RECEITA'): OpcaoDoSelect[] {
    const lista = this.categorias();
    const opcoes = lista === null ? [] : opcoesDeCategoria(natureza === 'RECEITA' ? lista.receitas : lista.despesas);
    const regra = this.regra;
    if (regra !== null && regra.categoriaArquivada && regra.natureza === natureza) {
      return [{ id: regra.categoriaId, rotulo: `${regra.categoriaNome} (arquivada)` }, ...opcoes];
    }
    return opcoes;
  }

  /** As regras da tela, iguais às da API, para avisar antes de enviar. */
  private errosLocais(): Partial<Record<CampoDaRegra, string>> {
    const valores = this.formulario.getRawValue();
    const erros: Partial<Record<CampoDaRegra, string>> = {};
    const texto = valores.texto.trim();
    if (texto === '') {
      erros.texto = 'Escreva o texto da regra.';
    } else if (texto.length > 255) {
      erros.texto = 'O texto pode ter até 255 caracteres.';
    } else if (letrasOuNumeros(texto) < 2) {
      erros.texto = 'Use ao menos 2 letras ou números.';
    }
    if (valores.categoria === null) {
      erros.categoria = 'Escolha a categoria.';
    }
    if (valores.descricao.trim().length > 255) {
      erros.descricao = 'A descrição pode ter até 255 caracteres.';
    }
    return erros;
  }

  private tratarErro(erro: unknown): void {
    const status = statusDoErro(erro);
    const codigo = codigoDoErro(erro);
    if (status === 400) {
      const conhecidos: Partial<Record<CampoDaRegra, string>> = {};
      for (const [campoDaApi, mensagem] of Object.entries(camposDoErro(erro))) {
        const campo = CAMPOS_DA_API[campoDaApi];
        if (campo !== undefined) {
          conhecidos[campo] = mensagem;
        }
      }
      this.errosDaApi.set(conhecidos);
      if (Object.keys(conhecidos).length === 0) {
        this.erroGeral.set('Confira os campos e tente de novo.');
      }
    } else if (status === 422 && codigo === 'REGRA_REPETIDA') {
      this.errosDaApi.set({ texto: detalheDoErro(erro, FALHA) });
    } else if (status === 422 && codigo === 'CATEGORIA_INVALIDA') {
      this.errosDaApi.set({ categoria: detalheDoErro(erro, 'Escolha outra categoria.') });
    } else if (status === 422 && codigo === 'CONTA_INVALIDA') {
      this.errosDaApi.set({ conta: detalheDoErro(erro, 'Escolha outra conta.') });
    } else if (status === 422 || status === 404) {
      this.erroGeral.set(detalheDoErro(erro, FALHA));
    } else {
      this.erroGeral.set(FALHA);
    }
  }
}

/** Abre o diálogo com o painel certo (tela cheia no celular); emite o resultado ao fechar. */
export function abrirDialogoDeRegra(dialogo: MatDialog, dados: DadosDoDialogoDeRegra): Observable<ResultadoDaRegra | undefined> {
  return dialogo.open<RegraDialogo, DadosDoDialogoDeRegra, ResultadoDaRegra>(RegraDialogo, {
    data: dados,
    panelClass: ['flux-dialogo', 'flux-dialogo--cheio-no-celular'],
    width: '540px',
    maxWidth: 'calc(100vw - 32px)',
    maxHeight: 'calc(100vh - 32px)',
    autoFocus: '#regra-texto',
  }).afterClosed();
}
