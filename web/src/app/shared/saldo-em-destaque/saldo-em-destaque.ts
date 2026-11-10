import { Component, input } from '@angular/core';

import { Valor } from '../valor/valor';

/**
 * Cartão dourado com um saldo em destaque (Contas e Início). O valor passa pelo
 * `app-valor` (ADR 0010), com os centavos menores; sobre o ouro, o negativo usa
 * `--flux-negativo-sobre-ouro` (`_superficies.scss`). O rodapé é projetado.
 */
@Component({
  selector: 'app-saldo-em-destaque',
  imports: [Valor],
  template: `
    <svg class="flux-cartao-destaque__ondas" viewBox="0 0 300 180" preserveAspectRatio="none" aria-hidden="true"><path d="M0 60 C 60 10, 120 110, 180 60 S 260 10, 300 50" /><path d="M0 95 C 60 45, 120 145, 180 95 S 260 45, 300 85" /><path d="M0 130 C 60 80, 120 180, 180 130 S 260 80, 300 120" /></svg>
    <div class="flux-cartao-destaque__topo"><span class="flux-cartao-destaque__rotulo">{{ rotulo() }}</span><span class="flux-cartao-destaque__chip" aria-hidden="true"></span></div>
    <p class="flux-cartao-destaque__valor flux-num"><app-valor tipo="saldo" [valor]="valor()" [centavosMenores]="true" /></p>
    <div class="flux-cartao-destaque__rodape"><ng-content /></div>
  `,
  styles: `
    .flux-cartao-destaque__valor { position: relative; }
  `,
  host: { class: 'flux-cartao-destaque' },
})
export class SaldoEmDestaque {
  readonly rotulo = input.required<string>();
  readonly valor = input.required<number>();
}
