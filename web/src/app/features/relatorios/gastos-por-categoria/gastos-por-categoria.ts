import { Component, computed, input, linkedSignal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { BarraHorizontal } from '../../../shared/graficos/barra-horizontal';
import { Icone } from '../../../shared/icone/icone';
import { formatarMoeda } from '../../../shared/moeda/moeda';
import { nomeDoMesSemAno } from '../../lancamentos/apresentacao';
import {
  corDaComparacao,
  legendaDaMedia,
  leituraDaComparacao,
  linkDaCategoria,
  nadaGasto,
  notaDaMedia,
  quantidadeDeDespesas,
  textoDaComparacao,
  textoDaMediaDaLinha,
} from '../apresentacao';
import { CategoriaDoRelatorio, RelatorioDoMes } from '../relatorios.service';

@Component({
  selector: 'app-gastos-por-categoria',
  imports: [RouterLink, Icone, BarraHorizontal],
  templateUrl: './gastos-por-categoria.html',
})
export class GastosPorCategoria {
  readonly relatorio = input.required<RelatorioDoMes>();
  readonly abertas = linkedSignal<RelatorioDoMes, ReadonlySet<number>>({
    source: this.relatorio,
    computation: () => new Set(),
  });
  readonly formatarMoeda = formatarMoeda;
  readonly textoDaComparacao = textoDaComparacao;
  readonly textoDaMediaDaLinha = textoDaMediaDaLinha;
  readonly leituraDaComparacao = leituraDaComparacao;
  readonly corDaComparacao = corDaComparacao;
  readonly nomeDoMesSemAno = nomeDoMesSemAno;
  readonly nota = computed(() => {
    return notaDaMedia(this.relatorio().mes, this.relatorio().mesesDaMedia, false);
  });
  readonly legenda = computed(() => {
    return legendaDaMedia(this.relatorio().mesesDaMedia.length, false);
  });
  readonly maximo = computed(() => {
    return Math.max(
      0,
      ...this.relatorio().categorias.map((categoria) =>
        Math.max(categoria.valor, categoria.media ?? 0),
      ),
    );
  });
  readonly nada = computed(() => nadaGasto(this.relatorio().mes));
  readonly totalDasDespesas = computed(() => formatarMoeda(this.relatorio().despesas.valor));
  readonly quantidadeDasDespesas = computed(() => quantidadeDeDespesas(this.relatorio().despesas.quantidade));

  link(categoria: CategoriaDoRelatorio): object | null {
    return linkDaCategoria(this.relatorio().mes, categoria);
  }

  aberta(categoria: CategoriaDoRelatorio): boolean {
    return categoria.id !== null && this.abertas().has(categoria.id);
  }

  alternar(categoria: CategoriaDoRelatorio): void {
    if (categoria.id === null) {
      return;
    }
    const id = categoria.id;
    this.abertas.update((atuais) => {
      const proximas = new Set(atuais);
      if (proximas.has(id)) {
        proximas.delete(id);
      } else {
        proximas.add(id);
      }
      return proximas;
    });
  }
}
