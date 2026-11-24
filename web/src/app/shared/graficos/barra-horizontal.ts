import { Component, computed, input } from '@angular/core';

import { naEscala } from './escalas';

@Component({
  selector: 'app-barra-horizontal',
  template: `
    <span class="barra__preenche" [style.width.%]="valorNaEscala()"></span>
    @if (media() !== null && media()! > 0) {
      <span class="barra__media" [style.left.%]="mediaNaEscala()"></span>
    }
  `,
  styles: `
    :host {
      position: relative;
      display: block;
    }
  `,
  host: {
    '[class]': "'grafico-barra grafico-barra--' + variante()",
    'aria-hidden': 'true',
  },
})
export class BarraHorizontal {
  readonly valor = input.required<number>();
  readonly media = input.required<number | null>();
  readonly maximo = input.required<number>();
  readonly variante = input.required<'principal' | 'sub' | 'sem'>();
  readonly valorNaEscala = computed(() => naEscala(this.valor(), this.maximo()));
  readonly mediaNaEscala = computed(() => naEscala(this.media() ?? 0, this.maximo()));
}
