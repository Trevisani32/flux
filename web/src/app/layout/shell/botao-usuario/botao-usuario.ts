import { Component, computed, input } from '@angular/core';
import { MatMenuModule, MatMenuPanel } from '@angular/material/menu';

import { Icone } from '../../../shared/icone/icone';
import { inicialDoNome } from './inicial';

@Component({
  selector: 'app-botao-usuario',
  imports: [MatMenuModule, Icone],
  templateUrl: './botao-usuario.html',
  styleUrl: './botao-usuario.scss',
})
export class BotaoUsuario {
  readonly nome = input.required<string>();
  readonly email = input('');
  readonly compacto = input(false);
  readonly menu = input.required<MatMenuPanel>();
  readonly inicial = computed(() => inicialDoNome(this.nome()));
}
