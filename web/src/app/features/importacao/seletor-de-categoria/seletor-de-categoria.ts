import { Component, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { Observable } from 'rxjs';

import { Alerta } from '../../../shared/alerta/alerta';
import { dataCurta } from '../../../shared/datas/data-curta';
import { Valor } from '../../../shared/valor/valor';
import { GrupoDeCategorias } from '../../categorias/categorias.service';

/** Uma opção do seletor: a principal ou, recuada, a subcategoria. */
export interface OpcaoDoSeletor {
  readonly id: number;
  readonly nome: string;
  readonly sub: boolean;
}

export interface DadosDoSeletor {
  readonly descricao: string;
  readonly valor: number;
  readonly data: string;
  readonly receita: boolean;
  readonly opcoes: readonly OpcaoDoSeletor[];
  /** A categoria já escolhida na linha (nula = sem categoria). */
  readonly atual: number | null;
  /** Quantas outras linhas para importar têm a mesma descrição. */
  readonly iguais: number;
  /** Fatia 04b: "Sugerida pela 2ª regra: contém "uber"", quando a categoria atual veio da regra. */
  readonly regra: string | null;
}

export interface EscolhaDoSeletor {
  readonly categoriaId: number | null;
  readonly usarNasIguais: boolean;
}

/** As ativas da natureza, cada principal seguida das subcategorias dela (decisão 20). */
export function opcoesDoSeletor(grupo: GrupoDeCategorias): OpcaoDoSeletor[] {
  return grupo.ativas.flatMap((principal) => [
    { id: principal.id, nome: principal.nome, sub: false },
    ...principal.subcategorias.map((sub) => ({ id: sub.id, nome: sub.nome, sub: true })),
  ]);
}

/** O seletor de categoria da revisão: um diálogo só para a página toda (tela cheia no celular). */
@Component({
  selector: 'app-seletor-de-categoria',
  imports: [MatButtonModule, MatDialogModule, ReactiveFormsModule, Alerta, Valor],
  templateUrl: './seletor-de-categoria.html',
  styleUrl: './seletor-de-categoria.scss',
})
export class SeletorDeCategoria {
  private readonly referencia = inject<MatDialogRef<SeletorDeCategoria, EscolhaDoSeletor>>(MatDialogRef);

  readonly dados = inject<DadosDoSeletor>(MAT_DIALOG_DATA);
  readonly dia = this.dados.data === '' ? '' : dataCurta(this.dados.data).slice(0, 5);
  readonly formulario = new FormGroup({
    categoria: new FormControl<number | null>(this.dados.atual),
    usarNasIguais: new FormControl(true, { nonNullable: true }),
  });

  textoDasIguais(): string {
    return this.dados.iguais === 1 ? 'Usar também na outra linha com a mesma descrição'
      : `Usar também nas outras ${this.dados.iguais} linhas com a mesma descrição`;
  }

  aplicar(): void {
    const valores = this.formulario.getRawValue();
    this.referencia.close({ categoriaId: valores.categoria, usarNasIguais: this.dados.iguais > 0 && valores.usarNasIguais });
  }

  cancelar(): void {
    this.referencia.close();
  }
}

export function abrirSeletorDeCategoria(dialogo: MatDialog, dados: DadosDoSeletor): Observable<EscolhaDoSeletor | undefined> {
  return dialogo.open<SeletorDeCategoria, DadosDoSeletor, EscolhaDoSeletor>(SeletorDeCategoria, {
    data: dados,
    panelClass: ['flux-dialogo', 'flux-dialogo--cheio-no-celular'],
    width: '440px',
    maxWidth: 'calc(100vw - 32px)',
    maxHeight: 'calc(100vh - 32px)',
    autoFocus: 'first-heading',
  }).afterClosed();
}
