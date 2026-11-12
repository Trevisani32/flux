import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, Subject, catchError, map, of, switchMap } from 'rxjs';

import { Alerta } from '../../../shared/alerta/alerta';
import { dataCurta } from '../../../shared/datas/data-curta';
import { Icone } from '../../../shared/icone/icone';
import { Valor } from '../../../shared/valor/valor';
import { camposDoErro, detalheDoErro, propriedadeDoErro, statusDoErro } from '../../auth/erros-http';
import { Conta, ListaDeContas } from '../../contas/contas.service';
import { diaEMes, distancia, valorSemSinal } from '../conversao';
import { ItemDaLista, LancamentosService, ParDoLancamento, ParesDoLancamento } from '../lancamentos.service';

export interface DadosDaConversaoDialogo {
  /** O importado de onde a conversão parte (decisão 26). */
  readonly lancamentoId: number;
  /** As contas ativas são as do "Lançar a entrada em outra conta" (decisão 28). */
  readonly contas: ListaDeContas;
  /** O par que o resultado da importação sugeriu (decisão 35): vem escolhido, se ainda servir. */
  readonly parSugerido: number | null;
}

type CampoDaConversao = 'outroLado' | 'conta' | 'descricao';

/** Uma carga dos pares: a resposta ou o erro, e se ela veio depois de um erro ao converter. */
type CargaDosPares = { readonly pares: ParesDoLancamento; readonly depoisDoErro: boolean }
  | { readonly erro: unknown; readonly depoisDoErro: boolean };

const NOVA = 'nova';
const FALHA = 'Não foi possível converter. Tente novamente.';

/**
 * "Converter em transferência" (decisões 26 a 29): o importado fica fixo, e o outro lado é um dos pares
 * possíveis, o mais perto da data primeiro, ou um lado novo, lançado na hora numa outra conta ativa. Com
 * um par só (ou o sugerido pelo resultado), ele já vem escolhido; sem par, vem escolhido o lado novo.
 * Fecha com a transferência criada. Um 404 ou 422 ao converter mostra a mensagem e recarrega os pares
 * (o par pode ter saído em outra aba), sem escolher nada de novo: a lista velha nunca fica na tela. O 422
 * da conta do lado novo (com `campo: "contaId"`) vai para o select da conta e não recarrega nada: o par
 * não mudou. As contas que ainda não existiam na data do importado aparecem desabilitadas, com o motivo.
 */
@Component({
  selector: 'app-conversao-dialogo',
  imports: [MatButtonModule, MatDialogModule, ReactiveFormsModule, Alerta, Icone, Valor],
  templateUrl: './conversao-dialogo.html',
  styleUrl: './conversao-dialogo.scss',
})
export class ConversaoDialogo {
  private readonly servico = inject(LancamentosService);
  private readonly referencia = inject<MatDialogRef<ConversaoDialogo, ItemDaLista>>(MatDialogRef);
  private readonly snackBar = inject(MatSnackBar);
  private readonly cargas = new Subject<boolean>();

  readonly dados = inject<DadosDaConversaoDialogo>(MAT_DIALOG_DATA);
  readonly formulario = new FormGroup({
    outroLado: new FormControl<string | null>(null),
    conta: new FormControl<number | null>(null),
    descricao: new FormControl('', { nonNullable: true }),
  });
  readonly outroLado = toSignal(this.formulario.controls.outroLado.valueChanges, { initialValue: null });
  readonly estado = signal<ParesDoLancamento | null>(null);
  readonly falhaAoCarregar = signal(false);
  /** A mensagem do 404 ou do 422 ao carregar: tentar de novo não muda nada. */
  readonly detalheAoCarregar = signal<string | null>(null);
  readonly enviando = signal(false);
  readonly erroGeral = signal<string | null>(null);
  /** Depois do erro ao converter, os pares foram recarregados e há o que escolher. */
  readonly recarregada = signal(false);
  readonly erros = signal<Partial<Record<CampoDaConversao, string>>>({});
  readonly diaEMes = diaEMes;
  readonly nova = NOVA;
  /** O importado é uma saída (despesa): o outro lado é a entrada. */
  readonly saida = computed(() => (this.estado()?.lancamento.valor ?? 0) < 0);
  /** O lado novo vai para outra conta ativa (decisão 28). */
  readonly contasDaNova = computed(() => {
    const atual = this.estado();
    return atual === null ? [] : this.dados.contas.ativas.filter((conta) => conta.id !== atual.lancamento.contaId);
  });
  /** As que já existiam na data do importado (o saldo inicial até ela): só elas recebem o lado novo. */
  readonly contasQueServem = computed(() => this.contasDaNova().filter((conta) => !this.comecaDepois(conta)));
  readonly legenda = computed(() => this.saida() ? 'Entrou em' : 'Saiu de');
  readonly textoDaNova = computed(() => this.saida() ? 'Lançar a entrada em outra conta' : 'Lançar a saída em outra conta');
  readonly rotuloDoSelect = computed(() => this.saida() ? 'Conta da entrada' : 'Conta da saída');
  /** Sem par e sem conta que sirva: não há o que converter, e a tela só diz por quê. */
  readonly semSaida = computed(() => (this.estado()?.pares.length ?? 1) === 0 && this.contasQueServem().length === 0);
  readonly metaDoFixo = computed(() => {
    const lancamento = this.estado()?.lancamento;
    return lancamento === undefined ? ''
      : `${this.saida() ? 'Sai de' : 'Entra em'} ${lancamento.contaNome} em ${diaEMes(lancamento.data)} · importado`;
  });
  readonly metaDaNova = computed(() => {
    const atual = this.estado();
    if (atual === null) {
      return '';
    }
    if (this.contasDaNova().length === 0) {
      return 'Cadastre outra conta ativa para lançar o outro lado.';
    }
    if (this.contasQueServem().length === 0) {
      return `Nenhuma outra conta ativa existia em ${dataCurta(atual.lancamento.data)}: a conta do outro lado precisa ter o saldo `
        + 'inicial até essa data.';
    }
    return atual.pares.length > 0 ? 'Para conta sem extrato: carteira, investimento'
      : `Uma ${this.saida() ? 'receita' : 'despesa'} de ${valorSemSinal(atual.lancamento.valor)} em ${diaEMes(atual.lancamento.data)}, `
        + `ligada a esta ${this.saida() ? 'saída' : 'entrada'}`;
  });
  readonly textoSemPar = computed(() => {
    const atual = this.estado();
    return atual === null ? '' : `Nenhuma ${this.saida() ? 'entrada' : 'saída'} de ${valorSemSinal(atual.lancamento.valor)} em outra conta `
      + `entre ${diaEMes(atual.inicio)} e ${diaEMes(atual.fim)}.`;
  });

  constructor() {
    // O erro fica dentro do switchMap: a próxima carga ("Tentar de novo", ou depois de um erro ao converter) pede de novo.
    this.cargas.pipe(
      switchMap((depoisDoErro) => this.servico.pares(this.dados.lancamentoId).pipe(
        map((pares): CargaDosPares => ({ pares, depoisDoErro })),
        catchError((erro: unknown) => of<CargaDosPares>({ erro, depoisDoErro })),
      )),
      takeUntilDestroyed(),
    ).subscribe((carga) => this.mostrar(carga));
    for (const campo of ['outroLado', 'conta', 'descricao'] as const) {
      const controle: AbstractControl = this.formulario.controls[campo];
      controle.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
        this.erros.update(({ [campo]: removido, ...outros }) => {
          void removido;
          return outros;
        });
      });
    }
    this.carregar();
  }

  /** "Tentar de novo" e a carga inicial. */
  carregar(): void {
    this.recarregar(false);
  }

  metaDoPar(par: ParDoLancamento): string {
    const atual = this.estado();
    const quando = atual === null ? '' : `, ${distancia(atual.lancamento.data, par.data)}`;
    return `${par.contaNome} · ${diaEMes(par.data)}${quando} · ${par.origem === 'IMPORTADO' ? 'importado' : 'lançado à mão'}`;
  }

  valorDoPar(par: ParDoLancamento): string {
    return `par-${par.id}`;
  }

  /** A conta começou depois da data do importado: o lado novo não cabe nela (as datas ISO se comparam como texto). */
  comecaDepois(conta: Conta): boolean {
    const atual = this.estado();
    return atual !== null && conta.dataSaldoInicial > atual.lancamento.data;
  }

  /** "Carteira" ou, desabilitada, "Carteira (começa em 11/11/2026)". */
  rotuloDaConta(conta: Conta): string {
    return this.comecaDepois(conta) ? `${conta.nome} (começa em ${dataCurta(conta.dataSaldoInicial)})` : conta.nome;
  }

  erro(campo: CampoDaConversao): string | null {
    return this.erros()[campo] ?? null;
  }

  /** Fecha sem nada, como o Esc: quem abriu continua como estava. */
  cancelar(): void {
    this.referencia.close();
  }

  converter(): void {
    const atual = this.estado();
    // Sem saída não há o que converter: o envio do formulário (o Enter) não faz nada, nem mostra erro.
    if (this.enviando() || atual === null || this.semSaida()) {
      return;
    }
    this.erroGeral.set(null);
    this.recarregada.set(false);
    const valores = this.formulario.getRawValue();
    const locais: Partial<Record<CampoDaConversao, string>> = {};
    if (valores.outroLado === null) {
      locais.outroLado = 'Escolha o outro lado da transferência.';
    } else if (valores.outroLado === NOVA && valores.conta === null) {
      locais.conta = 'Escolha a conta.';
    }
    this.erros.set(locais);
    if (Object.keys(locais).length > 0) {
      return;
    }
    const parId = valores.outroLado === NOVA ? null : Number(valores.outroLado?.slice(4));
    const descricao = valores.descricao.trim();
    this.enviando.set(true);
    // Esc e clique fora esperam a resposta: fechar agora perderia a transferência criada.
    this.referencia.disableClose = true;
    this.servico.converter(this.dados.lancamentoId, { parId, contaId: parId === null ? valores.conta : null,
      descricao: descricao === '' ? null : descricao }).subscribe({
      next: (transferencia) => {
        this.snackBar.open('Transferência criada.', 'Fechar', { duration: 4000 });
        this.referencia.close(transferencia);
      },
      error: (erro: unknown) => {
        this.enviando.set(false);
        this.referencia.disableClose = false;
        this.tratarErro(erro);
      },
    });
  }

  private tratarErro(erro: unknown): void {
    const status = statusDoErro(erro);
    if (status === 400) {
      const campos = camposDoErro(erro);
      const conhecidos: Partial<Record<CampoDaConversao, string>> = {};
      if (campos['parId'] !== undefined) {
        conhecidos.outroLado = campos['parId'];
      }
      if (campos['contaId'] !== undefined) {
        conhecidos.conta = campos['contaId'];
      }
      if (campos['descricao'] !== undefined) {
        conhecidos.descricao = campos['descricao'];
      }
      this.erros.set(conhecidos);
      if (Object.keys(conhecidos).length === 0) {
        this.erroGeral.set('Confira os campos e tente de novo.');
      }
    } else if (status === 422 && propriedadeDoErro(erro, 'campo') === 'contaId') {
      // A conta do lado novo (inexistente, arquivada, começou depois da data ou o limite do mês): a escolha fica.
      this.erros.set({ conta: detalheDoErro(erro, FALHA) });
    } else if (status === 404 || status === 422) {
      // O par ou a conta pode ter mudado em outra aba: a mensagem fica, e a lista volta do servidor.
      this.erroGeral.set(detalheDoErro(erro, FALHA));
      this.recarregar(true);
    } else {
      this.erroGeral.set(FALHA);
    }
  }

  /** Os pares saem de cena antes do pedido: os botões de opção nunca mostram a lista velha. */
  private recarregar(depoisDoErro: boolean): void {
    this.estado.set(null);
    this.falhaAoCarregar.set(false);
    this.detalheAoCarregar.set(null);
    this.formulario.controls.outroLado.setValue(null);
    this.cargas.next(depoisDoErro);
  }

  private mostrar(carga: CargaDosPares): void {
    if ('erro' in carga) {
      if (carga.depoisDoErro) {
        this.erroGeral.set(null);
      }
      const status = statusDoErro(carga.erro);
      this.detalheAoCarregar.set(status === 404 || status === 422 ? detalheDoErro(carga.erro, '') || null : null);
      this.falhaAoCarregar.set(true);
      return;
    }
    const pares = carga.pares;
    this.estado.set(pares);
    if (carga.depoisDoErro) {
      // Nada vem escolhido depois de um erro: o par que sobrou pode não ser o que o usuário queria.
      this.recarregada.set(pares.pares.length > 0 || this.contasQueServem().length > 0);
      return;
    }
    const sugerido = pares.pares.find((par) => par.id === this.dados.parSugerido);
    const escolhido = sugerido ?? (pares.pares.length === 1 ? pares.pares[0] : null);
    if (escolhido !== null) {
      this.formulario.controls.outroLado.setValue(this.valorDoPar(escolhido));
    } else if (pares.pares.length === 0 && this.contasQueServem().length > 0) {
      this.formulario.controls.outroLado.setValue(NOVA);
    }
  }
}

/** Abre a conversão (tela cheia no celular); emite a transferência criada, ou undefined. */
export function abrirConversao(dialogo: MatDialog, dados: DadosDaConversaoDialogo): Observable<ItemDaLista | undefined> {
  return dialogo.open<ConversaoDialogo, DadosDaConversaoDialogo, ItemDaLista>(ConversaoDialogo, {
    data: dados,
    panelClass: ['flux-dialogo', 'flux-dialogo--cheio-no-celular'],
    width: '540px',
    maxWidth: 'calc(100vw - 32px)',
    maxHeight: 'calc(100vh - 32px)',
    autoFocus: 'first-heading',
  }).afterClosed();
}