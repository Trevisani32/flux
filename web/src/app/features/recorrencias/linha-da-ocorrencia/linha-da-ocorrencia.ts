import { Component, computed, inject, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

import { corDaConta } from '../../../features/contas/apresentacao';
import { textoDaCategoria } from '../../cartoes/apresentacao';
import { Icone } from '../../../shared/icone/icone';
import { Valor } from '../../../shared/valor/valor';
import { quandoAConfirmar } from '../frases';
import { FluxoDaOcorrencia } from '../fluxo-da-ocorrencia';
import { Ocorrencia } from '../recorrencias.service';

/** A mesma ocorrência prevista no card, na página e na lista de lançamentos. */
@Component({
  selector: 'app-linha-da-ocorrencia',
  imports: [MatButtonModule, Icone, Valor],
  templateUrl: './linha-da-ocorrencia.html',
  styleUrl: './linha-da-ocorrencia.scss',
})
export class LinhaDaOcorrencia {
  private readonly fluxo = inject(FluxoDaOcorrencia);

  readonly ocorrencia = input.required<Ocorrencia>();
  readonly hoje = input.required<string>();
  readonly modo = input<'confirmar' | 'lista'>('confirmar');
  readonly contaFiltrada = input(false);
  readonly quandoAConfirmar = quandoAConfirmar;
  readonly textoDaCategoria = textoDaCategoria;
  readonly corDaConta = corDaConta;
  readonly local = computed(() => this.ocorrencia().cartaoNome ?? this.ocorrencia().contaNome ?? '');

  confirmar(): void {
    this.fluxo.confirmar(this.ocorrencia(), this.hoje());
  }

  pular(): void {
    this.fluxo.pular(this.ocorrencia());
  }

  acoes(): void {
    this.fluxo.acoes(this.ocorrencia(), this.hoje());
  }
}
