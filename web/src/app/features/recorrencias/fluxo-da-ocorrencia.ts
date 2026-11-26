import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, forkJoin } from 'rxjs';

import { diaEMes, mesSemAno } from '../cartoes/apresentacao';
import { CartoesService } from '../cartoes/cartoes.service';
import { CategoriasService } from '../categorias/categorias.service';
import { ContasService } from '../contas/contas.service';
import { abrirAcoesDoPrevisto } from './acoes-do-previsto/acoes-do-previsto';
import { abrirConfirmarOcorrencia } from './confirmar-dialogo/confirmar-dialogo';
import { Confirmacao, Ocorrencia, RecorrenciasService } from './recorrencias.service';
import { abrirRecorrencia } from './recorrencia-dialogo/recorrencia-dialogo';

/** Centraliza as ações de uma ocorrência para as três páginas que a mostram. */
@Injectable({ providedIn: 'root' })
export class FluxoDaOcorrencia {
  private readonly dialogo = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly servico = inject(RecorrenciasService);
  private readonly contas = inject(ContasService);
  private readonly cartoes = inject(CartoesService);
  private readonly categorias = inject(CategoriasService);
  private readonly mudou = new Subject<void>();

  readonly mudou$ = this.mudou.asObservable();

  notificarMudanca(): void {
    this.mudou.next();
  }

  confirmar(ocorrencia: Ocorrencia, hoje: string): void {
    abrirConfirmarOcorrencia(this.dialogo, { ocorrencia, hoje }).subscribe((confirmacao) => {
      if (confirmacao === undefined) {
        return;
      }
      if (confirmacao === 'pulada') {
        this.avisarPulo(ocorrencia);
        return;
      }
      if (confirmacao.ligada === true) {
        this.avisarLigacao(confirmacao);
        return;
      }
      const aviso = confirmacao.compraId === null
        ? `${ocorrencia.descricao} confirmada: lançamento de ${diaEMes(confirmacao.data)} criado.`
        : `${ocorrencia.descricao} confirmada: compra lançada na fatura de ${mesSemAno(confirmacao.faturaMes!)}`
          + ` do ${confirmacao.cartaoNome}.`;
      this.snackBar.open(aviso, 'Fechar', { duration: 5000 });
      this.mudou.next();
    });
  }

  jaLancei(ocorrencia: Ocorrencia, hoje: string): void {
    abrirConfirmarOcorrencia(this.dialogo, { ocorrencia, hoje, jaLancei: true }).subscribe((confirmacao) => {
      if (confirmacao === undefined || confirmacao === 'pulada') {
        return;
      }
      this.avisarLigacao(confirmacao);
    });
  }

  pular(ocorrencia: Ocorrencia): void {
    this.servico.pular(ocorrencia.recorrenciaId, ocorrencia.periodo).subscribe({
      next: () => this.avisarPulo(ocorrencia),
      error: () => this.snackBar.open('Não foi possível pular a ocorrência. Tente novamente.', 'Fechar', { duration: 5000 }),
    });
  }

  acoes(ocorrencia: Ocorrencia, hoje: string): void {
    abrirAcoesDoPrevisto(this.dialogo, { ocorrencia, hoje }).subscribe((acao) => {
      if (acao === 'confirmar') {
        this.confirmar(ocorrencia, hoje);
      } else if (acao === 'pular') {
        this.pular(ocorrencia);
      } else if (acao === 'ligar') {
        this.jaLancei(ocorrencia, hoje);
      } else if (acao === 'editar') {
        this.editar(ocorrencia.recorrenciaId, hoje);
      }
    });
  }

  private editar(id: number, hoje: string): void {
    forkJoin({
      recorrencia: this.servico.buscar(id),
      contas: this.contas.listar(),
      cartoes: this.cartoes.listar(),
      categorias: this.categorias.listar(),
    }).subscribe({
      next: ({ recorrencia, contas, cartoes, categorias }) => {
        abrirRecorrencia(this.dialogo, { recorrencia, hoje, contas, cartoes, categorias }).subscribe((salva) => {
          if (salva !== undefined) {
            this.snackBar.open('Recorrência salva.', 'Fechar', { duration: 5000 });
            this.mudou.next();
          }
        });
      },
      error: () => this.snackBar.open('Não foi possível abrir a recorrência. Tente novamente.', 'Fechar', { duration: 5000 }),
    });
  }

  private avisarLigacao(confirmacao: Confirmacao): void {
    const alvo = confirmacao.compraId === null ? 'ao lançamento' : 'à compra';
    this.snackBar.open(`Ligada ${alvo} de ${diaEMes(confirmacao.data)}.`, 'Fechar', { duration: 5000 });
    this.mudou.next();
  }

  private avisarPulo(ocorrencia: Ocorrencia): void {
    this.snackBar.open(`Ocorrência de ${diaEMes(ocorrencia.data)} pulada.`, 'Desfazer', { duration: 5000 }).onAction().subscribe(() => {
      this.servico.voltarAPrever(ocorrencia.recorrenciaId, ocorrencia.periodo).subscribe({ next: () => this.mudou.next() });
    });
    this.mudou.next();
  }
}
