import { Component, computed, input } from '@angular/core';

import { Icone } from '../icone/icone';
import { NomeIcone } from '../icone/icones';

export type TipoAlerta = 'info' | 'sucesso' | 'erro' | 'atencao';

/**
 * Alerta com ícone, título e texto opcional (projetado). Erro usa role="alert";
 * informação, sucesso e atenção usam role="status", a menos que `papel` diga outra coisa.
 */
@Component({
  selector: 'app-alerta',
  imports: [Icone],
  template: `<div [class]="'flux-alerta flux-alerta--' + tipo()" [attr.role]="papelEfetivo()"><app-icone [nome]="icone()" /><div class="flux-alerta__corpo"><strong class="flux-alerta__titulo">{{ titulo() }}</strong><ng-content /></div></div>`,
})
export class Alerta {
  readonly tipo = input.required<TipoAlerta>();
  readonly icone = input.required<NomeIcone>();
  readonly titulo = input.required<string>();
  readonly papel = input<'alert' | 'status' | null>(null);
  readonly papelEfetivo = computed(() => this.papel() ?? (this.tipo() === 'erro' ? 'alert' : 'status'));
}
