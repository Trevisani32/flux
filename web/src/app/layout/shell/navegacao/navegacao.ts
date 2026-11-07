import { Component, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { Icone } from '../../../shared/icone/icone';
import { Marca } from '../../../shared/marca/marca';

@Component({
  selector: 'app-navegacao',
  imports: [RouterLink, RouterLinkActive, Marca, Icone],
  templateUrl: './navegacao.html',
  styleUrl: './navegacao.scss',
})
export class Navegacao {
  readonly navegou = output<void>();
}
