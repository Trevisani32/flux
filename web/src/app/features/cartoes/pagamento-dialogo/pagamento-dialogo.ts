import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, filter, forkJoin, map, merge, of, take } from 'rxjs';

import { Alerta } from '../../../shared/alerta/alerta';
import { abrirConfirmacao } from '../../../shared/confirmacao/confirmacao-dialogo';
import { dataCurta } from '../../../shared/datas/data-curta';
import { Icone } from '../../../shared/icone/icone';
import { formatarMoeda } from '../../../shared/moeda/moeda';
import { ErroDoValor, escreverValorDigitado, lerValorDigitado } from '../../../shared/moeda/valor-digitado';
import { Valor } from '../../../shared/valor/valor';
import { camposDoErro, detalheDoErro, propriedadeDoErro, statusDoErro } from '../../auth/erros-http';
import { Conta, ContasService } from '../../contas/contas.service';
import { nomeDaFatura } from '../apresentacao';
import { CartoesService, Fatura } from '../cartoes.service';
import { DadosDoPagamento, Pagamento, PagamentosService } from '../pagamentos.service';

export interface DadosDoDialogoDePagamento {
  /** "Pagar fatura": o cartão e o mês da fatura. Na edição, os dois vêm do pagamento. */
  readonly cartaoId: number | null;
  readonly mes: string | null;
  /** null = "Pagar fatura"; senão, o pagamento que se edita. */
  readonly pagamentoId: number | null;
  /** Hoje no fuso do negócio ("2026-11-12"): a data padrão e o máximo (decisão 30). */
  readonly hoje: string;
}

/**
 * Como o diálogo fecha quando algo mudou; sem mudança, fecha sem resultado. "inexistente": o pagamento (ou, em "Pagar
 * fatura", o cartão) não existe mais, excluído em outra aba, ao carregar ou ao salvar, e quem abriu recarrega.
 */
export type ResultadoDoPagamento = 'pago' | 'salvo' | 'excluido' | 'desfeito' | 'inexistente';

type CampoDoPagamento = 'conta' | 'data' | 'valor' | 'observacao';

const MENSAGENS_DO_VALOR: Readonly<Record<ErroDoValor | 'sinal' | 'zero', string>> = {
  vazio: 'Informe o valor.',
  invalido: 'Digite um valor como 1.234,56.',
  casas: 'Use no máximo 2 casas decimais.',
  grande: 'Use no máximo 13 dígitos antes da vírgula.',
  sinal: 'Digite o valor sem sinal.',
  zero: 'O valor precisa ser maior que zero.',
};

/** Campo do 400 (e do 422 com "campo") da API → campo do formulário. */
const CAMPOS_DA_API: Readonly<Record<string, CampoDoPagamento>> = {
  contaId: 'conta',
  data: 'data',
  valor: 'valor',
  observacao: 'observacao',
};

const FALHA = 'Não foi possível salvar. Tente novamente.';

interface Carregado {
  readonly contas: readonly Conta[];
  readonly fatura: Fatura | null;
  readonly pagamento: Pagamento | null;
}

/**
 * "Pagar fatura" e o pagamento que já existe (Fatia 05b, decisões 30, 31, 33 e 37): a conta (a de pagamento do cartão
 * vem preenchida, se estiver ativa), a data (até hoje), o valor (vem o pendente; parcial e a mais podem) e a observação.
 * O importado só muda a observação e se desfaz; o lançado à mão se exclui. Com a conta ou o cartão arquivado, só leitura.
 * No celular, ocupa a tela.
 */
@Component({
  selector: 'app-pagamento-dialogo',
  imports: [MatButtonModule, MatDialogModule, ReactiveFormsModule, Alerta, Icone, Valor],
  templateUrl: './pagamento-dialogo.html',
  styleUrl: './pagamento-dialogo.scss',
})
export class PagamentoDialogo {
  private readonly servico = inject(PagamentosService);
  private readonly cartoesService = inject(CartoesService);
  private readonly contasService = inject(ContasService);
  private readonly referencia = inject<MatDialogRef<PagamentoDialogo, ResultadoDoPagamento>>(MatDialogRef);
  private readonly dialogo = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly dados = inject<DadosDoDialogoDePagamento>(MAT_DIALOG_DATA);
  readonly editando = this.dados.pagamentoId !== null;
  readonly dataCurta = dataCurta;
  readonly carregado = signal<Carregado | null>(null);
  readonly falhaAoCarregar = signal<string | null>(null);
  readonly formulario = new FormGroup({
    conta: new FormControl<number | null>(null),
    data: new FormControl(this.dados.hoje, { nonNullable: true }),
    valor: new FormControl('', { nonNullable: true }),
    observacao: new FormControl('', { nonNullable: true }),
  });
  readonly enviando = signal(false);
  private inexistente = false;
  readonly erroGeral = signal<string | null>(null);
  /** Erros que vieram da API, por campo; somem quando o campo muda. */
  readonly errosDaApi = signal<Partial<Record<CampoDoPagamento, string>>>({});
  /** Decisão 37: o importado convertido; conta, data e valor vêm do extrato. */
  readonly importado = computed(() => this.carregado()?.pagamento?.origem === 'IMPORTADO');
  /** "dezembro" ou, fora do ano de hoje, "dezembro de 2025". */
  readonly nomeDaFatura = computed(() => {
    const mes = this.carregado()?.pagamento?.faturaMes ?? this.dados.mes ?? '';
    return mes === '' ? '' : nomeDaFatura(mes, this.dados.hoje);
  });
  readonly titulo = computed(() => this.editando ? `Pagamento da fatura de ${this.nomeDaFatura()}` : `Pagar a fatura de ${this.nomeDaFatura()}`);
  /** Decisões 4 e 30: as contas ativas que pagam fatura; na edição, a que o pagamento já tem fica, mesmo que não sirva mais. */
  readonly contas = computed(() => {
    const atual = this.carregado();
    if (atual === null) {
      return [];
    }
    const queServem = atual.contas.filter((conta) => conta.arquivadaEm === null && conta.tipo !== 'INVESTIMENTO');
    const doPagamento = atual.pagamento === null ? undefined : atual.contas.find((conta) => conta.id === atual.pagamento?.contaId);
    return doPagamento !== undefined && !queServem.includes(doPagamento) ? [doPagamento, ...queServem] : queServem;
  });
  /** A conta ou o cartão arquivado que trava o pagamento (decisão 33). */
  readonly motivoDaSoLeitura = computed(() => {
    const pagamento = this.carregado()?.pagamento ?? null;
    if (pagamento === null || !pagamento.somenteLeitura) {
      return null;
    }
    return pagamento.contaArquivada ? { titulo: `${pagamento.contaNome} está arquivada`, onde: 'a conta em Contas', o: 'a' }
      : { titulo: `${pagamento.cartaoNome} está arquivado`, onde: 'o cartão em Cartões', o: 'o' };
  });

  constructor() {
    this.carregar();
    const campos: CampoDoPagamento[] = ['conta', 'data', 'valor', 'observacao'];
    for (const campo of campos) {
      const controle: AbstractControl = this.formulario.controls[campo];
      controle.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
        this.errosDaApi.update(({ [campo]: removido, ...outros }) => {
          void removido;
          return outros;
        });
      });
    }
  }

  carregar(): void {
    this.falhaAoCarregar.set(null);
    const id = this.dados.pagamentoId;
    const pagamento: Observable<Pagamento | null> = id === null ? of(null) : this.servico.buscar(id);
    const fatura: Observable<Fatura | null> = id === null && this.dados.cartaoId !== null && this.dados.mes !== null
      ? this.cartoesService.fatura(this.dados.cartaoId, this.dados.mes) : of(null);
    forkJoin({ contas: this.contasService.listar().pipe(map((lista) => [...lista.ativas, ...lista.arquivadas])), fatura, pagamento })
      .subscribe({
        next: (carregado) => {
          this.carregado.set(carregado);
          this.preencher(carregado);
        },
        error: (erro: unknown) => {
          if (statusDoErro(erro) === 404) {
            this.marcarInexistente();
          }
          this.falhaAoCarregar.set(statusDoErro(erro) === 404 ? detalheDoErro(erro, 'Pagamento não encontrado.')
            : 'Não foi possível carregar o pagamento.');
        },
      });
  }

  erro(campo: CampoDoPagamento): string | null {
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

  /** Fecha sem resultado, como o Esc; com o pagamento que não existe mais, "inexistente". */
  cancelar(): void {
    this.referencia.close(this.inexistente ? 'inexistente' : undefined);
  }

  /** Correções do QA (05b): depois do 404, o Esc e o clique fora também fecham com "inexistente". */
  private marcarInexistente(): void {
    if (this.inexistente) {
      return;
    }
    this.inexistente = true;
    this.referencia.disableClose = true;
    merge(this.referencia.backdropClick(), this.referencia.keydownEvents().pipe(filter((evento) => evento.key === 'Escape')))
      .pipe(take(1)).subscribe(() => this.cancelar());
  }

  salvar(): void {
    const atual = this.carregado();
    if (this.enviando() || atual === null) {
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
    const pedido: DadosDoPagamento = {
      contaId: valores.conta as number,
      data: valores.data,
      valor: lido.valor,
      observacao: valores.observacao.trim() === '' ? null : valores.observacao.trim(),
    };
    const pagamento = atual.pagamento;
    const chamada = pagamento === null ? this.servico.pagar(this.dados.cartaoId as number, this.dados.mes as string, pedido)
      : this.servico.atualizar(pagamento.id, pedido);
    this.enviando.set(true);
    // Esc e clique fora esperam a resposta: fechar agora perderia o recarregamento da tela.
    this.referencia.disableClose = true;
    chamada.subscribe({
      next: () => {
        if (pagamento !== null) {
          this.snackBar.open('Pagamento salvo.', 'Fechar', { duration: 4000 });
        }
        this.referencia.close(pagamento === null ? 'pago' : 'salvo');
      },
      error: (erro: unknown) => {
        this.tratarErro(erro);
        this.enviando.set(false);
        // Depois do 404, o Esc e o clique fora continuam presos ao "inexistente" (marcarInexistente).
        this.referencia.disableClose = this.inexistente;
      },
    });
  }

  /** Decisão 33: o lançado à mão sai, e a fatura volta a ter o valor pendente. */
  excluir(): void {
    const pagamento = this.carregado()?.pagamento ?? null;
    if (pagamento === null || this.enviando()) {
      return;
    }
    abrirConfirmacao(this.dialogo, {
      titulo: 'Excluir o pagamento?',
      icone: 'lixeira',
      perigo: true,
      paragrafos: [['O pagamento de ', { forte: formatarMoeda(pagamento.valor) }, ` sai de ${pagamento.contaNome}, e a fatura de `
        + `${this.nomeDaFatura()} volta a ter esse valor pendente. Isso não pode ser desfeito.`]],
      confirmar: 'Excluir pagamento',
      falha: 'Não foi possível excluir. Tente novamente.',
      acao: () => this.servico.excluir(pagamento.id),
    }).subscribe((excluido) => {
      if (excluido === true) {
        this.snackBar.open('Pagamento excluído.', 'Fechar', { duration: 4000 });
        this.referencia.close('excluido');
      }
    });
  }

  /** Decisão 37: o importado volta a ser a despesa do extrato, sem categoria. */
  desfazer(): void {
    const pagamento = this.carregado()?.pagamento ?? null;
    if (pagamento === null || this.enviando()) {
      return;
    }
    abrirConfirmacao(this.dialogo, {
      titulo: 'Desfazer o pagamento?',
      icone: 'reativar',
      perigo: false,
      paragrafos: [[`O lançamento volta a ser uma despesa de ${pagamento.contaNome}, sem categoria, e a fatura de ${this.nomeDaFatura()} `
        + 'volta a ter esse valor pendente.']],
      confirmar: 'Desfazer pagamento',
      falha: 'Não foi possível desfazer. Tente novamente.',
      acao: () => this.servico.desfazer(pagamento.id),
    }).subscribe((desfeito) => {
      if (desfeito === true) {
        this.snackBar.open('O pagamento voltou a ser despesa.', 'Fechar', { duration: 4000 });
        this.referencia.close('desfeito');
      }
    });
  }

  private preencher(carregado: Carregado): void {
    const controles = this.formulario.controls;
    const pagamento = carregado.pagamento;
    if (pagamento !== null) {
      controles.conta.setValue(pagamento.contaId);
      controles.data.setValue(pagamento.data);
      controles.valor.setValue(escreverValorDigitado(pagamento.valor));
      controles.observacao.setValue(pagamento.observacao ?? '');
      if (pagamento.origem === 'IMPORTADO') {
        controles.conta.disable();
        controles.data.disable();
        controles.valor.disable();
      }
      return;
    }
    const fatura = carregado.fatura;
    // Decisão 4: a conta de pagamento do cartão vem preenchida, se ainda paga fatura (ativa e não de investimento).
    const padrao = this.contas().find((conta) => conta.id === fatura?.contaPagamentoId);
    controles.conta.setValue(padrao?.id ?? null);
    controles.valor.setValue(fatura !== null && (fatura.pendente ?? 0) > 0 ? escreverValorDigitado(fatura.pendente ?? 0) : '');
  }

  /** As regras da tela, iguais às da API, para avisar antes de enviar. */
  private errosLocais(): Partial<Record<CampoDoPagamento, string>> {
    const valores = this.formulario.getRawValue();
    const erros: Partial<Record<CampoDoPagamento, string>> = {};
    if (valores.conta === null) {
      erros.conta = 'Escolha a conta.';
    }
    if (valores.data === '') {
      erros.data = 'Informe a data.';
    } else if (valores.data > this.dados.hoje && !this.importado()) {
      erros.data = `A data pode ir até hoje, ${dataCurta(this.dados.hoje)}.`;
    }
    const lido = lerValorDigitado(valores.valor);
    if ('erro' in lido) {
      erros.valor = MENSAGENS_DO_VALOR[lido.erro];
    } else if (lido.valor < 0) {
      erros.valor = MENSAGENS_DO_VALOR.sinal;
    } else if (lido.valor === 0) {
      erros.valor = MENSAGENS_DO_VALOR.zero;
    }
    if (valores.observacao.trim().length > 1000) {
      erros.observacao = 'Use no máximo 1.000 caracteres.';
    }
    return erros;
  }

  /**
   * O 400 diz os campos em "campos"; o 422 da conta que não serve diz o campo em "campo" (com a mensagem no "detail"). O
   * 404 é o pagamento (ou o cartão) excluído em outra aba: a mensagem fica no alerta, e o diálogo fecha com "inexistente".
   */
  private tratarErro(erro: unknown): void {
    const status = statusDoErro(erro);
    if (status === 404) {
      this.marcarInexistente();
    }
    const campoDoNegocio = status === 422 ? propriedadeDoErro(erro, 'campo') : null;
    const campos = campoDoNegocio === null ? camposDoErro(erro) : { [campoDoNegocio]: detalheDoErro(erro, FALHA) };
    const conhecidos: Partial<Record<CampoDoPagamento, string>> = {};
    for (const [campoDaApi, mensagem] of Object.entries(campos)) {
      const campo = CAMPOS_DA_API[campoDaApi];
      if (campo !== undefined) {
        conhecidos[campo] = mensagem;
      }
    }
    if (Object.keys(conhecidos).length > 0) {
      this.errosDaApi.set(conhecidos);
    } else if (status === 400) {
      this.erroGeral.set('Confira os campos e tente de novo.');
    } else if (status === 422 || status === 404) {
      this.erroGeral.set(detalheDoErro(erro, FALHA));
    } else {
      this.erroGeral.set(FALHA);
    }
  }
}

/** Abre "Pagar fatura" (ou o pagamento que existe) com o painel certo; o Observable emite como ele fechou. */
export function abrirPagamento(dialogo: MatDialog, dados: DadosDoDialogoDePagamento): Observable<ResultadoDoPagamento | undefined> {
  return dialogo.open<PagamentoDialogo, DadosDoDialogoDePagamento, ResultadoDoPagamento>(PagamentoDialogo, {
    data: dados,
    panelClass: ['flux-dialogo', 'flux-dialogo--cheio-no-celular'],
    width: '520px',
    maxWidth: 'calc(100vw - 32px)',
    maxHeight: 'calc(100vh - 32px)',
    autoFocus: 'first-heading',
  }).afterClosed();
}
