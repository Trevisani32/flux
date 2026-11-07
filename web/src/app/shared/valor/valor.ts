import { Component, computed, input } from '@angular/core';

import { partesDaMoeda, SEM_VALOR, SINAL_MENOS } from '../moeda/moeda';

export type TipoValor = 'sinal' | 'saldo' | 'neutro';
type CorValor = 'positivo' | 'negativo' | 'neutro' | 'padrao';

interface Apresentacao {
  readonly visivel: string;
  readonly falado: string | null;
  readonly cor: CorValor;
}

@Component({
  selector: 'app-valor',
  template: `<span class="valor__texto" [attr.aria-hidden]="apresentacao().falado === null ? null : 'true'">{{ apresentacao().visivel }}</span>@if (apresentacao().falado; as falado) {<span class="flux-visualmente-oculto">{{ falado }}</span>}`,
  styles: `
    :host { font: var(--flux-fonte-valor); font-variant-numeric: tabular-nums; font-feature-settings: 'tnum'; white-space: nowrap; letter-spacing: -0.2px; }
    :host(.flux-valor--positivo) { color: var(--flux-positivo); }
    :host(.flux-valor--negativo) { color: var(--flux-negativo); }
    :host(.flux-valor--neutro) { color: var(--flux-texto-2); }
  `,
  host: {
    class: 'flux-valor flux-num',
    '[class.flux-valor--positivo]': "apresentacao().cor === 'positivo'",
    '[class.flux-valor--negativo]': "apresentacao().cor === 'negativo'",
    '[class.flux-valor--neutro]': "apresentacao().cor === 'neutro'",
  },
})
export class Valor {
  readonly valor = input.required<number | null | undefined>();
  readonly tipo = input<TipoValor>('sinal');

  readonly apresentacao = computed<Apresentacao>(() => {
    const partes = partesDaMoeda(this.valor());
    if (partes === null) {
      return { visivel: SEM_VALOR, falado: 'sem valor', cor: 'neutro' };
    }
    const { absoluto, negativo, zero } = partes;
    if (this.tipo() === 'neutro') {
      return { visivel: absoluto, falado: null, cor: 'neutro' };
    }
    if (negativo) {
      return { visivel: `${SINAL_MENOS}${absoluto}`, falado: `menos ${absoluto}`, cor: 'negativo' };
    }
    if (this.tipo() === 'saldo') {
      return { visivel: absoluto, falado: null, cor: 'padrao' };
    }
    if (zero) {
      return { visivel: absoluto, falado: null, cor: 'neutro' };
    }
    return { visivel: `+${absoluto}`, falado: `mais ${absoluto}`, cor: 'positivo' };
  });
}
