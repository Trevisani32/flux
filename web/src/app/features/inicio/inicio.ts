import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { AGORA } from '../../core/tempo/agora';
import { Alerta } from '../../shared/alerta/alerta';
import { CabecalhoPagina } from '../../shared/cabecalho-pagina/cabecalho-pagina';
import { Card } from '../../shared/card/card';
import { dataPorExtenso } from '../../shared/datas/data-por-extenso';
import { EstadoVazio } from '../../shared/estado-vazio/estado-vazio';
import { Icone } from '../../shared/icone/icone';
import { NomeIcone } from '../../shared/icone/icones';
import { SaldoEmDestaque } from '../../shared/saldo-em-destaque/saldo-em-destaque';
import { Valor } from '../../shared/valor/valor';
import { corDaConta, quantidadeDeContasAtivas, tipoDaConta } from '../contas/apresentacao';
import { ContasService, ListaDeContas } from '../contas/contas.service';

interface ItemEmBreve {
  readonly icone: NomeIcone;
  readonly titulo: string;
  readonly texto: string;
}

@Component({
  selector: 'app-inicio',
  imports: [MatButtonModule, RouterLink, Alerta, CabecalhoPagina, Card, EstadoVazio, Icone, SaldoEmDestaque, Valor],
  templateUrl: './inicio.html',
  styleUrl: './inicio.scss',
})
export class Inicio {
  private readonly auth = inject(AuthService);

  readonly tipoDaConta = tipoDaConta;
  readonly corDaConta = corDaConta;
  readonly quantidadeDeContasAtivas = quantidadeDeContasAtivas;
  readonly titulo = computed(() => `Olá, ${this.auth.usuario()?.nome ?? ''}`);
  readonly hoje = dataPorExtenso(inject(AGORA)());
  readonly contas = signal<ListaDeContas | null>(null);
  readonly falhaAoCarregar = signal(false);
  readonly temInvestimento = computed(() => this.contas()?.ativas.some((conta) => conta.tipo === 'INVESTIMENTO') ?? false);
  readonly emBreve: readonly ItemEmBreve[] = [
    { icone: 'lancamentos', titulo: 'Lançamentos e transferências', texto: 'Receitas, despesas e transferências, com o saldo de cada conta em dia.' },
    { icone: 'importar', titulo: 'Importação de extratos', texto: 'OFX e CSV do banco, com categorização automática.' },
    { icone: 'cartao', titulo: 'Cartões de crédito', texto: 'Faturas, compras parceladas e limite disponível.' },
  ];

  constructor() {
    inject(ContasService).listar().subscribe({
      next: (lista) => this.contas.set(lista),
      error: () => this.falhaAoCarregar.set(true),
    });
  }
}
