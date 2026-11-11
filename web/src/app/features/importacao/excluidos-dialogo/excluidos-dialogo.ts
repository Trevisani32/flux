import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable } from 'rxjs';

import { Alerta } from '../../../shared/alerta/alerta';
import { dataCurta, dataCurtaDoInstante } from '../../../shared/datas/data-curta';
import { Icone } from '../../../shared/icone/icone';
import { Valor } from '../../../shared/valor/valor';
import { detalheDoErro } from '../../auth/erros-http';
import { LancamentosService } from '../../lancamentos/lancamentos.service';
import { Excluido, Importacao, ImportacaoService } from '../importacao.service';

/**
 * Os importados que o usuário excluiu (ignorados) de uma importação, com "Restaurar" (decisão
 * 31). "Fechar" devolve true quando restaurou algum. A página não usa esse resultado: ela recarrega
 * o histórico em qualquer fechamento, porque o Esc e o clique fora fecham sem ele.
 */
@Component({
  selector: 'app-excluidos-dialogo',
  imports: [MatButtonModule, MatDialogModule, Alerta, Icone, Valor],
  templateUrl: './excluidos-dialogo.html',
  styleUrl: './excluidos-dialogo.scss',
})
export class ExcluidosDialogo {
  private readonly referencia = inject<MatDialogRef<ExcluidosDialogo, boolean>>(MatDialogRef);
  private readonly lancamentos = inject(LancamentosService);
  private readonly snackBar = inject(MatSnackBar);

  readonly importacao = inject<Importacao>(MAT_DIALOG_DATA);
  readonly excluidos = signal<readonly Excluido[] | null>(null);
  readonly falha = signal<string | null>(null);
  readonly restaurando = signal<number | null>(null);
  readonly dataCurta = dataCurta;
  readonly dataCurtaDoInstante = dataCurtaDoInstante;
  private restaurouAlgum = false;

  constructor() {
    inject(ImportacaoService).excluidos(this.importacao.id).subscribe({
      next: (lista) => this.excluidos.set(lista),
      error: () => this.falha.set('Não foi possível carregar os excluídos.'),
    });
  }

  restaurar(excluido: Excluido): void {
    if (this.restaurando() !== null) {
      return;
    }
    this.restaurando.set(excluido.id);
    this.falha.set(null);
    this.lancamentos.restaurarLancamento(excluido.id, null).subscribe({
      next: () => {
        this.restaurouAlgum = true;
        this.restaurando.set(null);
        this.excluidos.update((lista) => (lista ?? []).filter((item) => item.id !== excluido.id));
        this.snackBar.open('Lançamento restaurado.', 'Fechar', { duration: 4000 });
      },
      error: (erro: unknown) => {
        this.restaurando.set(null);
        this.falha.set(detalheDoErro(erro, 'Não foi possível restaurar. Tente novamente.'));
      },
    });
  }

  fechar(): void {
    this.referencia.close(this.restaurouAlgum);
  }
}

export function abrirExcluidos(dialogo: MatDialog, importacao: Importacao): Observable<boolean | undefined> {
  return dialogo.open<ExcluidosDialogo, Importacao, boolean>(ExcluidosDialogo, {
    data: importacao,
    panelClass: 'flux-dialogo',
    width: '480px',
    maxWidth: 'calc(100vw - 32px)',
    maxHeight: 'calc(100vh - 32px)',
    autoFocus: 'first-heading',
  }).afterClosed();
}
