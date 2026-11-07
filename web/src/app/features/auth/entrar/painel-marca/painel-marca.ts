import { Component } from '@angular/core';

import { Icone } from '../../../../shared/icone/icone';
import { Marca } from '../../../../shared/marca/marca';

@Component({
  selector: 'app-painel-marca',
  imports: [Marca, Icone],
  templateUrl: './painel-marca.html',
  styleUrl: './painel-marca.scss',
})
export class PainelMarca {
  readonly barras = [45, 62, 38, 70, 52, 86];
}
