import { Component, computed, input } from '@angular/core';

import { ICONES, NomeIcone } from './icones';

@Component({
  selector: 'app-icone',
  template: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" [attr.stroke-width]="traco()" stroke-linecap="round" stroke-linejoin="round" focusable="false">@for (caminho of caminhos(); track $index) {<path [attr.d]="caminho" />}</svg>`,
  styles: `
    :host { display: inline-flex; flex: none; }
    svg { width: 100%; height: 100%; }
  `,
  host: {
    class: 'flux-icone',
    'aria-hidden': 'true',
    '[style.width.px]': 'tamanho()',
    '[style.height.px]': 'tamanho()',
  },
})
export class Icone {
  readonly nome = input.required<NomeIcone>();
  readonly tamanho = input(20);
  readonly traco = input(1.75);
  readonly caminhos = computed(() => ICONES[this.nome()]);
}
