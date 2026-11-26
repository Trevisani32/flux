import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { Observable } from 'rxjs';

import { dataLonga } from '../frases';
import { Icone } from '../../../shared/icone/icone';
import { Valor } from '../../../shared/valor/valor';
import { Ocorrencia } from '../recorrencias.service';

export interface DadosDasAcoesDoPrevisto {
  readonly ocorrencia: Ocorrencia;
  readonly hoje: string;
}

export type AcaoDoPrevisto = 'confirmar' | 'pular' | 'ligar' | 'editar' | undefined;

@Component({
  selector: 'app-acoes-do-previsto',
  imports: [MatButtonModule, MatDialogModule, Icone, Valor],
  templateUrl: './acoes-do-previsto.html',
  styleUrl: './acoes-do-previsto.scss',
})
export class AcoesDoPrevisto {
  private readonly referencia = inject<MatDialogRef<AcoesDoPrevisto, AcaoDoPrevisto>>(MatDialogRef);
  readonly dados = inject<DadosDasAcoesDoPrevisto>(MAT_DIALOG_DATA);
  readonly dataLonga = dataLonga;

  rotulo(): string {
    return this.dados.ocorrencia.atrasada ? 'Atrasada' : 'Previsto';
  }

  escolher(acao: Exclude<AcaoDoPrevisto, undefined>): void {
    this.referencia.close(acao);
  }
}

export function abrirAcoesDoPrevisto(dialogo: MatDialog, dados: DadosDasAcoesDoPrevisto): Observable<AcaoDoPrevisto> {
  return dialogo.open<AcoesDoPrevisto, DadosDasAcoesDoPrevisto, AcaoDoPrevisto>(AcoesDoPrevisto, {
    data: dados,
    panelClass: ['flux-dialogo', 'flux-dialogo--embaixo-no-celular'],
    width: '420px',
    maxWidth: 'calc(100vw - 24px)',
  }).afterClosed();
}
