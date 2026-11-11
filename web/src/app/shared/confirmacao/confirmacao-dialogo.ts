import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { Observable } from 'rxjs';

import { codigoDoErro, detalheDoErro, propriedadeDoErro, statusDoErro } from '../../features/auth/erros-http';
import { Alerta } from '../alerta/alerta';
import { Icone } from '../icone/icone';
import { NomeIcone } from '../icone/icones';
import { Valor } from '../valor/valor';

/** Um trecho de parágrafo: texto simples ou em negrito. */
export type TrechoDeTexto = string | { readonly forte: string };

/**
 * O diálogo troca para o bloqueio quando a API responde 422 com este código (decisão 33):
 * "Esta conta não pode ser excluída", com o uso que veio no campo "uso" do erro.
 */
export interface BloqueioDaConfirmacao {
  /** Código do 422 (ex.: "CONTA_EM_USO"). */
  readonly codigo: string;
  readonly titulo: string;
  /** O parágrafo, montado com o uso ("34 lançamentos e 3 transferências"). */
  readonly paragrafo: (uso: string) => readonly TrechoDeTexto[];
  /** Botão que fecha com "alternativa" (ex.: "Arquivar conta"); sem ele, só "Fechar". */
  readonly alternativa?: string;
}

export interface DadosDaConfirmacao {
  readonly titulo: string;
  readonly icone: NomeIcone;
  /** Selo e botão vermelhos (excluir). */
  readonly perigo: boolean;
  /** Alerta dourado com um saldo ("O saldo desta conta é R$ 3.902,05"). */
  readonly alerta?: { readonly titulo: string; readonly valor: number; readonly texto: string };
  readonly paragrafos: readonly (readonly TrechoDeTexto[])[];
  /** Texto do botão que confirma. */
  readonly confirmar: string;
  /** Texto do botão que fecha sem confirmar; sem ele, "Cancelar" ("Fechar" num aviso que só leva a outra tela). */
  readonly cancelar?: string;
  /** Mensagem quando a API falha sem um "detail" que sirva (rede, 500). */
  readonly falha: string;
  /** Chamada à API. O diálogo fecha com true quando ela termina bem. */
  readonly acao: () => Observable<unknown>;
  readonly bloqueio?: BloqueioDaConfirmacao;
}

/** true: a ação terminou bem; "alternativa": o botão do bloqueio; undefined: fechou sem fazer nada. */
export type ResultadoDaConfirmacao = boolean | 'alternativa' | undefined;

/** Confirmação de arquivar ou excluir: chama a API e só fecha quando ela responde bem. */
@Component({
  selector: 'app-confirmacao-dialogo',
  imports: [MatButtonModule, MatDialogModule, Alerta, Icone, Valor],
  templateUrl: './confirmacao-dialogo.html',
  styleUrl: './confirmacao-dialogo.scss',
})
export class ConfirmacaoDialogo {
  private readonly referencia = inject<MatDialogRef<ConfirmacaoDialogo, ResultadoDaConfirmacao>>(MatDialogRef);

  readonly dados = inject<DadosDaConfirmacao>(MAT_DIALOG_DATA);
  readonly enviando = signal(false);
  readonly erro = signal<string | null>(null);
  /** O parágrafo do bloqueio, quando a API recusou com o código dele. */
  readonly bloqueado = signal<readonly TrechoDeTexto[] | null>(null);

  confirmar(): void {
    if (this.enviando()) {
      return;
    }
    this.enviando.set(true);
    // Esc e clique fora esperam a resposta: fechar agora perderia o recarregamento da lista.
    this.referencia.disableClose = true;
    this.erro.set(null);
    this.dados.acao().subscribe({
      next: () => this.referencia.close(true),
      error: (erro: unknown) => {
        const status = statusDoErro(erro);
        const bloqueio = this.dados.bloqueio;
        const uso = propriedadeDoErro(erro, 'uso');
        if (status === 422 && bloqueio !== undefined && codigoDoErro(erro) === bloqueio.codigo && uso !== null) {
          this.bloqueado.set(bloqueio.paragrafo(uso));
        } else {
          this.erro.set(status === 404 || status === 422 ? detalheDoErro(erro, this.dados.falha) : this.dados.falha);
        }
        this.enviando.set(false);
        this.referencia.disableClose = false;
      },
    });
  }

  /** Fecha sem resultado (undefined), como o Esc e o clique fora. */
  cancelar(): void {
    this.referencia.close();
  }

  /** O botão do bloqueio ("Arquivar conta"): quem abriu decide o que fazer. */
  escolherAlternativa(): void {
    this.referencia.close('alternativa');
  }

  forte(trecho: TrechoDeTexto): string | null {
    return typeof trecho === 'string' ? null : trecho.forte;
  }

  simples(trecho: TrechoDeTexto): string {
    return typeof trecho === 'string' ? trecho : trecho.forte;
  }
}

/** Abre a confirmação com o painel certo; o Observable emite o resultado ao fechar. */
export function abrirConfirmacao(dialogo: MatDialog, dados: DadosDaConfirmacao): Observable<ResultadoDaConfirmacao> {
  return dialogo.open<ConfirmacaoDialogo, DadosDaConfirmacao, ResultadoDaConfirmacao>(ConfirmacaoDialogo, {
    data: dados,
    panelClass: dados.perigo ? 'flux-dialogo-perigo' : 'flux-dialogo',
    width: '460px',
    maxWidth: 'calc(100vw - 32px)',
    maxHeight: 'calc(100vh - 32px)',
  }).afterClosed();
}
