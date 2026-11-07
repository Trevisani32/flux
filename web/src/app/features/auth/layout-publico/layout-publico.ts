import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { PainelMarca } from './painel-marca/painel-marca';

/** Moldura das telas públicas: painel da marca + lado do conteúdo + rodapé com os documentos. */
@Component({
  selector: 'app-layout-publico',
  imports: [RouterLink, PainelMarca],
  templateUrl: './layout-publico.html',
  styleUrl: './layout-publico.scss',
})
export class LayoutPublico {
  /** No celular, a faixa escura mostra só a marca. */
  readonly compacto = input(false);
}
