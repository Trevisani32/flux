import { Component, computed, inject } from '@angular/core';

import { AuthService } from '../../core/auth/auth.service';
import { AGORA } from '../../core/tempo/agora';
import { Card } from '../../shared/card/card';
import { CabecalhoPagina } from '../../shared/cabecalho-pagina/cabecalho-pagina';
import { dataPorExtenso } from '../../shared/datas/data-por-extenso';
import { EstadoVazio } from '../../shared/estado-vazio/estado-vazio';
import { Icone } from '../../shared/icone/icone';
import { NomeIcone } from '../../shared/icone/icones';

interface ItemEmBreve {
  readonly icone: NomeIcone;
  readonly titulo: string;
  readonly texto: string;
}

@Component({
  selector: 'app-inicio',
  imports: [CabecalhoPagina, EstadoVazio, Card, Icone],
  templateUrl: './inicio.html',
  styleUrl: './inicio.scss',
})
export class Inicio {
  private readonly auth = inject(AuthService);
  readonly titulo = computed(() => `Olá, ${this.auth.usuario()?.nome ?? ''}`);
  readonly hoje = dataPorExtenso(inject(AGORA)());
  readonly emBreve: readonly ItemEmBreve[] = [
    { icone: 'lancamentos', titulo: 'Contas e lançamentos', texto: 'Saldo de cada conta, receitas, despesas e transferências.' },
    { icone: 'importar', titulo: 'Importação de extratos', texto: 'OFX e CSV do banco, com categorização automática.' },
    { icone: 'cartao', titulo: 'Cartões de crédito', texto: 'Faturas, compras parceladas e limite disponível.' },
  ];
}
