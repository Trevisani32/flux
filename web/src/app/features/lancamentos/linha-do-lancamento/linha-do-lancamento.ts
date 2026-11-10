import { Component, computed, input, output } from '@angular/core';

import { dataCurta } from '../../../shared/datas/data-curta';
import { Icone } from '../../../shared/icone/icone';
import { NomeIcone } from '../../../shared/icone/icones';
import { Valor } from '../../../shared/valor/valor';
import { metaDoItem } from '../apresentacao';
import { ItemDaLista } from '../lancamentos.service';

/**
 * Uma linha de lançamento: um botão que abre o Editar (decisão 3). Usada na lista do mês e
 * em "Últimos lançamentos" do Início. O selo vem do tipo (decisão 35); a transferência vai
 * sem sinal e em cinza (`app-valor tipo="neutro"`).
 */
@Component({
  selector: 'app-linha-do-lancamento',
  imports: [Icone, Valor],
  templateUrl: './linha-do-lancamento.html',
  styleUrl: './linha-do-lancamento.scss',
  host: { '[class.linha-do-lancamento--compacta]': 'compacta()' },
})
export class LinhaDoLancamento {
  readonly item = input.required<ItemDaLista>();
  /** A lista já é de uma conta: a meta não repete o nome dela. */
  readonly contaFiltrada = input(false);
  /** No Início: a data entra na meta ("hoje" ou "07/11"); recebe o "hoje" ISO. */
  readonly hojeNaMeta = input<string | null>(null);
  /** Dentro de um card (Início): sem a margem lateral e com o selo menor. */
  readonly compacta = input(false);
  readonly abrir = output<ItemDaLista>();

  readonly icone = computed<NomeIcone>(() => {
    const tipo = this.item().tipo;
    return tipo === 'RECEITA' ? 'entrada' : tipo === 'DESPESA' ? 'saida' : 'lancamentos';
  });
  readonly semCategoria = computed(() => this.item().tipo !== 'TRANSFERENCIA' && this.item().categoriaId === null);
  readonly meta = computed(() => this.comData(metaDoItem(this.item(), false, this.contaFiltrada())));
  readonly metaCurta = computed(() => this.comData(metaDoItem(this.item(), true, this.contaFiltrada())));

  private comData(meta: string): string {
    const hoje = this.hojeNaMeta();
    if (hoje === null) {
      return meta;
    }
    const data = this.item().data === hoje ? 'hoje' : dataCurta(this.item().data).slice(0, 5);
    return meta === '' ? data : `${meta} · ${data}`;
  }
}
