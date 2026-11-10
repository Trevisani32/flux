import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { Observable } from 'rxjs';

import { detalheDoErro, statusDoErro } from '../../features/auth/erros-http';
import { Alerta } from '../alerta/alerta';
import { Icone } from '../icone/icone';
import { NomeIcone } from '../icone/icones';
import { Valor } from '../valor/valor';

/** Um trecho de parágrafo: texto simples ou em negrito. */
export type TrechoDeTexto = string | { readonly forte: string };

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
  /** Mensagem quando a API falha sem um "detail" que sirva (rede, 500). */
  readonly falha: string;
  /** Chamada à API. O diálogo fecha com true quando ela termina bem. */
  readonly acao: () => Observable<unknown>;
}

/** Confirmação de arquivar ou excluir: chama a API e só fecha quando ela responde bem. */
@Component({
  selector: 'app-confirmacao-dialogo',
  imports: [MatButtonModule, MatDialogModule, Alerta, Icone, Valor],
  templateUrl: './confirmacao-dialogo.html',
  styleUrl: './confirmacao-dialogo.scss',
})
export class ConfirmacaoDialogo {
  private readonly referencia = inject<MatDialogRef<ConfirmacaoDialogo, boolean>>(MatDialogRef);

  readonly dados = inject<DadosDaConfirmacao>(MAT_DIALOG_DATA);
  readonly enviando = signal(false);
  readonly erro = signal<string | null>(null);

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
        this.erro.set(status === 404 || status === 422 ? detalheDoErro(erro, this.dados.falha) : this.dados.falha);
        this.enviando.set(false);
        this.referencia.disableClose = false;
      },
    });
  }

  /** Fecha sem resultado (undefined), como o Esc e o clique fora. */
  cancelar(): void {
    this.referencia.close();
  }

  forte(trecho: TrechoDeTexto): string | null {
    return typeof trecho === 'string' ? null : trecho.forte;
  }

  simples(trecho: TrechoDeTexto): string {
    return typeof trecho === 'string' ? trecho : trecho.forte;
  }
}

/** Abre a confirmação com o painel certo; o Observable emite true quando a ação terminou bem. */
export function abrirConfirmacao(dialogo: MatDialog, dados: DadosDaConfirmacao): Observable<boolean | undefined> {
  return dialogo.open<ConfirmacaoDialogo, DadosDaConfirmacao, boolean>(ConfirmacaoDialogo, {
    data: dados,
    panelClass: dados.perigo ? 'flux-dialogo-perigo' : 'flux-dialogo',
    width: '460px',
    maxWidth: 'calc(100vw - 32px)',
    maxHeight: 'calc(100vh - 32px)',
  }).afterClosed();
}
