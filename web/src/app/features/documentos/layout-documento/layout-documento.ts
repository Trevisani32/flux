import { Component, inject, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';
import { dataDaVersao } from '../../../shared/datas/data-hora';
import { Icone } from '../../../shared/icone/icone';
import { Marca } from '../../../shared/marca/marca';
import { VERSAO_DOS_TERMOS } from '../versao-dos-termos';

export interface SecaoDoDocumento {
  readonly id: string;
  readonly rotulo: string;
}

export interface OutroDocumento {
  readonly rota: string;
  readonly rotulo: string;
}

/** Página pública de documento: barra com a marca, índice (desktop) e texto projetado. */
@Component({
  selector: 'app-layout-documento',
  imports: [MatButtonModule, RouterLink, Icone, Marca],
  templateUrl: './layout-documento.html',
  styleUrl: './layout-documento.scss',
})
export class LayoutDocumento {
  readonly titulo = input.required<string>();
  readonly leitura = input.required<string>();
  readonly rotuloDoIndice = input.required<string>();
  readonly secoes = input.required<readonly SecaoDoDocumento[]>();
  readonly outro = input.required<OutroDocumento>();

  readonly autenticado = inject(AuthService).autenticado;
  readonly versao = VERSAO_DOS_TERMOS;
  readonly versaoPorExtenso = dataDaVersao(VERSAO_DOS_TERMOS);
}
