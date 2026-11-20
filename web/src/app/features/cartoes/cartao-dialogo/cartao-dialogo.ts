import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { Observable, catchError, debounceTime, merge, of, switchMap } from 'rxjs';

import { Alerta } from '../../../shared/alerta/alerta';
import { Icone } from '../../../shared/icone/icone';
import { ErroDoValor, escreverValorDigitado, lerValorDigitado } from '../../../shared/moeda/valor-digitado';
import { camposDoErro, codigoDoErro, detalheDoErro, statusDoErro } from '../../auth/erros-http';
import { CORES_DE_CONTA } from '../../contas/apresentacao';
import { ListaDeContas } from '../../contas/contas.service';
import { diaEMes, mesSemAno } from '../apresentacao';
import { Cartao, CartoesService, CorCartao, DadosDoCartao, PreviaDosDias } from '../cartoes.service';

export interface DadosDoDialogoDeCartao {
  /** null = Novo cartão. */
  readonly cartao: Cartao | null;
  /** As ativas viram as opções da conta de pagamento; a arquivada que o cartão já tem aparece na edição. */
  readonly contas: ListaDeContas;
}

type CampoDoCartao = 'nome' | 'limite' | 'diaFechamento' | 'diaVencimento' | 'conta';

interface OpcaoDeConta {
  readonly id: number;
  readonly rotulo: string;
}

const MENSAGENS_DO_LIMITE: Readonly<Record<ErroDoValor | 'zero', string>> = {
  vazio: 'Informe o limite.',
  invalido: 'Digite um valor como 1.234,56.',
  casas: 'Use até 2 casas decimais e até 13 dígitos antes da vírgula.',
  grande: 'Use até 2 casas decimais e até 13 dígitos antes da vírgula.',
  zero: 'O limite precisa ser maior que zero.',
};

const DIA_FORA = 'Escolha um dia de 1 a 31.';
const DIAS_QUE_EMPATAM = 'Com o fechamento no dia 28 ou depois, o vencimento precisa ser num dia antes dele, no mês seguinte.';
const FALHA = 'Não foi possível salvar o cartão. Tente novamente.';
/** A exceção da decisão 7: o cartão sem lançamento e sem ajuste de fechamento. */
const VALEM_JA = 'O cartão ainda não tem lançamentos: os dias novos valem já.';

/** Campo do 400 da API → campo do formulário. */
const CAMPOS_DA_API: Readonly<Record<string, CampoDoCartao>> = {
  nome: 'nome',
  limite: 'limite',
  diaFechamento: 'diaFechamento',
  diaVencimento: 'diaVencimento',
  contaPagamentoId: 'conta',
};

/** Um dia de 1 a 31 digitado; null se não é. */
function diaDigitado(texto: string): number | null {
  return /^\d{1,2}$/.test(texto.trim()) && Number(texto) >= 1 && Number(texto) <= 31 ? Number(texto) : null;
}

/**
 * "Novo cartão" e "Editar cartão" (decisões 1 a 4 e 7 da 05a). A dica embaixo dos dias diz a regra com os dias
 * escolhidos. Na edição, mudar os dias mostra, pela prévia da API, a partir de qual fatura eles valem, ou que valem já
 * (o cartão sem lançamento). Fecha com o cartão salvo.
 */
@Component({
  selector: 'app-cartao-dialogo',
  imports: [MatButtonModule, MatDialogModule, ReactiveFormsModule, Alerta, Icone],
  templateUrl: './cartao-dialogo.html',
  styleUrl: './cartao-dialogo.scss',
})
export class CartaoDialogo {
  private readonly servico = inject(CartoesService);
  private readonly referencia = inject<MatDialogRef<CartaoDialogo, Cartao>>(MatDialogRef);

  readonly dados = inject<DadosDoDialogoDeCartao>(MAT_DIALOG_DATA);
  readonly cartao = this.dados.cartao;
  readonly editando = this.cartao !== null;
  readonly cores = CORES_DE_CONTA;
  /** Decisão 4: as ativas corrente, poupança e carteira; na edição, a conta que o cartão já tem, mesmo arquivada. */
  readonly opcoesDeConta: readonly OpcaoDeConta[] = this.montarOpcoesDeConta();
  readonly formulario = new FormGroup({
    nome: new FormControl(this.cartao?.nome ?? '', { nonNullable: true }),
    limite: new FormControl(this.cartao === null ? '' : escreverValorDigitado(this.cartao.limite), { nonNullable: true }),
    diaFechamento: new FormControl(this.cartao === null ? '' : String(this.cartao.diaFechamento), { nonNullable: true }),
    diaVencimento: new FormControl(this.cartao === null ? '' : String(this.cartao.diaVencimento), { nonNullable: true }),
    conta: new FormControl<number | null>(this.cartao?.contaPagamentoId ?? (this.opcoesDeConta.length === 1 ? this.opcoesDeConta[0].id : null)),
    cor: new FormControl<CorCartao | null>(this.cartao?.cor ?? null),
  });
  readonly enviando = signal(false);
  readonly erroGeral = signal<string | null>(null);
  /** Erros que vieram da API, por campo; somem quando o campo muda. */
  readonly errosDaApi = signal<Partial<Record<CampoDoCartao, string>>>({});
  /** Decisão 7: a prévia que a API calculou para os dias do formulário, ou null. */
  readonly previaDosDias = signal<PreviaDosDias | null>(null);
  private readonly fechamentoDigitado = toSignal(this.formulario.controls.diaFechamento.valueChanges,
    { initialValue: this.formulario.controls.diaFechamento.value });
  private readonly vencimentoDigitado = toSignal(this.formulario.controls.diaVencimento.valueChanges,
    { initialValue: this.formulario.controls.diaVencimento.value });
  /** Decisões 3 e 8 com os dias escolhidos: "A compra até o dia 3 entra na fatura que vence no dia 10 do mesmo mês; …". */
  readonly dicaDosDias = computed(() => {
    const fechamento = diaDigitado(this.fechamentoDigitado());
    const vencimento = diaDigitado(this.vencimentoDigitado());
    if (fechamento === null || vencimento === null || (vencimento > fechamento && fechamento >= 28)) {
      return null;
    }
    return `A compra até o dia ${fechamento} entra na fatura que vence no dia ${vencimento} `
      + `${vencimento > fechamento ? 'do mesmo mês' : 'do mês seguinte'}; depois do dia ${fechamento}, na fatura seguinte.`;
  });
  readonly avisoDaTroca = computed(() => {
    const previa = this.previaDosDias();
    if (previa?.valemJa === true) {
      return { titulo: VALEM_JA, texto: null };
    }
    const troca = previa?.troca ?? null;
    if (troca === null) {
      return null;
    }
    const primeira = mesSemAno(troca.primeiraMes);
    return {
      titulo: `Os dias novos valem a partir da fatura de ${primeira}.`,
      texto: `A de ${mesSemAno(troca.abertaMes)} (aberta) continua fechando em ${diaEMes(troca.abertaFechamento)}. `
        + `${primeira.charAt(0).toLocaleUpperCase('pt-BR')}${primeira.slice(1)} passa a fechar em ${diaEMes(troca.primeiraFechamento)} e vencer em `
        + `${diaEMes(troca.primeiraVencimento)}; as compras de ${diaEMes(troca.primeiraInicio)} a ${diaEMes(troca.primeiraFechamento)} ficam nela.`,
    };
  });

  constructor() {
    for (const campo of ['nome', 'limite', 'diaFechamento', 'diaVencimento', 'conta'] as const) {
      const controle: AbstractControl = this.formulario.controls[campo];
      controle.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
        this.errosDaApi.update(({ [campo]: removido, ...outros }) => {
          void removido;
          return outros;
        });
      });
    }
    const cartao = this.cartao;
    if (cartao !== null) {
      // Decisão 7: a prévia da troca, 300 ms depois de mudar um dos dias (só eles); dias que não servem não pedem nada.
      merge(this.formulario.controls.diaFechamento.valueChanges, this.formulario.controls.diaVencimento.valueChanges).pipe(
        debounceTime(300),
        switchMap((): Observable<PreviaDosDias | null> => {
          const fechamento = diaDigitado(this.formulario.controls.diaFechamento.value);
          const vencimento = diaDigitado(this.formulario.controls.diaVencimento.value);
          if (fechamento === null || vencimento === null || this.erroDosDias() !== null
            || (fechamento === cartao.diaFechamento && vencimento === cartao.diaVencimento)) {
            return of(null);
          }
          return this.servico.previaDosDias(cartao.id, fechamento, vencimento).pipe(catchError(() => of(null)));
        }),
        takeUntilDestroyed(),
      ).subscribe((previa) => this.previaDosDias.set(previa));
    }
  }

  erro(campo: CampoDoCartao): string | null {
    const daApi = this.errosDaApi()[campo];
    if (daApi !== undefined) {
      return daApi;
    }
    return this.formulario.controls[campo].touched ? (this.errosLocais()[campo] ?? null) : null;
  }

  /** Ao sair do campo, reescreve o limite no formato "1.234,56". */
  formatarLimite(): void {
    const controle = this.formulario.controls.limite;
    const lido = lerValorDigitado(controle.value);
    if ('valor' in lido) {
      controle.setValue(escreverValorDigitado(lido.valor), { emitEvent: false });
    }
  }

  /** Fecha sem resultado (undefined), como o Esc e o clique fora. */
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
    const lido = lerValorDigitado(valores.limite);
    if (!('valor' in lido)) {
      return;
    }
    const pedido: DadosDoCartao = {
      nome: valores.nome.trim(),
      limite: lido.valor,
      diaFechamento: Number(valores.diaFechamento),
      diaVencimento: Number(valores.diaVencimento),
      contaPagamentoId: valores.conta as number,
      cor: valores.cor,
    };
    this.enviando.set(true);
    // Esc e clique fora esperam a resposta: fechar agora perderia o recarregamento da lista.
    this.referencia.disableClose = true;
    const chamada = this.cartao === null ? this.servico.criar(pedido) : this.servico.atualizar(this.cartao.id, pedido);
    chamada.subscribe({
      next: (salvo) => this.referencia.close(salvo),
      error: (erro: unknown) => {
        this.tratarErro(erro);
        this.enviando.set(false);
        this.referencia.disableClose = false;
      },
    });
  }

  /** As regras da tela, iguais às da API, para avisar antes de enviar. */
  private errosLocais(): Partial<Record<CampoDoCartao, string>> {
    const valores = this.formulario.getRawValue();
    const erros: Partial<Record<CampoDoCartao, string>> = {};
    if (valores.nome.trim() === '') {
      erros.nome = 'Escreva o nome do cartão.';
    } else if (valores.nome.trim().length > 60) {
      erros.nome = 'O nome pode ter até 60 caracteres.';
    }
    const lido = lerValorDigitado(valores.limite);
    if ('erro' in lido) {
      erros.limite = MENSAGENS_DO_LIMITE[lido.erro];
    } else if (lido.valor <= 0) {
      erros.limite = MENSAGENS_DO_LIMITE.zero;
    }
    if (diaDigitado(valores.diaFechamento) === null) {
      erros.diaFechamento = DIA_FORA;
    }
    if (diaDigitado(valores.diaVencimento) === null) {
      erros.diaVencimento = DIA_FORA;
    } else if (this.erroDosDias() !== null) {
      erros.diaVencimento = DIAS_QUE_EMPATAM;
    }
    if (valores.conta === null) {
      erros.conta = 'Escolha a conta de pagamento.';
    }
    return erros;
  }

  /** Decisão 3: com o vencimento depois do fechamento, o fechamento vai até o dia 27. */
  private erroDosDias(): string | null {
    const fechamento = diaDigitado(this.formulario.controls.diaFechamento.value);
    const vencimento = diaDigitado(this.formulario.controls.diaVencimento.value);
    return fechamento !== null && vencimento !== null && vencimento > fechamento && fechamento >= 28 ? DIAS_QUE_EMPATAM : null;
  }

  private montarOpcoesDeConta(): OpcaoDeConta[] {
    const opcoes: OpcaoDeConta[] = this.dados.contas.ativas.filter((conta) => conta.tipo !== 'INVESTIMENTO')
      .map((conta) => ({ id: conta.id, rotulo: conta.nome }));
    const cartao = this.dados.cartao;
    if (cartao !== null && !opcoes.some((opcao) => opcao.id === cartao.contaPagamentoId)) {
      opcoes.unshift({ id: cartao.contaPagamentoId, rotulo: cartao.contaPagamentoArquivada ? `${cartao.contaPagamentoNome} (arquivada)`
        : cartao.contaPagamentoNome });
    }
    return opcoes;
  }

  private tratarErro(erro: unknown): void {
    const status = statusDoErro(erro);
    if (status === 400) {
      const conhecidos: Partial<Record<CampoDoCartao, string>> = {};
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
    } else if (status === 422 && codigoDoErro(erro) === 'NOME_EM_USO') {
      this.errosDaApi.set({ nome: detalheDoErro(erro, 'Já existe um cartão ativo com este nome.') });
    } else if (status === 422 && codigoDoErro(erro) === 'CONTA_INVALIDA') {
      this.errosDaApi.set({ conta: detalheDoErro(erro, 'Escolha outra conta.') });
    } else if (status === 422 || status === 404) {
      this.erroGeral.set(detalheDoErro(erro, FALHA));
    } else {
      this.erroGeral.set(FALHA);
    }
  }
}
