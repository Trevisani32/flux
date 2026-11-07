import { InjectionToken } from '@angular/core';

/** Relógio da interface. Os testes trocam por uma data fixa. */
export const AGORA = new InjectionToken<() => Date>('AGORA', {
  providedIn: 'root',
  factory: () => () => new Date(),
});
