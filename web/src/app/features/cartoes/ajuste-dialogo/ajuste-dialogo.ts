import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable } from 'rxjs';

import { Alerta } from '../../../shared/alerta/alerta';
import { Icone } from '../../../shared/icone/icone';
import { detalheDoErro, propriedadeDoErro, statusDoErro } from '../../auth/erros-http';
import { mesVizinho } from '../../lancamentos/filtros';
import { diaEMes, mesSemAno, somarDias } from '../apresentacao';
import { CartoesService, Fatura, ResultadoDoAjuste } from '../cartoes.service';

export interface DadosDoAjuste {
  readonly fatura: Fatura;
}

const FALHA = 'Não foi possível ajustar o fechamento. Tente novamente.';

/**
 * "Fechamento ajustado.", "Fechamento ajustado. 1 compra mudou de fatura." ou "… 2 compras e 1 estorno mudaram de
 * fatura.": as compras e os estornos contados à parte.
 */
export function avisoDoAjuste(compras: number, estornos: number): string {
  const partes: string[] = [];
  if (compras > 0) {
    partes.push(compras === 1 ? '1 compra' : `${compras} compras`);
  }
  if (estornos > 0) {
    partes.push(estornos === 1 ? '1 estorno' : `${estornos} estornos`);
  }
  if (partes.length === 0) {
    return 'Fechamento ajustado.';
  }
  return `Fechamento ajustado. ${partes.join(' e ')} ${compras + estornos === 1 ? 'mudou' : 'mudaram'} de fatura.`;
}

/**
 * "Ajustar o fechamento" de uma fatura (decisão 12 da 05a): a data nova fica entre o dia seguinte ao fechamento da
 * anterior e a véspera do vencimento (ou do fechamento da seguinte). O aviso diz o trecho que muda de fatura, só com
 * as datas. Fecha com o resultado do ajuste.
 */
@Component({
  selector: 'app-ajuste-dialogo',
  imports: [MatButtonModule, MatDialogModule, ReactiveFormsModule, Alerta, Icone],
  templateUrl: './ajuste-dialogo.html',
  styleUrl: './ajuste-dialogo.scss',
})
export class AjusteDialogo {
  private readonly servico = inject(CartoesService);
  private readonly referencia = inject<MatDialogRef<AjusteDialogo, ResultadoDoAjuste>>(MatDialogRef);
  private readonly snackBar = inject(MatSnackBar);

  readonly fatura = inject<DadosDoAjuste>(MAT_DIALOG_DATA).fatura;
  readonly diaEMes = diaEMes;
  readonly mesDaFatura = mesSemAno(this.fatura.mes);
  readonly mesSeguinte = mesSemAno(mesVizinho(this.fatura.mes, 1));
  readonly formulario = new FormGroup({ data: new FormControl(this.fatura.fechamento, { nonNullable: true }) });
  private readonly escolhida = toSignal(this.formulario.controls.data.valueChanges, { initialValue: this.fatura.fechamento });
  readonly enviando = signal(false);
  readonly erroDaData = signal<string | null>(null);
  readonly erroGeral = signal<string | null>(null);
  /** "De 04/11 a 09/12: depois do fechamento de novembro e antes do vencimento, 10/12." */
  readonly dica = `De ${diaEMes(this.fatura.ajusteDe)} a ${diaEMes(this.fatura.ajusteAte)}: depois do fechamento de `
    + `${mesSemAno(mesVizinho(this.fatura.mes, -1))} e antes ${this.fatura.ajusteAteOVencimento
      ? `do vencimento, ${diaEMes(this.fatura.vencimento)}` : `do fechamento de ${this.mesSeguinte}, ${diaEMes(somarDias(this.fatura.ajusteAte, 1))}`}.`;
  /** O trecho que muda de fatura com a data escolhida; nada quando ela é a de agora ou está fora dos limites. */
  readonly aviso = computed(() => {
    const nova = this.escolhida();
    const atual = this.fatura.fechamento;
    if (nova === '' || nova === atual || nova < this.fatura.ajusteDe || nova > this.fatura.ajusteAte) {
      return null;
    }
    const de = nova > atual ? somarDias(atual, 1) : somarDias(nova, 1);
    const ate = nova > atual ? nova : atual;
    const trecho = de === ate ? diaEMes(de) : `${diaEMes(de)} a ${diaEMes(ate)}`;
    const comeco = `A fatura de ${this.mesSeguinte} passa a começar em ${diaEMes(somarDias(nova, 1))}.`;
    return nova > atual
      ? { titulo: `Compras de ${trecho} passam de ${this.mesSeguinte} para esta fatura.`, texto: `As parcelas delas andam junto, um mês antes. ${comeco}` }
      : { titulo: `Compras de ${trecho} passam desta fatura para ${this.mesSeguinte}.`, texto: `As parcelas delas andam junto, um mês depois. ${comeco}` };
  });

  constructor() {
    this.formulario.controls.data.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.erroDaData.set(null));
  }

  cancelar(): void {
    this.referencia.close();
  }

  ajustar(): void {
    if (this.enviando()) {
      return;
    }
    const data = this.formulario.controls.data.value;
    this.erroGeral.set(null);
    if (data === '') {
      this.erroDaData.set('Informe a data do fechamento.');
      return;
    }
    if (data < this.fatura.ajusteDe || data > this.fatura.ajusteAte) {
      this.erroDaData.set(this.dica);
      return;
    }
    this.enviando.set(true);
    this.referencia.disableClose = true;
    this.servico.ajustarFechamento(this.fatura.cartaoId, this.fatura.mes, data).subscribe({
      next: (resultado) => {
        this.snackBar.open(avisoDoAjuste(resultado.comprasMovidas, resultado.estornosMovidos), 'Fechar', { duration: 5000 });
        this.referencia.close(resultado);
      },
      error: (erro: unknown) => {
        const status = statusDoErro(erro);
        if (status === 422 && propriedadeDoErro(erro, 'campo') === 'data') {
          this.erroDaData.set(detalheDoErro(erro, this.dica));
        } else {
          this.erroGeral.set(status === 422 || status === 404 || status === 400 ? detalheDoErro(erro, FALHA) : FALHA);
        }
        this.enviando.set(false);
        this.referencia.disableClose = false;
      },
    });
  }
}

/** Abre o ajuste; o Observable emite o resultado, ou undefined se fechou sem ajustar. */
export function abrirAjuste(dialogo: MatDialog, fatura: Fatura): Observable<ResultadoDoAjuste | undefined> {
  return dialogo.open<AjusteDialogo, DadosDoAjuste, ResultadoDoAjuste>(AjusteDialogo, {
    data: { fatura },
    panelClass: ['flux-dialogo', 'flux-dialogo--cheio-no-celular'],
    width: '480px',
    maxWidth: 'calc(100vw - 32px)',
    maxHeight: 'calc(100vh - 32px)',
    autoFocus: '#ajuste-data',
  }).afterClosed();
}
