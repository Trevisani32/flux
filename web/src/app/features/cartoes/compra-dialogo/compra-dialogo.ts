import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { Observable, catchError, debounceTime, distinctUntilChanged, forkJoin, map, merge, of, switchMap } from 'rxjs';

import { Alerta } from '../../../shared/alerta/alerta';
import { abrirConfirmacao, TrechoDeTexto } from '../../../shared/confirmacao/confirmacao-dialogo';
import { dataCurta } from '../../../shared/datas/data-curta';
import { Icone } from '../../../shared/icone/icone';
import { ErroDoValor, escreverValorDigitado, lerValorDigitado } from '../../../shared/moeda/valor-digitado';
import { Valor } from '../../../shared/valor/valor';
import { camposDoErro, codigoDoErro, detalheDoErro, statusDoErro } from '../../auth/erros-http';
import { ListaDeCategorias } from '../../categorias/categorias.service';
import { dataLimite } from '../../lancamentos/lancamento-dialogo/lancamento-dialogo';
import { nomeDoMes, OpcaoDeCategoria, opcoesDeCategoria } from '../../lancamentos/apresentacao';
import { dataMinimaDaCompra, dicaDoEstorno, TextoDaPrevia, textoDaPrevia, textoDaCategoria } from '../apresentacao';
import { Cartao, CartoesService } from '../cartoes.service';
import { Compra, ComprasService, DadosDaCompra, DadosDoEstorno, PreviaDaCompra, TipoDaCompra } from '../compras.service';

export interface DadosDoDialogoDeCompra {
  readonly tipo: TipoDaCompra;
  /** null = Nova compra (ou Novo estorno). */
  readonly id: number | null;
  /** O cartão já escolhido no novo (o da fatura aberta na tela), ou null. */
  readonly cartaoId: number | null;
  /** Hoje no fuso do negócio ("2026-11-12"): a data padrão e os limites, 10 anos para trás e 12 meses para a frente. */
  readonly hoje: string;
  readonly categorias: ListaDeCategorias;
}

/** Como o diálogo fecha quando algo mudou; sem mudança, fecha sem resultado. */
export type ResultadoDaCompra = 'salvo' | 'excluido';

type CampoDaCompra = 'cartao' | 'data' | 'descricao' | 'valor' | 'parcelas' | 'parcelaInicial' | 'categoria' | 'observacao';

const MENSAGENS_DO_VALOR: Readonly<Record<ErroDoValor | 'sinal' | 'zero', string>> = {
  vazio: 'Informe o valor.',
  invalido: 'Digite um valor como 1.234,56.',
  casas: 'Use no máximo 2 casas decimais.',
  grande: 'Use no máximo 13 dígitos antes da vírgula.',
  sinal: 'Digite o valor sem sinal.',
  zero: 'O valor precisa ser maior que zero.',
};

/** Campo do 400 da API → campo do formulário. */
const CAMPOS_DA_API: Readonly<Record<string, CampoDaCompra>> = {
  cartaoId: 'cartao',
  data: 'data',
  descricao: 'descricao',
  valorTotal: 'valor',
  valor: 'valor',
  parcelas: 'parcelas',
  parcelaInicial: 'parcelaInicial',
  categoriaId: 'categoria',
  observacao: 'observacao',
};

const FALHA = 'Não foi possível salvar. Tente novamente.';

/** As opções do select de parcelas: "À vista (1x)", "2x" a "48x" (decisão 14). */
export const OPCOES_DE_PARCELAS: readonly { readonly valor: number; readonly rotulo: string }[] = Array.from({ length: 48 },
  (_, indice) => ({ valor: indice + 1, rotulo: indice === 0 ? 'À vista (1x)' : `${indice + 1}x` }));

interface Carregado {
  readonly cartoes: readonly Cartao[];
  readonly compra: Compra | null;
}

/**
 * Nova compra, Editar compra, Novo estorno e Editar estorno (decisões 14 a 20 da 05a). A prévia das parcelas vem da
 * API, 300 ms depois de mudar cartão, data, valor, parcelas ou a primeira parcela (decisão 18): o web não faz conta
 * com dinheiro. Com o cartão arquivado, só leitura. No celular, ocupa a tela.
 */
@Component({
  selector: 'app-compra-dialogo',
  imports: [MatButtonModule, MatDialogModule, ReactiveFormsModule, Alerta, Icone, Valor],
  templateUrl: './compra-dialogo.html',
  styleUrl: './compra-dialogo.scss',
})
export class CompraDialogo {
  private readonly servico = inject(ComprasService);
  private readonly cartoesService = inject(CartoesService);
  private readonly referencia = inject<MatDialogRef<CompraDialogo, ResultadoDaCompra>>(MatDialogRef);
  private readonly dialogo = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);

  readonly dados = inject<DadosDoDialogoDeCompra>(MAT_DIALOG_DATA);
  readonly estorno = this.dados.tipo === 'ESTORNO';
  readonly editando = this.dados.id !== null;
  readonly limite = dataLimite(this.dados.hoje);
  readonly minima = dataMinimaDaCompra(this.dados.hoje);
  /** O `min` do campo: na edição, a data que a compra já tem, quando é mais antiga que a faixa (decisão 16). */
  readonly minimaDoCampo = computed(() => {
    const data = this.carregado()?.compra?.data ?? null;
    return data !== null && data < this.minima ? data : this.minima;
  });
  readonly opcoesDeParcelas = OPCOES_DE_PARCELAS;
  readonly textoDaCategoria = textoDaCategoria;
  readonly dataCurta = dataCurta;
  readonly carregado = signal<Carregado | null>(null);
  readonly falhaAoCarregar = signal<string | null>(null);
  readonly formulario = new FormGroup({
    cartao: new FormControl<number | null>(this.dados.cartaoId),
    data: new FormControl(this.dados.hoje, { nonNullable: true }),
    descricao: new FormControl('', { nonNullable: true }),
    valor: new FormControl('', { nonNullable: true }),
    parcelas: new FormControl(1, { nonNullable: true }),
    andamento: new FormControl(false, { nonNullable: true }),
    parcelaInicial: new FormControl('1', { nonNullable: true }),
    categoria: new FormControl<number | null>(null),
    observacao: new FormControl('', { nonNullable: true }),
  });
  readonly enviando = signal(false);
  readonly erroGeral = signal<string | null>(null);
  /** Erros que vieram da API, por campo; somem quando o campo muda. O da prévia vem por aqui também. */
  readonly errosDaApi = signal<Partial<Record<CampoDaCompra, string>>>({});
  readonly previa = signal<PreviaDaCompra | null>(null);
  private readonly parcelas = toSignal(this.formulario.controls.parcelas.valueChanges, { initialValue: 1 });
  private readonly andamento = toSignal(this.formulario.controls.andamento.valueChanges, { initialValue: false });
  private readonly inicial = toSignal(this.formulario.controls.parcelaInicial.valueChanges, { initialValue: '1' });
  readonly emAndamento = computed(() => this.parcelas() > 1 && this.andamento());
  readonly titulo = computed(() => {
    const compra = this.carregado()?.compra ?? null;
    if (compra?.somenteLeitura) {
      return compra.descricao;
    }
    return this.estorno ? (this.editando ? 'Editar estorno' : 'Novo estorno') : (this.editando ? 'Editar compra' : 'Nova compra');
  });
  /** "Lançar 10 parcelas", "Lançar compra", "Lançar estorno" ou "Salvar". */
  readonly textoDoSalvar = computed(() => {
    if (this.editando) {
      return 'Salvar';
    }
    if (this.estorno) {
      return 'Lançar estorno';
    }
    const inicial = this.emAndamento() ? Number(this.inicial()) : 1;
    const quantas = Number.isInteger(inicial) && inicial >= 1 && inicial <= this.parcelas() ? this.parcelas() - inicial + 1 : this.parcelas();
    return quantas === 1 ? 'Lançar compra' : `Lançar ${quantas} parcelas`;
  });
  readonly textoDaPrevia = computed<TextoDaPrevia | null>(() => {
    const previa = this.previa();
    return previa === null ? null : textoDaPrevia(previa, this.dados.hoje);
  });
  /** Decisão 20: "Cai na fatura de dezembro (vence 10/12) e abate o total…", com a fatura da prévia, quando há. */
  readonly dicaDoEstorno = computed(() => dicaDoEstorno(this.previa()?.primeiraFatura ?? null, this.dados.hoje));
  /** Os cartões ativos (o da compra que se edita é ativo: com ele arquivado, o diálogo é só leitura). */
  readonly cartoes = computed(() => this.carregado()?.cartoes ?? []);
  /** As categorias da natureza (despesa na compra, receita no estorno); na edição, a arquivada que a compra já tem. */
  readonly categorias = computed<OpcaoDeCategoria[]>(() => {
    const opcoes = opcoesDeCategoria(this.estorno ? this.dados.categorias.receitas : this.dados.categorias.despesas);
    const compra = this.carregado()?.compra ?? null;
    if (compra !== null && compra.categoriaArquivada && compra.categoriaId !== null) {
      return [{ id: compra.categoriaId, rotulo: `${compra.categoriaNome} (arquivada)` }, ...opcoes];
    }
    return opcoes;
  });

  constructor() {
    this.carregar();
    const campos: CampoDaCompra[] = ['cartao', 'data', 'descricao', 'valor', 'parcelas', 'parcelaInicial', 'categoria', 'observacao'];
    for (const campo of campos) {
      const controle: AbstractControl = this.formulario.controls[campo];
      controle.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
        this.errosDaApi.update(({ [campo]: removido, ...outros }) => {
          void removido;
          return outros;
        });
      });
    }
    // Decisão 18: a prévia 300 ms depois da última mudança no cartão, na data, no valor, nas parcelas ou na primeira
    // parcela (os textos não pedem nada); o pedido anterior é cancelado. No estorno, ela só diz a fatura da data. Só
    // pede quando o que ela usa muda de fato: marcar "Compra em andamento" com a primeira parcela em 1 não pede.
    const controles = this.formulario.controls;
    merge(controles.cartao.valueChanges, controles.data.valueChanges, controles.valor.valueChanges, controles.parcelas.valueChanges,
      controles.andamento.valueChanges, controles.parcelaInicial.valueChanges).pipe(
      debounceTime(300),
      map(() => this.chaveDaPrevia()),
      distinctUntilChanged(),
      switchMap(() => this.pedirPrevia()),
      takeUntilDestroyed(),
    ).subscribe((resultado) => {
      this.previa.set(resultado.previa);
      if (resultado.erroDoValor !== null) {
        this.errosDaApi.update((erros) => ({ ...erros, valor: resultado.erroDoValor ?? undefined }));
      }
    });
  }

  carregar(): void {
    this.falhaAoCarregar.set(null);
    const compra: Observable<Compra | null> = this.dados.id === null ? of(null) : this.servico.buscar(this.dados.tipo, this.dados.id);
    forkJoin({ cartoes: this.cartoesService.listar(), compra }).subscribe({
      next: ({ cartoes, compra: carregada }) => {
        const ativos = cartoes.ativos;
        this.carregado.set({ cartoes: ativos, compra: carregada });
        if (carregada !== null && !carregada.somenteLeitura) {
          this.preencher(carregada);
        } else if (this.formulario.controls.cartao.value === null && ativos.length === 1) {
          this.formulario.controls.cartao.setValue(ativos[0].id);
        }
      },
      error: (erro: unknown) => this.falhaAoCarregar.set(statusDoErro(erro) === 404
        ? detalheDoErro(erro, this.estorno ? 'Estorno não encontrado.' : 'Compra não encontrada.')
        : this.estorno ? 'Não foi possível carregar o estorno.' : 'Não foi possível carregar a compra.'),
    });
  }

  erro(campo: CampoDaCompra): string | null {
    const daApi = this.errosDaApi()[campo];
    if (daApi !== undefined) {
      return daApi;
    }
    return this.formulario.controls[campo].touched ? (this.errosLocais()[campo] ?? null) : null;
  }

  /** Ao sair do campo, reescreve o valor no formato "1.234,56". */
  formatarValor(): void {
    const controle = this.formulario.controls.valor;
    const lido = lerValorDigitado(controle.value);
    if ('valor' in lido) {
      controle.setValue(escreverValorDigitado(lido.valor), { emitEvent: false });
    }
  }

  /** Fecha sem resultado, como o Esc. */
  cancelar(): void {
    this.referencia.close();
  }

  salvar(): void {
    if (this.enviando() || this.carregado() === null) {
      return;
    }
    this.formulario.markAllAsTouched();
    this.errosDaApi.set({});
    this.erroGeral.set(null);
    if (Object.keys(this.errosLocais()).length > 0) {
      return;
    }
    const valores = this.formulario.getRawValue();
    const lido = lerValorDigitado(valores.valor);
    if (!('valor' in lido)) {
      return;
    }
    const comuns = {
      cartaoId: valores.cartao as number,
      data: valores.data,
      descricao: valores.descricao.trim(),
      categoriaId: valores.categoria,
      observacao: valores.observacao.trim() === '' ? null : valores.observacao.trim(),
    };
    const id = this.dados.id;
    let chamada: Observable<Compra>;
    if (this.estorno) {
      const pedido: DadosDoEstorno = { ...comuns, valor: lido.valor };
      chamada = id === null ? this.servico.criarEstorno(pedido) : this.servico.atualizarEstorno(id, pedido);
    } else {
      const pedido: DadosDaCompra = { ...comuns, valorTotal: lido.valor, parcelas: valores.parcelas,
        parcelaInicial: this.emAndamento() ? Number(valores.parcelaInicial) : 1 };
      chamada = id === null ? this.servico.criarCompra(pedido) : this.servico.atualizarCompra(id, pedido);
    }
    this.enviando.set(true);
    // Esc e clique fora esperam a resposta: fechar agora perderia o recarregamento da tela.
    this.referencia.disableClose = true;
    chamada.subscribe({
      next: (salva) => {
        this.avisarQueSalvou(salva);
        this.referencia.close('salvo');
      },
      error: (erro: unknown) => {
        this.tratarErro(erro);
        this.enviando.set(false);
        this.referencia.disableClose = false;
      },
    });
  }

  /** Decisão 19: a confirmação diz de quais faturas as parcelas saem; confirmada, fecha com "excluido". */
  excluir(): void {
    const compra = this.carregado()?.compra ?? null;
    if (compra === null || this.enviando()) {
      return;
    }
    const parcelas = compra.parcelasNoFlux;
    const primeira = nomeDoMes(parcelas[0].mes);
    const ultima = nomeDoMes(parcelas[parcelas.length - 1].mes);
    const paragrafo: TrechoDeTexto[] = this.estorno ? [`O estorno sai da fatura de ${primeira}. `]
      : parcelas.length === 1 ? [`A compra sai da fatura de ${primeira}. `]
        : ['As ', { forte: `${parcelas.length} parcelas` }, ` saem das faturas de ${primeira} a ${ultima}. `];
    abrirConfirmacao(this.dialogo, {
      titulo: `Excluir ${compra.descricao}?`,
      icone: 'lixeira',
      perigo: true,
      paragrafos: [[...paragrafo, 'Isso não pode ser desfeito.']],
      confirmar: this.estorno ? 'Excluir estorno' : 'Excluir compra',
      falha: 'Não foi possível excluir. Tente novamente.',
      acao: () => this.servico.excluir(this.dados.tipo, compra.id),
    }).subscribe((excluida) => {
      if (excluida === true) {
        this.snackBar.open(this.estorno ? 'Estorno excluído.' : 'Compra excluída.', 'Fechar', { duration: 4000 });
        this.referencia.close('excluido');
      }
    });
  }

  private preencher(compra: Compra): void {
    const controles = this.formulario.controls;
    controles.cartao.setValue(compra.cartaoId);
    controles.data.setValue(compra.data);
    controles.descricao.setValue(compra.descricao);
    controles.valor.setValue(escreverValorDigitado(compra.valorTotal));
    controles.parcelas.setValue(compra.parcelas);
    controles.andamento.setValue(compra.parcelaInicial > 1);
    controles.parcelaInicial.setValue(String(compra.parcelaInicial));
    controles.categoria.setValue(compra.categoriaId);
    controles.observacao.setValue(compra.observacao ?? '');
  }

  /** "Compra lançada em 10 parcelas." com "Ver fatura" (a da 1ª parcela no Flux); na edição, "Compra salva.". */
  private avisarQueSalvou(salva: Compra): void {
    if (this.editando) {
      this.snackBar.open(this.estorno ? 'Estorno salvo.' : 'Compra salva.', 'Fechar', { duration: 4000 });
      return;
    }
    const quantas = salva.parcelasNoFlux.length;
    const texto = this.estorno ? 'Estorno lançado.' : quantas === 1 ? 'Compra lançada.' : `Compra lançada em ${quantas} parcelas.`;
    const mes = salva.parcelasNoFlux[0].mes;
    this.snackBar.open(texto, 'Ver fatura', { duration: 8000 }).onAction()
      .subscribe(() => void this.router.navigate(['/cartoes', salva.cartaoId, 'faturas', mes]));
  }

  /** O que a prévia usa: o cartão, a data, o valor lido, as parcelas e a primeira parcela que vale (1 fora do andamento). */
  private chaveDaPrevia(): string {
    const valores = this.formulario.getRawValue();
    const lido = lerValorDigitado(valores.valor);
    return JSON.stringify([valores.cartao, valores.data, 'valor' in lido ? lido.valor : valores.valor, valores.parcelas,
      this.emAndamento() ? Number(valores.parcelaInicial) : 1]);
  }

  /** A prévia de agora, ou null quando os campos ainda não servem; o 400 do valor (o mínimo) volta como erro dele. */
  private pedirPrevia(): Observable<{ previa: PreviaDaCompra | null; erroDoValor: string | null }> {
    const valores = this.formulario.getRawValue();
    const lido = lerValorDigitado(valores.valor);
    const inicial = this.emAndamento() ? Number(valores.parcelaInicial) : 1;
    const erros = this.errosLocais();
    // A data fora da faixa (a que a compra antiga já tem, na edição) não pede prévia: a API recusaria.
    if (valores.cartao === null || !('valor' in lido) || erros.valor !== undefined || erros.data !== undefined
      || erros.parcelaInicial !== undefined || valores.data < this.minima || valores.data > this.limite) {
      return of({ previa: null, erroDoValor: null });
    }
    return this.servico.previa({ cartaoId: valores.cartao, data: valores.data, valorTotal: lido.valor, parcelas: valores.parcelas,
      parcelaInicial: inicial }).pipe(
      map((previa) => ({ previa, erroDoValor: null })),
      catchError((erro: unknown) => of({ previa: null, erroDoValor: statusDoErro(erro) === 400 ? (camposDoErro(erro)['valorTotal'] ?? null) : null })),
    );
  }

  /** As regras da tela, iguais às da API, para avisar antes de enviar. */
  private errosLocais(): Partial<Record<CampoDaCompra, string>> {
    const valores = this.formulario.getRawValue();
    const erros: Partial<Record<CampoDaCompra, string>> = {};
    if (valores.cartao === null) {
      erros.cartao = 'Escolha o cartão.';
    }
    // Decisão 16: a faixa vale para a data que se lança; na edição, a data que a compra já tem fica.
    const dataDeAntes = this.carregado()?.compra?.data ?? null;
    if (valores.data === '') {
      erros.data = 'Informe a data.';
    } else if (valores.data === dataDeAntes) {
      // A data não mudou.
    } else if (valores.data > this.limite) {
      erros.data = `A data pode ir até ${dataCurta(this.limite)}.`;
    } else if (valores.data < this.minima) {
      erros.data = `A data precisa ser ${dataCurta(this.minima)} ou depois.`;
    }
    if (valores.descricao.trim() === '') {
      erros.descricao = 'Escreva a descrição.';
    } else if (valores.descricao.trim().length > 255) {
      erros.descricao = 'Use no máximo 255 caracteres.';
    }
    const lido = lerValorDigitado(valores.valor);
    if ('erro' in lido) {
      erros.valor = MENSAGENS_DO_VALOR[lido.erro];
    } else if (lido.valor < 0) {
      erros.valor = MENSAGENS_DO_VALOR.sinal;
    } else if (lido.valor === 0) {
      erros.valor = MENSAGENS_DO_VALOR.zero;
    }
    if (this.emAndamento()) {
      const inicial = Number(valores.parcelaInicial);
      if (!/^\d{1,2}$/.test(valores.parcelaInicial.trim()) || inicial < 1 || inicial > valores.parcelas) {
        erros.parcelaInicial = `A primeira parcela no Flux vai da 1ª à ${valores.parcelas}ª.`;
      }
    }
    if (valores.observacao.trim().length > 1000) {
      erros.observacao = 'Use no máximo 1.000 caracteres.';
    }
    return erros;
  }

  private tratarErro(erro: unknown): void {
    const status = statusDoErro(erro);
    if (status === 400) {
      const conhecidos: Partial<Record<CampoDaCompra, string>> = {};
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
    } else if (status === 422 && codigoDoErro(erro) === 'CATEGORIA_INVALIDA') {
      this.errosDaApi.set({ categoria: detalheDoErro(erro, 'Escolha outra categoria.') });
    } else if (status === 422 || status === 404) {
      this.erroGeral.set(detalheDoErro(erro, FALHA));
    } else {
      this.erroGeral.set(FALHA);
    }
  }
}

/** Abre o diálogo da compra (ou do estorno) com o painel certo; o Observable emite como ele fechou. */
export function abrirCompra(dialogo: MatDialog, dados: DadosDoDialogoDeCompra): Observable<ResultadoDaCompra | undefined> {
  return dialogo.open<CompraDialogo, DadosDoDialogoDeCompra, ResultadoDaCompra>(CompraDialogo, {
    data: dados,
    panelClass: ['flux-dialogo', 'flux-dialogo--cheio-no-celular'],
    width: '560px',
    maxWidth: 'calc(100vw - 32px)',
    maxHeight: 'calc(100vh - 32px)',
    autoFocus: 'first-heading',
  }).afterClosed();
}
