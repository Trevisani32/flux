import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { AGORA } from '../../core/tempo/agora';
import { Alerta } from '../../shared/alerta/alerta';
import { CabecalhoPagina } from '../../shared/cabecalho-pagina/cabecalho-pagina';
import { Card } from '../../shared/card/card';
import { dataIsoNoFuso } from '../../shared/datas/data-curta';
import { dataPorExtenso } from '../../shared/datas/data-por-extenso';
import { EstadoVazio } from '../../shared/estado-vazio/estado-vazio';
import { Icone } from '../../shared/icone/icone';
import { NomeIcone } from '../../shared/icone/icones';
import { SaldoEmDestaque } from '../../shared/saldo-em-destaque/saldo-em-destaque';
import { Valor } from '../../shared/valor/valor';
import { CategoriasService, ListaDeCategorias } from '../categorias/categorias.service';
import { corDaConta, quantidadeDeContasAtivas, tipoDaConta } from '../contas/apresentacao';
import { ContasService, ListaDeContas } from '../contas/contas.service';
import { nomeDoMesSemAno } from '../lancamentos/apresentacao';
import { mesDe } from '../lancamentos/filtros';
import { abrirDialogoDeLancamento, contaPadrao } from '../lancamentos/lancamento-dialogo/lancamento-dialogo';
import { ItemDaLista, LancamentosService, ListaDoMes } from '../lancamentos/lancamentos.service';
import { LinhaDoLancamento } from '../lancamentos/linha-do-lancamento/linha-do-lancamento';

interface ItemEmBreve {
  readonly icone: NomeIcone;
  readonly titulo: string;
  readonly texto: string;
}

/** Saldo total, contas, o mês e os últimos lançamentos (decisão 32). */
@Component({
  selector: 'app-inicio',
  imports: [MatButtonModule, RouterLink, Alerta, CabecalhoPagina, Card, EstadoVazio, Icone, LinhaDoLancamento, SaldoEmDestaque, Valor],
  templateUrl: './inicio.html',
  styleUrl: './inicio.scss',
})
export class Inicio {
  private readonly auth = inject(AuthService);
  private readonly contasService = inject(ContasService);
  private readonly lancamentosService = inject(LancamentosService);
  private readonly dialogo = inject(MatDialog);
  private readonly agora = inject(AGORA)();

  readonly tipoDaConta = tipoDaConta;
  readonly corDaConta = corDaConta;
  readonly quantidadeDeContasAtivas = quantidadeDeContasAtivas;
  readonly nomeDoMesSemAno = nomeDoMesSemAno;
  readonly titulo = computed(() => `Olá, ${this.auth.usuario()?.nome ?? ''}`);
  readonly hoje = dataPorExtenso(this.agora);
  readonly hojeIso = dataIsoNoFuso(this.agora);
  readonly contas = signal<ListaDeContas | null>(null);
  readonly mes = signal<ListaDoMes | null>(null);
  readonly ultimos = signal<readonly ItemDaLista[] | null>(null);
  readonly falhaAoCarregar = signal(false);
  private readonly categorias = signal<ListaDeCategorias | null>(null);
  readonly temInvestimento = computed(() => this.contas()?.ativas.some((conta) => conta.tipo === 'INVESTIMENTO') ?? false);
  readonly temContaAtiva = computed(() => (this.contas()?.ativas.length ?? 0) > 0);
  /** O cartão do mês só aparece com algum lançamento no mês. */
  readonly mesComLancamentos = computed(() => {
    const mes = this.mes();
    return mes !== null && mes.quantidadeLancamentos + mes.quantidadeTransferencias > 0 ? mes : null;
  });
  readonly emBreve: readonly ItemEmBreve[] = [
    { icone: 'importar', titulo: 'Importação de extratos', texto: 'OFX e CSV do banco, com categorização automática.' },
    { icone: 'cartao', titulo: 'Cartões de crédito', texto: 'Faturas, compras parceladas e limite disponível.' },
  ];

  constructor() {
    this.carregar();
    inject(CategoriasService).listar().subscribe({
      next: (categorias) => this.categorias.set(categorias),
      error: () => this.categorias.set(null),
    });
  }

  /** Novo (item null) ou Editar, com o mesmo diálogo da tela de Lançamentos. */
  abrir(item: ItemDaLista | null): void {
    const contas = this.contas();
    const categorias = this.categorias();
    if (contas === null || categorias === null) {
      return;
    }
    abrirDialogoDeLancamento(this.dialogo, {
      item,
      hoje: this.hojeIso,
      contas,
      categorias,
      contaPadrao: contaPadrao(contas.ativas, null, this.lancamentosService.ultimaConta),
    }).subscribe((resultado) => {
      if (resultado !== undefined) {
        this.carregar();
      }
    });
  }

  private carregar(): void {
    this.contasService.listar().subscribe({
      next: (lista) => this.contas.set(lista),
      error: () => this.falhaAoCarregar.set(true),
    });
    // Sem os lançamentos, o Início continua com as contas: os dois cartões só somem.
    this.lancamentosService.listar({ mes: mesDe(this.hojeIso), conta: null, categoria: null, tipo: null, busca: '' }).subscribe({
      next: (mes) => this.mes.set(mes),
      error: () => this.mes.set(null),
    });
    this.lancamentosService.ultimos().subscribe({
      next: (ultimos) => this.ultimos.set(ultimos),
      error: () => this.ultimos.set(null),
    });
  }
}
