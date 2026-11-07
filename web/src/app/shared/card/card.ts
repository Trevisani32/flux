import { Component, input } from '@angular/core';

@Component({
  selector: 'app-card',
  templateUrl: './card.html',
  styleUrl: './card.scss',
  host: { class: 'flux-card' },
})
export class Card {
  readonly titulo = input<string>();
  readonly nivel = input<2 | 3>(2);
}
