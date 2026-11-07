import { Component, input } from '@angular/core';

import { Icone } from '../icone/icone';
import { NomeIcone } from '../icone/icones';

@Component({
  selector: 'app-estado-vazio',
  imports: [Icone],
  templateUrl: './estado-vazio.html',
  styleUrl: './estado-vazio.scss',
  host: { class: 'flux-card flux-estado-vazio' },
})
export class EstadoVazio {
  readonly icone = input.required<NomeIcone>();
  readonly titulo = input.required<string>();
  readonly texto = input<string>();
  readonly nivel = input<2 | 3>(2);
}
