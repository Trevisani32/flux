import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';

import { AGORA } from '../../../core/tempo/agora';
import { Alerta } from '../../../shared/alerta/alerta';
import { CabecalhoPagina } from '../../../shared/cabecalho-pagina/cabecalho-pagina';
import { dataIsoNoFuso } from '../../../shared/datas/data-curta';
import { EstadoVazio } from '../../../shared/estado-vazio/estado-vazio';
import { Icone } from '../../../shared/icone/icone';
import { Valor } from '../../../shared/valor/valor';
import { camposDoErro, detalheDoErro, statusDoErro } from '../../auth/erros-http';
import { CategoriasService, ListaDeCategorias } from '../../categorias/categorias.service';
import { nomeDoMes } from '../../lancamentos/apresentacao';
import { mesVizinho } from '../../lancamentos/filtros';
import { abrirAjuste } from '../ajuste-dialogo/ajuste-dialogo';
import {
  comAno, diaEMes, faixaDeMeses, metaDoItemDaFatura, mesSemAno, quandoFecha, ROTULO_DA_SITUACAO, rotuloDaParcela, textoDaCategoria,
  textoDoTotal,
} from '../apresentacao';
import { CartoesService, Fatura, ItemDaFatura } from '../cartoes.service';
import { abrirCompra } from '../compra-dialogo/compra-dialogo';
import { TipoDaCompra } from '../compras.service';

/** O mês da URL: "2026-12". Fora disso, a página nem pede a fatura. */
const MES_VALIDO = /^\d{4}-(0[1-9]|1[0-2])$/;

/**
 * A fatura de um mês (decisões 8 a 13 da 05a), em /cartoes/:id/faturas/:mes: as setas andam de mês em mês, até onde a
 * API diz; a linha do tempo, os totais e os lançamentos, a compra mais recente primeiro. Os números vêm da API.
 */
@Component({
  selector: 'app-fatura',
  imports: [MatButtonModule, MatMenuModule, RouterLink, Alerta, CabecalhoPagina, EstadoVazio, Icone, Valor],
  templateUrl: './fatura.html',
  styleUrl: './fatura.scss',
})
export class PaginaDaFatura {
  private readonly rota = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly servico = inject(CartoesService);
  private readonly dialogo = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private pedido: Subscription | null = null;

  readonly hoje = dataIsoNoFuso(inject(AGORA)());
  readonly diaEMes = diaEMes;
  readonly textoDoTotal = textoDoTotal;
  readonly metaDoItem = metaDoItemDaFatura;
  readonly rotuloDaParcela = rotuloDaParcela;
  readonly textoDaCategoria = textoDaCategoria;
  readonly rotuloDaSituacao = ROTULO_DA_SITUACAO;
  /** "novembro", para o nome das setas ("Fatura de novembro"). */
  readonly rotuloDaSeta = mesSemAno;
  /** "dezembro de 2026". */
  readonly nomeDoMesMinusculo = nomeDoMes;
  readonly cartaoId = signal(0);
  readonly mes = signal('');
  readonly fatura = signal<Fatura | null>(null);
  readonly falha = signal<{ readonly mensagem: string; readonly deNovo: boolean } | null>(null);
  readonly categorias = signal<ListaDeCategorias | null>(null);
  readonly titulo = computed(() => MES_VALIDO.test(this.mes()) ? `Fatura de ${nomeDoMes(this.mes())}` : 'Fatura');
  readonly anterior = computed(() => mesVizinho(this.mes(), -1));
  readonly seguinte = computed(() => mesVizinho(this.mes(), 1));
  readonly podeLancar = computed(() => {
    const fatura = this.fatura();
    return fatura !== null && !fatura.cartaoArquivado && this.categorias() !== null;
  });
  /** "Fecha em 21 dias", "Fechou em 03/11 · venceu em 10/11" ou "Começa em 04/12". */
  readonly notaDaSituacao = computed(() => {
    const fatura = this.fatura();
    if (fatura === null) {
      return '';
    }
    if (fatura.situacao === 'ABERTA') {
      return quandoFecha(this.hoje, fatura.fechamento);
    }
    if (fatura.situacao === 'FUTURA') {
      return `Começa em ${diaEMes(fatura.inicio)}`;
    }
    return `Fechou em ${diaEMes(fatura.fechamento)} · ${fatura.vencimento < this.hoje ? 'venceu' : 'vence'} em ${diaEMes(fatura.vencimento)}`;
  });
  readonly notaDasCompras = computed(() => {
    const quantas = this.fatura()?.quantidadeCompras ?? 0;
    return quantas === 1 ? '1 compra ou parcela' : `${quantas} compras e parcelas`;
  });
  readonly notaDosEstornos = computed(() => {
    const quantos = this.fatura()?.quantidadeEstornos ?? 0;
    return quantos === 1 ? '1 estorno' : `${quantos} estornos`;
  });
  readonly notaDoTotal = computed(() => {
    const fatura = this.fatura();
    if (fatura === null) {
      return '';
    }
    return fatura.situacao === 'FECHADA' ? `fechou em ${diaEMes(fatura.fechamento)}` : `até agora; fecha em ${diaEMes(fatura.fechamento)}`;
  });
  /**
   * "parcelas de janeiro a setembro", "parcelas de janeiro" ou "nada nas próximas faturas"; com crédito (decisão 11),
   * "nas faturas de janeiro". Com o ano quando a fatura não é do ano de hoje ou a faixa cruza anos.
   */
  readonly notaDeDepois = computed(() => {
    const fatura = this.fatura();
    const ate = fatura?.depoisAte ?? null;
    if (fatura === null || ate === null) {
      return 'nada nas próximas faturas';
    }
    const de = this.seguinte();
    const meses = faixaDeMeses(de, ate, comAno(this.hoje, fatura.mes) || de.slice(0, 4) !== ate.slice(0, 4));
    return fatura.depois < 0 ? `nas faturas de ${meses}` : `parcelas de ${meses}`;
  });
  /** O vazio diz as datas; "pelas datas do cartão" só na fatura que ainda não existe (decisão 9). */
  readonly textoDoVazio = computed(() => {
    const fatura = this.fatura();
    if (fatura === null) {
      return '';
    }
    const datas = `Fecha em ${diaEMes(fatura.fechamento)} e vence em ${diaEMes(fatura.vencimento)}`;
    return fatura.gravada ? `${datas}.` : `${datas}, pelas datas do cartão.`;
  });

  constructor() {
    this.rota.paramMap.pipe(takeUntilDestroyed()).subscribe((parametros) => {
      this.cartaoId.set(Number(parametros.get('id')));
      this.mes.set(parametros.get('mes') ?? '');
      this.carregar();
    });
    inject(CategoriasService).listar().subscribe({
      next: (categorias) => this.categorias.set(categorias),
      error: () => this.categorias.set(null),
    });
  }

  /** "Dezembro de 2026". */
  nomeDoMesComMaiuscula(mes: string): string {
    const nome = nomeDoMes(mes);
    return nome.charAt(0).toLocaleUpperCase('pt-BR') + nome.slice(1);
  }

  carregar(): void {
    this.pedido?.unsubscribe();
    this.falha.set(null);
    this.fatura.set(null);
    // O cartão e o mês da URL que não servem nem chegam à API.
    if (!Number.isSafeInteger(this.cartaoId()) || this.cartaoId() <= 0) {
      this.falha.set({ mensagem: 'Cartão não encontrado.', deNovo: false });
      return;
    }
    if (!MES_VALIDO.test(this.mes())) {
      this.falha.set({ mensagem: 'Mês inválido.', deNovo: false });
      return;
    }
    this.pedido = this.servico.fatura(this.cartaoId(), this.mes()).subscribe({
      next: (fatura) => this.fatura.set(fatura),
      error: (erro: unknown) => {
        const status = statusDoErro(erro);
        this.falha.set(status === 404 ? { mensagem: detalheDoErro(erro, 'Cartão não encontrado.'), deNovo: false }
          : status === 400 ? { mensagem: camposDoErro(erro)['mes'] ?? 'Mês inválido.', deNovo: false }
            : { mensagem: 'Não foi possível carregar a fatura.', deNovo: true });
      },
    });
  }

  mudarMes(passo: -1 | 1): void {
    void this.router.navigate(['/cartoes', this.cartaoId(), 'faturas', mesVizinho(this.mes(), passo)]);
  }

  novaCompra(): void {
    this.abrirCompra('COMPRA', null);
  }

  novoEstorno(): void {
    this.abrirCompra('ESTORNO', null);
  }

  abrirItem(item: ItemDaFatura): void {
    this.abrirCompra(item.tipo, item.compraId);
  }

  ajustar(): void {
    const fatura = this.fatura();
    if (fatura === null) {
      return;
    }
    abrirAjuste(this.dialogo, fatura).subscribe((resultado) => {
      if (resultado !== undefined) {
        this.carregar();
      }
    });
  }

  /** Decisão 12: "Voltar ao fechamento normal" desfaz o ajuste. */
  voltarAoNormal(): void {
    this.servico.voltarAoFechamentoNormal(this.cartaoId(), this.mes()).subscribe({
      next: () => {
        this.snackBar.open('O fechamento voltou ao normal.', 'Fechar', { duration: 5000 });
        this.carregar();
      },
      error: (erro: unknown) => this.snackBar.open(statusDoErro(erro) === 422
        ? detalheDoErro(erro, 'Não foi possível voltar ao fechamento normal. Tente novamente.')
        : 'Não foi possível voltar ao fechamento normal. Tente novamente.', 'Fechar', { duration: 6000 }),
    });
  }

  private abrirCompra(tipo: TipoDaCompra, id: number | null): void {
    const categorias = this.categorias();
    if (categorias === null) {
      return;
    }
    abrirCompra(this.dialogo, { tipo, id, cartaoId: this.cartaoId(), hoje: this.hoje, categorias }).subscribe((resultado) => {
      if (resultado !== undefined) {
        this.carregar();
      }
    });
  }
}
