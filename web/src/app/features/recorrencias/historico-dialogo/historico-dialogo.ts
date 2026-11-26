import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { Alerta } from '../../../shared/alerta/alerta';
import { Valor } from '../../../shared/valor/valor';
import { dataCurta } from '../../../shared/datas/data-curta';
import { FluxoDaOcorrencia } from '../fluxo-da-ocorrencia';
import { rotuloDoPeriodo } from '../frases';
import { Historico, ItemDoHistorico, RecorrenciasService } from '../recorrencias.service';

export interface DadosDoHistorico {
  readonly recorrenciaId: number;
  readonly hoje: string;
}

@Component({
  selector: 'app-historico-dialogo',
  imports: [MatButtonModule, MatDialogModule, RouterLink, Alerta, Valor],
  templateUrl: './historico-dialogo.html',
  styleUrl: './historico-dialogo.scss',
})
export class HistoricoDialogo {
  private readonly servico = inject(RecorrenciasService);
  private readonly fluxo = inject(FluxoDaOcorrencia);
  private readonly referencia = inject<MatDialogRef<HistoricoDialogo, void>>(MatDialogRef);
  private readonly snackBar = inject(MatSnackBar);
  readonly dados = inject<DadosDoHistorico>(MAT_DIALOG_DATA);
  readonly historico = signal<Historico | null>(null);
  readonly falha = signal(false);
  readonly rotuloDoPeriodo = rotuloDoPeriodo;
  readonly dataCurta = dataCurta;

  constructor() {
    this.carregar();
  }

  voltarAPrever(periodo: string): void {
    this.servico.voltarAPrever(this.dados.recorrenciaId, periodo).subscribe({
      next: () => {
        this.snackBar.open('Voltou a ser prevista.', 'Fechar', { duration: 5000 });
        this.fluxo.notificarMudanca();
        this.carregar();
      },
    });
  }

  fechar(): void {
    this.referencia.close();
  }

  tituloDoItem(historico: Historico, item: ItemDoHistorico): string {
    const periodo = rotuloDoPeriodo(historico.frequencia, item.periodo, this.dados.hoje);
    const dataPrevista = `${item.dataPrevista.slice(8, 10)}/${item.dataPrevista.slice(5, 7)}`;
    return `${periodo} · ${dataPrevista}`;
  }

  private carregar(): void {
    this.falha.set(false);
    this.servico.historico(this.dados.recorrenciaId).subscribe({
      next: (historico) => this.historico.set(historico),
      error: () => this.falha.set(true),
    });
  }
}

export function abrirHistorico(dialogo: MatDialog, recorrenciaId: number, hoje: string): Observable<void | undefined> {
  return dialogo.open<HistoricoDialogo, DadosDoHistorico, void>(HistoricoDialogo, {
    data: { recorrenciaId, hoje },
    panelClass: 'flux-dialogo',
    width: '480px',
    maxWidth: 'calc(100vw - 32px)',
  }).afterClosed();
}
