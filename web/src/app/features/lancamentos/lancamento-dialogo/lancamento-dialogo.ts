import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable } from 'rxjs';

import { Alerta } from '../../../shared/alerta/alerta';
import { abrirConfirmacao, TrechoDeTexto } from '../../../shared/confirmacao/confirmacao-dialogo';
import { dataCurta } from '../../../shared/datas/data-curta';
import { Icone } from '../../../shared/icone/icone';
import { partesDaMoeda, SINAL_MENOS } from '../../../shared/moeda/moeda';
import { ErroDoValor, escreverValorDigitado, lerValorDigitado } from '../../../shared/moeda/valor-digitado';
import { Valor } from '../../../shared/valor/valor';
import { camposDoErro, codigoDoErro, detalheDoErro, statusDoErro } from '../../auth/erros-http';
import { ListaDeCategorias } from '../../categorias/categorias.service';
import { Conta, ListaDeContas } from '../../contas/contas.service';
import { OpcaoDeCategoria, opcoesDeCategoria } from '../apresentacao';
import { DadosDaTransferencia, DadosDoLancamento, ItemDaLista, LancamentosService } from '../lancamentos.service';

export interface DadosDoDialogoDeLancamento {
  /** null = Novo. */
  readonly item: ItemDaLista | null;
  /** Hoje no fuso do negócio ("2026-11-09"). */
  readonly hoje: string;
  /** Ativas (o formulário) e arquivadas (o nome da conta que deixa o item só leitura). */
  readonly contas: ListaDeContas;
  readonly categorias: ListaDeCategorias;
  /** Conta já escolhida no Novo (decisão 16; `contaPadrao`). */
  readonly contaPadrao: number | null;
}

/** Como o diálogo fecha quando algo mudou; sem mudança, fecha sem resultado. */
export type ResultadoDoLancamento = 'salvo' | 'excluido';

type TipoDoFormulario = 'DESPESA' | 'RECEITA' | 'TRANSFERENCIA';
type CampoDoLancamento = 'valor' | 'data' | 'descricao' | 'conta' | 'categoria' | 'origem' | 'destino' | 'observacao';

const MENSAGENS_DO_VALOR: Readonly<Record<ErroDoValor | 'sinal' | 'zero', string>> = {
  vazio: 'Informe o valor.',
  invalido: 'Digite um valor como 1.234,56.',
  casas: 'Use no máximo 2 casas decimais.',
  grande: 'Use no máximo 13 dígitos antes da vírgula.',
  sinal: 'Digite o valor sem sinal.',
  zero: 'O valor precisa ser maior que zero.',
};

/** Campo do 400 da API → campo do formulário. */
const CAMPOS_DA_API: Readonly<Record<string, CampoDoLancamento>> = {
  valor: 'valor',
  data: 'data',
  descricao: 'descricao',
  contaId: 'conta',
  categoriaId: 'categoria',
  contaOrigemId: 'origem',
  contaDestinoId: 'destino',
  observacao: 'observacao',
};

const FALHA = 'Não foi possível salvar. Tente novamente.';

/** Hoje + 12 meses, como o plusMonths do Java: 29/02 vira 28/02 (decisão 21). */
export function dataLimite(hoje: string): string {
  const [ano, mes, dia] = hoje.split('-').map(Number);
  const ultimoDia = new Date(Date.UTC(ano + 1, mes, 0)).getUTCDate();
  return `${ano + 1}-${String(mes).padStart(2, '0')}-${String(Math.min(dia, ultimoDia)).padStart(2, '0')}`;
}

/** Conta do Novo (decisão 16): a do filtro, a última usada, a única ativa ou nenhuma. */
export function contaPadrao(ativas: readonly Conta[], doFiltro: number | null, ultima: number | null): number | null {
  const ativa = (id: number | null) => id !== null && ativas.some((conta) => conta.id === id);
  if (ativa(doFiltro)) {
    return doFiltro;
  }
  if (ativa(ultima)) {
    return ultima;
  }
  return ativas.length === 1 ? ativas[0].id : null;
}

/** O valor com o sinal do tipo, como texto: "−R$ 287,43", "+R$ 7.200,00" ou "R$ 1.000,00". */
function valorComSinal(item: ItemDaLista): string {
  const partes = partesDaMoeda(item.valor);
  if (partes === null || item.tipo === 'TRANSFERENCIA') {
    return partes?.absoluto ?? '';
  }
  return `${partes.negativo ? SINAL_MENOS : '+'}${partes.absoluto}`;
}

/**
 * Novo e Editar de receita, despesa e transferência num diálogo só (decisão 2). Fecha com
 * "salvo" ou "excluido"; o lançamento de uma conta arquivada abre só para leitura (decisões 23 e 28).
 */
@Component({
  selector: 'app-lancamento-dialogo',
  imports: [MatButtonModule, MatDialogModule, ReactiveFormsModule, Alerta, Icone, Valor],
  templateUrl: './lancamento-dialogo.html',
  styleUrl: './lancamento-dialogo.scss',
})
export class LancamentoDialogo {
  private readonly servico = inject(LancamentosService);
  private readonly referencia = inject<MatDialogRef<LancamentoDialogo, ResultadoDoLancamento>>(MatDialogRef);
  private readonly dialogo = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly dados = inject<DadosDoDialogoDeLancamento>(MAT_DIALOG_DATA);
  readonly item = this.dados.item;
  readonly editando = this.item !== null;
  readonly limite = dataLimite(this.dados.hoje);
  readonly contasAtivas = this.dados.contas.ativas;
  readonly somenteLeitura = this.item?.somenteLeitura ?? false;
  /** No item só leitura: a conta arquivada que trava o item. */
  readonly contaArquivada = this.dados.contas.arquivadas
    .find((conta) => conta.id === this.item?.contaId || conta.id === this.item?.contaDestinoId)?.nome ?? null;
  readonly formulario = new FormGroup({
    tipo: new FormControl<TipoDoFormulario>(this.item?.tipo ?? 'DESPESA', { nonNullable: true }),
    valor: new FormControl(this.item === null ? '' : escreverValorDigitado(Math.abs(this.item.valor)), { nonNullable: true }),
    data: new FormControl(this.item?.data ?? this.dados.hoje, { nonNullable: true }),
    descricao: new FormControl(this.descricaoInicial(), { nonNullable: true }),
    conta: new FormControl<number | null>(this.item === null ? this.dados.contaPadrao : this.item.tipo === 'TRANSFERENCIA' ? null : this.item.contaId),
    categoria: new FormControl<number | null>(this.item?.categoriaId ?? null),
    origem: new FormControl<number | null>(this.item?.tipo === 'TRANSFERENCIA' ? this.item.contaId : this.dados.contaPadrao),
    destino: new FormControl<number | null>(this.item?.tipo === 'TRANSFERENCIA' ? this.item.contaDestinoId : null),
    observacao: new FormControl(this.item?.observacao ?? '', { nonNullable: true }),
  });
  readonly tipo = toSignal(this.formulario.controls.tipo.valueChanges, { initialValue: this.formulario.controls.tipo.value });
  readonly transferencia = computed(() => this.tipo() === 'TRANSFERENCIA');
  readonly titulo = computed(() => this.somenteLeitura ? (this.item?.descricao ?? '')
    : this.editando ? (this.transferencia() ? 'Editar transferência' : 'Editar lançamento')
      : this.transferencia() ? 'Nova transferência' : 'Novo lançamento');
  /** As categorias da natureza do tipo; na edição, a arquivada que o lançamento já tem (decisão 24). */
  readonly categorias = computed<OpcaoDeCategoria[]>(() => {
    const tipo = this.tipo();
    const opcoes = opcoesDeCategoria(tipo === 'RECEITA' ? this.dados.categorias.receitas : this.dados.categorias.despesas);
    const item = this.item;
    if (item !== null && item.categoriaArquivada && item.categoriaId !== null && item.tipo === tipo) {
      return [{ id: item.categoriaId, rotulo: `${item.categoriaNome} (arquivada)` }, ...opcoes];
    }
    return opcoes;
  });
  readonly enviando = signal(false);
  readonly erroGeral = signal<string | null>(null);
  /** Erros que vieram da API, por campo; somem quando o campo muda. */
  readonly errosDaApi = signal<Partial<Record<CampoDoLancamento, string>>>({});
  /** "Salvar e criar outro" gravou algo: Cancelar fecha com "salvo", para a lista recarregar. */
  private salvouAlgum = false;

  constructor() {
    const campos: CampoDoLancamento[] = ['valor', 'data', 'descricao', 'conta', 'categoria', 'origem', 'destino', 'observacao'];
    for (const campo of campos) {
      const controle: AbstractControl = this.formulario.controls[campo];
      controle.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
        this.errosDaApi.update(({ [campo]: removido, ...outros }) => {
          void removido;
          return outros;
        });
      });
    }
    // Trocar entre receita e despesa limpa a categoria da outra natureza (decisão 15).
    this.formulario.controls.tipo.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      const categoria = this.formulario.controls.categoria;
      if (!this.categorias().some((opcao) => opcao.id === categoria.value)) {
        categoria.setValue(null);
      }
    });
  }

  /** Na edição, lançamento não vira transferência nem o contrário (decisão 15). */
  tipoTravado(tipo: TipoDoFormulario): boolean {
    return this.item !== null && (this.item.tipo === 'TRANSFERENCIA') !== (tipo === 'TRANSFERENCIA');
  }

  textoDoSalvarEOutro(): string {
    return this.transferencia() ? 'Salvar e criar outra' : 'Salvar e criar outro';
  }

  textoDoSalvar(): string {
    return this.editando ? 'Salvar' : this.transferencia() ? 'Criar transferência' : 'Criar lançamento';
  }

  erro(campo: CampoDoLancamento): string | null {
    const daApi = this.errosDaApi()[campo];
    if (daApi !== undefined) {
      return daApi;
    }
    return this.formulario.controls[campo].touched ? (this.errosLocais()[campo] ?? null) : null;
  }

  /** "Data futura: entra no saldo em 15/11/2026." quando a data passa de hoje e está válida. */
  dicaDaData(): string | null {
    const data = this.formulario.controls.data.value;
    return this.erro('data') === null && data > this.dados.hoje ? `Data futura: entra no saldo em ${dataCurta(data)}.` : null;
  }

  /** Ao sair do campo, reescreve o valor no formato "1.234,56". */
  formatarValor(): void {
    const controle = this.formulario.controls.valor;
    const lido = lerValorDigitado(controle.value);
    if ('valor' in lido) {
      controle.setValue(escreverValorDigitado(lido.valor), { emitEvent: false });
    }
  }

  /** Fecha sem resultado, como o Esc, a menos que "Salvar e criar outro" já tenha gravado. */
  cancelar(): void {
    this.referencia.close(this.salvouAlgum ? 'salvo' : undefined);
  }

  salvar(criarOutro: boolean): void {
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
    const lido = lerValorDigitado(valores.valor);
    if (!('valor' in lido)) {
      return;
    }
    const observacao = valores.observacao.trim() === '' ? null : valores.observacao.trim();
    let chamada: Observable<ItemDaLista>;
    let conta: number | null;
    if (valores.tipo === 'TRANSFERENCIA') {
      const pedido: DadosDaTransferencia = {
        valor: lido.valor,
        data: valores.data,
        contaOrigemId: valores.origem as number,
        contaDestinoId: valores.destino as number,
        descricao: valores.descricao.trim() === '' ? null : valores.descricao.trim(),
        observacao,
      };
      chamada = this.item === null ? this.servico.criarTransferencia(pedido) : this.servico.atualizarTransferencia(this.item.id, pedido);
      conta = valores.origem;
    } else {
      const pedido: DadosDoLancamento = {
        tipo: valores.tipo,
        valor: lido.valor,
        data: valores.data,
        descricao: valores.descricao.trim(),
        contaId: valores.conta as number,
        categoriaId: valores.categoria,
        observacao,
      };
      chamada = this.item === null ? this.servico.criarLancamento(pedido) : this.servico.atualizarLancamento(this.item.id, pedido);
      conta = valores.conta;
    }
    this.enviando.set(true);
    // Esc e clique fora esperam a resposta: fechar agora perderia o recarregamento da lista.
    this.referencia.disableClose = true;
    chamada.subscribe({
      next: () => {
        this.servico.ultimaConta = conta;
        this.salvouAlgum = true;
        this.snackBar.open(valores.tipo === 'TRANSFERENCIA' ? 'Transferência salva.' : 'Lançamento salvo.', 'Fechar', { duration: 4000 });
        if (criarOutro) {
          this.recomecar();
        } else {
          this.referencia.close('salvo');
        }
      },
      error: (erro: unknown) => {
        this.tratarErro(erro);
        this.enviando.set(false);
        this.referencia.disableClose = false;
      },
    });
  }

  /** Abre a confirmação por cima; confirmada, fecha com "excluido". */
  excluir(): void {
    const item = this.item;
    if (item === null || this.enviando()) {
      return;
    }
    const transferencia = item.tipo === 'TRANSFERENCIA';
    const paragrafo: TrechoDeTexto[] = transferencia
      ? ['Os dois lados saem do Flux: a saída de ', { forte: valorComSinal(item) },
        ` de ${item.contaNome} e a entrada em ${item.contaDestinoNome}, em ${dataCurta(item.data)}.`]
      : [{ forte: valorComSinal(item) }, ` em ${dataCurta(item.data)}, em ${item.contaNome}. O saldo da conta muda na hora.`];
    abrirConfirmacao(this.dialogo, {
      titulo: transferencia ? 'Excluir a transferência?' : `Excluir ${item.descricao}?`,
      icone: 'lixeira',
      perigo: true,
      paragrafos: [paragrafo, [{ forte: 'Não dá para desfazer.' }]],
      confirmar: transferencia ? 'Excluir transferência' : 'Excluir lançamento',
      falha: 'Não foi possível excluir. Tente novamente.',
      acao: () => transferencia ? this.servico.excluirTransferencia(item.id) : this.servico.excluirLancamento(item.id),
    }).subscribe((excluido) => {
      if (excluido === true) {
        this.snackBar.open(transferencia ? 'Transferência excluída.' : 'Lançamento excluído.', 'Fechar', { duration: 4000 });
        this.referencia.close('excluido');
      }
    });
  }

  private descricaoInicial(): string {
    return this.item?.descricao ?? '';
  }

  /** Depois de "Salvar e criar outro": mantém tipo, data e contas; limpa o resto e volta ao Valor. */
  private recomecar(): void {
    const controles = this.formulario.controls;
    controles.valor.setValue('');
    controles.descricao.setValue('');
    controles.categoria.setValue(null);
    controles.observacao.setValue('');
    this.formulario.markAsUntouched();
    this.enviando.set(false);
    this.referencia.disableClose = false;
    document.getElementById('lancamento-valor')?.focus();
  }

  /** As regras da tela, iguais às da API, para avisar antes de enviar. */
  private errosLocais(): Partial<Record<CampoDoLancamento, string>> {
    const valores = this.formulario.getRawValue();
    const transferencia = valores.tipo === 'TRANSFERENCIA';
    const erros: Partial<Record<CampoDoLancamento, string>> = {};
    const lido = lerValorDigitado(valores.valor);
    if ('erro' in lido) {
      erros.valor = MENSAGENS_DO_VALOR[lido.erro];
    } else if (lido.valor < 0) {
      erros.valor = MENSAGENS_DO_VALOR.sinal;
    } else if (lido.valor === 0) {
      erros.valor = MENSAGENS_DO_VALOR.zero;
    }
    const contas = (transferencia ? [valores.origem, valores.destino] : [valores.conta])
      .map((id) => this.contasAtivas.find((conta) => conta.id === id))
      .filter((conta): conta is Conta => conta !== undefined);
    const desde = contas.reduce<Conta | null>((maisRecente, conta) =>
      maisRecente === null || conta.dataSaldoInicial > maisRecente.dataSaldoInicial ? conta : maisRecente, null);
    if (valores.data === '') {
      erros.data = 'Informe a data.';
    } else if (valores.data > this.limite) {
      erros.data = `A data pode ir até ${dataCurta(this.limite)}.`;
    } else if (desde !== null && valores.data < desde.dataSaldoInicial) {
      erros.data = `A data precisa ser ${dataCurta(desde.dataSaldoInicial)} ou depois, a data do saldo inicial de ${desde.nome}.`;
    }
    if (!transferencia && valores.descricao.trim() === '') {
      erros.descricao = 'Informe a descrição.';
    } else if (valores.descricao.trim().length > 255) {
      erros.descricao = 'Use no máximo 255 caracteres.';
    }
    if (transferencia) {
      if (valores.origem === null) {
        erros.origem = 'Escolha a conta de origem.';
      }
      if (valores.destino === null) {
        erros.destino = 'Escolha a conta de destino.';
      } else if (valores.destino === valores.origem) {
        erros.destino = 'Escolha uma conta diferente da de origem.';
      }
    } else if (valores.conta === null) {
      erros.conta = 'Escolha a conta.';
    }
    if (valores.observacao.trim().length > 1000) {
      erros.observacao = 'Use no máximo 1.000 caracteres.';
    }
    return erros;
  }

  private tratarErro(erro: unknown): void {
    const status = statusDoErro(erro);
    if (status === 400) {
      const campos = camposDoErro(erro);
      const conhecidos: Partial<Record<CampoDoLancamento, string>> = {};
      for (const [campoDaApi, mensagem] of Object.entries(campos)) {
        const campo = CAMPOS_DA_API[campoDaApi];
        if (campo !== undefined) {
          conhecidos[campo] = mensagem;
        }
      }
      this.errosDaApi.set(conhecidos);
      if (Object.keys(conhecidos).length === 0) {
        this.erroGeral.set(campos['tipo'] ?? 'Confira os campos e tente de novo.');
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

/** Abre o diálogo com o painel certo; o Observable emite "salvo", "excluido" ou undefined. */
export function abrirDialogoDeLancamento(dialogo: MatDialog, dados: DadosDoDialogoDeLancamento): Observable<ResultadoDoLancamento | undefined> {
  return dialogo.open<LancamentoDialogo, DadosDoDialogoDeLancamento, ResultadoDoLancamento>(LancamentoDialogo, {
    data: dados,
    panelClass: 'flux-dialogo',
    width: '540px',
    maxWidth: 'calc(100vw - 32px)',
    maxHeight: 'calc(100vh - 32px)',
    autoFocus: dados.item === null ? '#lancamento-valor' : 'first-heading',
  }).afterClosed();
}
