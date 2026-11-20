import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable } from 'rxjs';

import { Alerta } from '../../../shared/alerta/alerta';
import { dataCurta } from '../../../shared/datas/data-curta';
import { Icone } from '../../../shared/icone/icone';
import { Valor } from '../../../shared/valor/valor';
import { detalheDoErro, propriedadeDoErro, statusDoErro } from '../../auth/erros-http';
import { nomeDoMes } from '../../lancamentos/apresentacao';
import { diaEMes, ROTULO_DA_SITUACAO } from '../apresentacao';
import { FaturaParaPagamento, FaturasParaPagamento, Pagamento, PagamentosService } from '../pagamentos.service';

export interface DadosDaConversaoEmPagamento {
  /** A despesa importada que vira pagamento. */
  readonly lancamentoId: number;
}

const FALHA = 'Não foi possível converter. Tente novamente.';

/**
 * "Converter em pagamento de fatura" (Fatia 05b, decisão 37): a despesa importada, o cartão e a fatura que ela paga. Vêm
 * primeiro as faturas sugeridas (fechadas, com o pendente igual ao valor e o vencimento perto da data), como a API ordena.
 * Convertida, ela perde a categoria; data, valor, conta e a chave do extrato ficam. No celular, ocupa a tela.
 */
@Component({
  selector: 'app-conversao-em-pagamento-dialogo',
  imports: [MatButtonModule, MatDialogModule, ReactiveFormsModule, Alerta, Icone, Valor],
  templateUrl: './conversao-em-pagamento-dialogo.html',
  styleUrl: './conversao-em-pagamento-dialogo.scss',
})
export class ConversaoEmPagamentoDialogo {
  private readonly servico = inject(PagamentosService);
  private readonly referencia = inject<MatDialogRef<ConversaoEmPagamentoDialogo, Pagamento>>(MatDialogRef);
  private readonly snackBar = inject(MatSnackBar);

  readonly dados = inject<DadosDaConversaoEmPagamento>(MAT_DIALOG_DATA);
  readonly dataCurta = dataCurta;
  readonly diaEMes = diaEMes;
  readonly rotuloDaSituacao = ROTULO_DA_SITUACAO;
  readonly opcoes = signal<FaturasParaPagamento | null>(null);
  /**
   * A falha ao carregar as faturas. Correções do QA (05b): a recusa da API (o 404 e o 422 da regra, como a data futura)
   * não oferece "Tentar de novo", como a página da fatura; só a falha de rede e o 5xx oferecem.
   */
  readonly falhaAoCarregar = signal<{ readonly mensagem: string; readonly deNovo: boolean } | null>(null);
  readonly cartao = new FormControl<number | null>(null);
  readonly fatura = new FormControl<string | null>(null);
  private readonly cartaoEscolhido = toSignal(this.cartao.valueChanges, { initialValue: null });
  readonly enviando = signal(false);
  readonly erroGeral = signal<string | null>(null);
  readonly erroDoCartao = signal<string | null>(null);
  readonly erroDaFatura = signal<string | null>(null);
  /** As faturas do cartão escolhido, na ordem da API. */
  readonly faturas = computed<readonly FaturaParaPagamento[]>(() => {
    const id = this.cartaoEscolhido();
    return this.opcoes()?.cartoes.find((cartao) => cartao.id === id)?.faturas ?? [];
  });

  constructor() {
    this.carregar();
    // Trocar o cartão escolhe a primeira fatura dele (a sugerida, quando há).
    this.cartao.valueChanges.pipe(takeUntilDestroyed()).subscribe((id) => {
      this.erroDoCartao.set(null);
      const primeira = this.opcoes()?.cartoes.find((cartao) => cartao.id === id)?.faturas[0]?.mes ?? null;
      this.fatura.setValue(primeira);
    });
    this.fatura.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.erroDaFatura.set(null));
  }

  carregar(): void {
    this.falhaAoCarregar.set(null);
    this.servico.faturasParaPagamento(this.dados.lancamentoId).subscribe({
      next: (opcoes) => {
        this.opcoes.set(opcoes);
        const comSugerida = opcoes.cartoes.find((cartao) => cartao.faturas.some((fatura) => fatura.sugerida));
        this.cartao.setValue((comSugerida ?? opcoes.cartoes[0])?.id ?? null);
      },
      error: (erro: unknown) => this.falhaAoCarregar.set(statusDoErro(erro) === 404 || statusDoErro(erro) === 422
        ? { mensagem: detalheDoErro(erro, 'Lançamento não encontrado.'), deNovo: false }
        : { mensagem: 'Não foi possível carregar as faturas.', deNovo: true }),
    });
  }

  /** "Novembro de 2026". */
  nomeDoMes(mes: string): string {
    const nome = nomeDoMes(mes);
    return nome.charAt(0).toLocaleUpperCase('pt-BR') + nome.slice(1);
  }

  /** "Fechada · vence 10/11 · pendente igual ao valor". */
  metaDaFatura(fatura: FaturaParaPagamento): string {
    const partes = [this.rotuloDaSituacao[fatura.situacao], `vence ${diaEMes(fatura.vencimento)}`];
    if (fatura.sugerida) {
      partes.push('pendente igual ao valor');
    }
    return partes.join(' · ');
  }

  /** Fecha sem resultado, como o Esc. */
  cancelar(): void {
    this.referencia.close();
  }

  converter(): void {
    const cartao = this.cartao.value;
    const mes = this.fatura.value;
    if (this.enviando() || this.opcoes() === null) {
      return;
    }
    this.erroGeral.set(null);
    if (cartao === null) {
      this.erroDoCartao.set('Escolha o cartão.');
      return;
    }
    if (mes === null) {
      this.erroDaFatura.set('Escolha a fatura.');
      return;
    }
    this.enviando.set(true);
    this.referencia.disableClose = true;
    this.servico.converter(this.dados.lancamentoId, cartao, mes).subscribe({
      next: (pagamento) => {
        this.snackBar.open(`Convertido: agora é o pagamento da fatura de ${nomeDoMes(pagamento.faturaMes)}.`, 'Fechar', { duration: 5000 });
        this.referencia.close(pagamento);
      },
      error: (erro: unknown) => {
        const mensagem = detalheDoErro(erro, FALHA);
        const campo = propriedadeDoErro(erro, 'campo');
        if (statusDoErro(erro) === 422 && campo === 'mes') {
          this.erroDaFatura.set(mensagem);
        } else if (statusDoErro(erro) === 422 && campo === 'cartaoId') {
          this.erroDoCartao.set(mensagem);
        } else {
          this.erroGeral.set(statusDoErro(erro) === 422 || statusDoErro(erro) === 404 ? mensagem : FALHA);
        }
        this.enviando.set(false);
        this.referencia.disableClose = false;
      },
    });
  }
}

/** Abre a conversão em pagamento; o Observable emite o pagamento criado, ou nada se ela fechou sem converter. */
export function abrirConversaoEmPagamento(dialogo: MatDialog, dados: DadosDaConversaoEmPagamento): Observable<Pagamento | undefined> {
  return dialogo.open<ConversaoEmPagamentoDialogo, DadosDaConversaoEmPagamento, Pagamento>(ConversaoEmPagamentoDialogo, {
    data: dados,
    panelClass: ['flux-dialogo', 'flux-dialogo--cheio-no-celular'],
    width: '540px',
    maxWidth: 'calc(100vw - 32px)',
    maxHeight: 'calc(100vh - 32px)',
    autoFocus: 'first-heading',
  }).afterClosed();
}
