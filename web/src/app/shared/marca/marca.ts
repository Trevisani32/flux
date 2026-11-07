import { Component } from '@angular/core';

@Component({
  selector: 'app-marca',
  template: `<span class="marca__selo" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" focusable="false"><path d="M4.7 9.5c2.7-2.7 4.6 2.7 7.3 0s4.6 2.7 7.3 0" /><path d="M4.7 14.8c2.7-2.7 4.6 2.7 7.3 0s4.6 2.7 7.3 0" opacity="0.65" /></svg></span><span class="marca__nome">Flux</span>`,
  styleUrl: './marca.scss',
})
export class Marca {}
