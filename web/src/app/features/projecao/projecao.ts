import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { map, Subscription } from 'rxjs';

import { AGORA } from '../../core/tempo/agora';
import { QUEBRA_DESKTOP } from '../../layout/quebras';
import { camposDoErro, statusDoErro } from '../auth/erros-http';
import { Alerta } from '../../shared/alerta/alerta';
import { CabecalhoPagina } from '../../shared/cabecalho-pagina/cabecalho-pagina';
import { Card } from '../../shared/card/card';
import { dataCurta } from '../../shared/datas/data-curta';
import { EstadoVazio } from '../../shared/estado-vazio/estado-vazio';
import { GraficoDegraus } from '../../shared/graficos/grafico-degraus';
import { Icone } from '../../shared/icone/icone';
import { formatarMoeda } from '../../shared/moeda/moeda';
import { Valor } from '../../shared/valor/valor';
import { corDaConta } from '../contas/apresentacao';
import { abrirPagamento } from '../cartoes/pagamento-dialogo/pagamento-dialogo';
import { dataDaOcorrencia } from '../recorrencias/frases';
import { FluxoDaOcorrencia } from '../recorrencias/fluxo-da-ocorrencia';
import {
  diasDoGrafico,
  menorDoGrafico,
  parametrosDaUrl,
  pedidoDaUrl,
} from './apresentacao';
import {
  linhaDaPrevistaNaFatura,
  maisDias,
  metaDaPendencia,
  naData,
  notaDoFim,
  notaDoMenor,
  notaDoSaldoHoje,
  periodoDoGrafico,
  rodapeDoPorConta,
  rotuloDoFim,
  subtituloDoCelular,
  textoDaFatura,
  textoDaTransferencia,
  textoDasFaturasFora,
  tituloDasFaturasFora,
  tituloDoNegativo,
  quandoNegativo,
} from './frases';
import { ItemDaProjecao, Projecao, ProjecaoService } from './projecao.service';

@Component({
  selector: 'app-projecao',
  imports: [
    MatButtonModule,
    RouterLink,
    Alerta,
    CabecalhoPagina,
    Card,
    EstadoVazio,
    GraficoDegraus,
    Icone,
    Valor,
  ],
  templateUrl: './projecao.html',
})
export class ProjecaoPagina {
  private readonly rota = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly servico = inject(ProjecaoService);
  readonly fluxoDaOcorrencia = inject(FluxoDaOcorrencia);
  private readonly dialogo = inject(MatDialog);
  private pedido: Subscription | null = null;

  readonly hoje = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Sao_Paulo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(inject(AGORA)());
  readonly projecao = signal<Projecao | null>(null);
  readonly carregando = signal(false);
  readonly erro = signal<string | null>(null);
  readonly erroDaData = signal<string | null>(null);
  readonly mostrarCampoDaData = signal(false);
  readonly tabela = signal(false);
  readonly todosOsDias = signal(false);
  /** O gráfico tem 270 px no desktop e 210 no celular (seção 3.4), como os de Relatórios. */
  readonly desktop = toSignal(
    inject(BreakpointObserver)
      .observe(QUEBRA_DESKTOP)
      .pipe(map((estado) => estado.matches)),
    { initialValue: true },
  );
  readonly pedidoAtual = signal<{ readonly ate: string | null; readonly contas: readonly number[] | null }>({
    ate: null,
    contas: null,
  });

  readonly corDaConta = corDaConta;
  readonly formatarMoeda = formatarMoeda;
  readonly dataCurta = dataCurta;
  readonly textoDaFatura = textoDaFatura;
  readonly textoDaTransferencia = textoDaTransferencia;
  readonly metaDaPendencia = metaDaPendencia;
  readonly linhaDaPrevistaNaFatura = linhaDaPrevistaNaFatura;
  readonly tituloDasFaturasFora = tituloDasFaturasFora;
  readonly textoDasFaturasFora = textoDasFaturasFora;
  readonly notaDoMenor = notaDoMenor;
  readonly notaDoFim = notaDoFim;
  readonly rotuloDoFim = rotuloDoFim;
  readonly notaDoSaldoHoje = notaDoSaldoHoje;
  readonly periodoDoGrafico = periodoDoGrafico;
  readonly subtituloDoCelular = subtituloDoCelular;
  readonly maisDias = maisDias;
  readonly rodapeDoPorConta = rodapeDoPorConta;
  readonly diasDoGrafico = computed(() => {
    const projecao = this.projecao();
    return projecao === null ? [] : diasDoGrafico(projecao);
  });
  readonly menorDoGrafico = computed(() => {
    const projecao = this.projecao();
    return projecao === null ? null : menorDoGrafico(projecao);
  });
  readonly temContasAtivas = computed(() => (this.projecao()?.contas.length ?? 0) > 0);
  readonly temEscolha = computed(() => (this.projecao()?.escolhidas.length ?? 0) > 0);
  readonly variasContas = computed(() => (this.projecao()?.escolhidas.length ?? 0) > 1);
  readonly diasVisiveis = computed(() => {
    const dias = this.projecao()?.dias ?? [];
    return this.todosOsDias() ? dias : dias.slice(0, 31);
  });
  readonly restoNoDesktop = computed(() => Math.max(0, (this.projecao()?.dias.length ?? 0) - 31));
  readonly restoNoCelular = computed(() => Math.max(0, (this.projecao()?.dias.length ?? 0) - 6));

  constructor() {
    this.rota.queryParamMap.pipe(takeUntilDestroyed()).subscribe((parametros) => {
      const pedido = pedidoDaUrl(parametros);
      this.pedidoAtual.set(pedido);
      this.todosOsDias.set(false);
      this.erroDaData.set(null);
      this.carregar(pedido.ate, pedido.contas);
    });
    this.fluxoDaOcorrencia.mudou$.pipe(takeUntilDestroyed()).subscribe(() => this.recarregar());
  }

  atalho(ate: string): void {
    const projecao = this.projecao();
    if (projecao !== null) {
      this.navegar(ate, this.pedidoAtual().contas, projecao);
    }
  }

  mudarData(valor: string): void {
    const projecao = this.projecao();
    if (projecao === null) {
      return;
    }
    if (valor === '') {
      this.erroDaData.set('Escolha uma data válida.');
    } else if (valor <= projecao.hoje) {
      this.erroDaData.set('Escolha uma data depois de hoje.');
    } else if (valor > projecao.periodos.dozeMeses) {
      this.erroDaData.set(`A projeção vai até ${dataCurta(projecao.periodos.dozeMeses)}.`);
    } else {
      this.erroDaData.set(null);
      this.navegar(valor, this.pedidoAtual().contas, projecao);
    }
  }

  alternarConta(id: number): void {
    const projecao = this.projecao();
    if (projecao === null) {
      return;
    }
    const selecionadas = this.pedidoAtual().contas ?? projecao.escolhidas;
    const contas = selecionadas.includes(id) ? selecionadas.filter((conta) => conta !== id) : [...selecionadas, id];
    this.navegar(this.pedidoAtual().ate, contas, projecao);
  }

  /** Volta às contas padrão e mantém o período (seção 3.5: "tira o `contas` da URL"). */
  voltarAoPadrao(): void {
    const projecao = this.projecao();
    if (projecao !== null) {
      this.navegar(this.pedidoAtual().ate, null, projecao);
    }
  }

  verSo(id: number): void {
    const projecao = this.projecao();
    if (projecao !== null) {
      this.navegar(this.pedidoAtual().ate, [id], projecao);
    }
  }

  tentarDeNovo(): void {
    const pedido = this.pedidoAtual();
    this.carregar(pedido.ate, pedido.contas);
  }

  voltarDoErro(): void {
    void this.router.navigate([], { relativeTo: this.rota, queryParams: {} });
  }

  abrirPagamento(item: ItemDaProjecao): void {
    abrirPagamento(this.dialogo, {
      cartaoId: item.cartaoId,
      mes: item.mes,
      pagamentoId: null,
      hoje: this.projecao()!.hoje,
    }).subscribe((resultado) => {
      if (resultado !== undefined) {
        this.recarregar();
      }
    });
  }

  textoDoItem(item: ItemDaProjecao): string {
    if (item.tipo === 'FATURA') {
      return textoDaFatura(item, this.projecao()!.hoje);
    }
    if (item.tipo === 'TRANSFERENCIA') {
      return textoDaTransferencia(item);
    }
    return item.descricao ?? '';
  }

  rotuloDoItem(item: ItemDaProjecao): string {
    return item.tipo === 'PREVISTO' ? 'Previsto' : item.tipo === 'FATURA' ? 'Fatura' : 'Futuro';
  }

  negativo(item: ItemDaProjecao): boolean {
    return item.valor < 0;
  }

  contaTemNegativo(id: number): boolean {
    return this.projecao()?.porConta.find((conta) => conta.contaId === id)?.trechosNegativos.length !== 0;
  }

  tituloDoNegativo(): string {
    const projecao = this.projecao()!;
    return tituloDoNegativo(
      projecao.porConta[0].nome,
      projecao.porConta[0].trechosNegativos[0],
      projecao.hoje,
      projecao.ate,
    );
  }

  tituloDoTotalNegativo(): string {
    const projecao = this.projecao()!;
    return tituloDoNegativo('O total', projecao.resumo.trechosNegativos[0], projecao.hoje, projecao.ate);
  }

  tituloDaContaNegativa(conta: Projecao['porConta'][number]): string {
    const projecao = this.projecao()!;
    return tituloDoNegativo(conta.nome, conta.trechosNegativos[0], projecao.hoje, projecao.ate);
  }

  complementoDoMenor(
    dia: string,
    trechos: Projecao['resumo']['trechosNegativos'],
    ate: string,
  ): string {
    const hoje = this.projecao()!.hoje;
    const outro = trechos.length > 1 ? ` Fica no negativo de novo ${quandoNegativo(trechos[1], hoje, ate)}.` : '';
    return `, ${naData(dia, hoje)}.${outro}`;
  }

  amanha(data: string): string {
    const [ano, mes, dia] = data.split('-').map(Number);
    return new Date(Date.UTC(ano, mes - 1, dia + 1)).toISOString().slice(0, 10);
  }

  diaDaOcorrencia(data: string): string {
    return dataDaOcorrencia(data, this.projecao()!.hoje);
  }

  private navegar(ate: string | null, contas: readonly number[] | null, projecao: Projecao): void {
    void this.router.navigate([], {
      relativeTo: this.rota,
      queryParams: parametrosDaUrl(ate, contas, projecao),
    });
  }

  private recarregar(): void {
    const pedido = this.pedidoAtual();
    this.carregar(pedido.ate, pedido.contas);
  }

  private carregar(ate: string | null, contas: readonly number[] | null): void {
    this.pedido?.unsubscribe();
    this.carregando.set(true);
    this.erro.set(null);
    this.pedido = this.servico.projetar(ate, contas).subscribe({
      next: (projecao) => {
        this.projecao.set(projecao);
        this.carregando.set(false);
      },
      error: (erro: unknown) => {
        this.projecao.set(null);
        this.erro.set(statusDoErro(erro) === 400 ? camposDoErro(erro)['ate'] ?? 'Escolha uma data válida.'
          : 'Não foi possível calcular a projeção.');
        this.carregando.set(false);
      },
    });
  }
}
