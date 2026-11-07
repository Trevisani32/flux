import { Component, input } from '@angular/core';

import { Icone } from '../../../../shared/icone/icone';
import { Marca } from '../../../../shared/marca/marca';

@Component({
  selector: 'app-painel-marca',
  imports: [Marca, Icone],
  templateUrl: './painel-marca.html',
  styleUrl: './painel-marca.scss',
})
export class PainelMarca {
  /** No celular, mostra só a marca (sem o slogan). Não muda nada no desktop. */
  readonly compacto = input(false);
  readonly barras = [45, 62, 38, 70, 52, 86];
}
