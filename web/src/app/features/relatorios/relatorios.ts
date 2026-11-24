import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { map, Subscription } from 'rxjs';

import { AGORA } from '../../core/tempo/agora';
import { QUEBRA_DESKTOP } from '../../layout/quebras';
import { Alerta } from '../../shared/alerta/alerta';
import { CabecalhoPagina } from '../../shared/cabecalho-pagina/cabecalho-pagina';
import { Card } from '../../shared/card/card';
import { dataIsoNoFuso } from '../../shared/datas/data-curta';
import { EstadoVazio } from '../../shared/estado-vazio/estado-vazio';
import { GraficoColunas } from '../../shared/graficos/grafico-colunas';
import { GraficoLinha } from '../../shared/graficos/grafico-linha';
import { ColunaDoGrafico, PontoDoGrafico } from '../../shared/graficos/mes-do-grafico';
import { Icone } from '../../shared/icone/icone';
import { formatarMoeda } from '../../shared/moeda/moeda';
import { SaldoEmDestaque } from '../../shared/saldo-em-destaque/saldo-em-destaque';
import { Valor } from '../../shared/valor/valor';
import { nomeDoMes } from '../lancamentos/apresentacao';
import { mesVizinho } from '../lancamentos/filtros';
import {
  colunasDoGrafico,
  corDaComparacao,
  faixaDosMeses,
  legendaDaMedia,
  leituraDaComparacao,
  mesDaUrl,
  notaDaMedia,
  parametrosDoMes,
  pontosDoSaldo,
  quantidadeDeContas,
  rotuloDoSaldo,
  textoDaComparacao,
  textoDasDespesas,
  textoDasReceitas,
} from './apresentacao';
import { GastosPorCategoria } from './gastos-por-categoria/gastos-por-categoria';
import { RelatorioDoMes, RelatoriosService } from './relatorios.service';

@Component({
  selector: 'app-relatorios',
  imports: [
    MatButtonModule,
    RouterLink,
    Alerta,
    CabecalhoPagina,
    Card,
    EstadoVazio,
    Icone,
    SaldoEmDestaque,
    Valor,
    GastosPorCategoria,
    GraficoColunas,
    GraficoLinha,
  ],
  templateUrl: './relatorios.html',
})
export class Relatorios {
  private readonly rota = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly servico = inject(RelatoriosService);
  private readonly hoje = dataIsoNoFuso(inject(AGORA)());
  private pedido: Subscription | null = null;

  readonly relatorio = signal<RelatorioDoMes | null>(null);
  readonly carregando = signal(false);
  readonly falha = signal(false);
  readonly pedidoMes = signal<string | null>(null);
  readonly tabelaDeColunas = signal(false);
  readonly tabelaDeSaldo = signal(false);
  readonly desktop = toSignal(
    inject(BreakpointObserver)
      .observe(QUEBRA_DESKTOP)
      .pipe(map((estado) => estado.matches)),
    { initialValue: true },
  );
  readonly nomeDoMes = nomeDoMes;
  readonly formatarMoeda = formatarMoeda;
  readonly corDaComparacao = corDaComparacao;
  readonly textoDaComparacao = textoDaComparacao;
  readonly leituraDaComparacao = leituraDaComparacao;
  readonly textoDasReceitas = textoDasReceitas;
  readonly textoDasDespesas = textoDasDespesas;
  readonly rotuloDoSaldo = rotuloDoSaldo;
  readonly quantidadeDeContas = quantidadeDeContas;
  readonly notaDaMedia = notaDaMedia;
  readonly legendaDaMedia = legendaDaMedia;
  readonly faixaDosMeses = faixaDosMeses;
  readonly colunas = computed<readonly ColunaDoGrafico[]>(() => {
    return colunasDoGrafico(this.relatorio()?.meses ?? []);
  });
  readonly pontos = computed<readonly PontoDoGrafico[]>(() => {
    const relatorio = this.relatorio();
    return relatorio === null ? [] : pontosDoSaldo(relatorio.meses, relatorio.hoje);
  });
  readonly mesDoSeletor = computed(() => {
    if (this.carregando() || this.falha()) {
      return this.pedidoMes() ?? this.hoje.slice(0, 7);
    }
    return this.relatorio()?.mes ?? this.hoje.slice(0, 7);
  });
  readonly subtituloDosGraficos = computed(() => {
    return faixaDosMeses(this.relatorio()?.meses ?? [], !this.desktop());
  });
  readonly mesesComSaldo = computed(() => {
    return this.relatorio()?.meses.filter((mes) => mes.saldo !== null) ?? [];
  });

  constructor() {
    this.rota.queryParamMap.pipe(takeUntilDestroyed()).subscribe((parametros) => {
      const mes = mesDaUrl(parametros.get('mes'));
      this.pedidoMes.set(mes);
      this.carregar(mes);
    });
  }

  mudarMes(passo: -1 | 1): void {
    this.irPara(mesVizinho(this.mesDoSeletor(), passo));
  }

  irPara(mes: string): void {
    const atual = this.relatorio()?.mesAtual ?? this.hoje.slice(0, 7);
    void this.router.navigate([], {
      relativeTo: this.rota,
      queryParams: parametrosDoMes(mes, atual),
    });
  }

  tentarDeNovo(): void {
    this.carregar(this.pedidoMes());
  }

  alternarColunas(): void {
    this.tabelaDeColunas.update((valor) => !valor);
  }

  alternarSaldo(): void {
    this.tabelaDeSaldo.update((valor) => !valor);
  }

  parametros(mes: string): object {
    return parametrosDoMes(mes, this.relatorio()?.mesAtual ?? this.hoje.slice(0, 7));
  }

  private carregar(mes: string | null): void {
    this.pedido?.unsubscribe();
    this.carregando.set(true);
    this.falha.set(false);
    this.pedido = this.servico.doMes(mes).subscribe({
      next: (relatorio) => {
        this.relatorio.set(relatorio);
        this.carregando.set(false);
      },
      error: () => {
        this.falha.set(true);
        this.carregando.set(false);
      },
    });
  }
}
