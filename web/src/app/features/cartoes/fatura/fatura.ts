import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription, finalize } from 'rxjs';

import { AGORA } from '../../../core/tempo/agora';
import { Alerta } from '../../../shared/alerta/alerta';
import { CabecalhoPagina } from '../../../shared/cabecalho-pagina/cabecalho-pagina';
import { abrirConfirmacao } from '../../../shared/confirmacao/confirmacao-dialogo';
import { dataIsoNoFuso } from '../../../shared/datas/data-curta';
import { EstadoVazio } from '../../../shared/estado-vazio/estado-vazio';
import { Icone } from '../../../shared/icone/icone';
import { formatarMoeda, partesDaMoeda } from '../../../shared/moeda/moeda';
import { Valor } from '../../../shared/valor/valor';
import { camposDoErro, detalheDoErro, statusDoErro } from '../../auth/erros-http';
import { CategoriasService, ListaDeCategorias } from '../../categorias/categorias.service';
import { nomeDoMes } from '../../lancamentos/apresentacao';
import { mesVizinho } from '../../lancamentos/filtros';
import { abrirAjuste } from '../ajuste-dialogo/ajuste-dialogo';
import {
  comAno, diaEMes, faixaDeMeses, metaDoItemDaFatura, mesSemAno, nomeDaFatura, quandoFecha, ROTULO_DA_SITUACAO, rotuloDaParcela,
  textoDaCategoria, textoDoTotal,
} from '../apresentacao';
import { CartoesService, Fatura, ItemDaFatura, PagamentoNaFatura } from '../cartoes.service';
import { abrirCompra } from '../compra-dialogo/compra-dialogo';
import { TipoDaCompra } from '../compras.service';
import { abrirPagamento } from '../pagamento-dialogo/pagamento-dialogo';

/** O mês da URL: "2026-12". Fora disso, a página nem pede a fatura. */
const MES_VALIDO = /^\d{4}-(0[1-9]|1[0-2])$/;

/**
 * A fatura de um mês (decisões 8 a 13 da 05a), em /cartoes/:id/faturas/:mes: as setas andam de mês em mês, até onde a
 * API diz; a linha do tempo, os totais e os lançamentos, a compra mais recente primeiro. Desde a 05b (decisões 30 a 38):
 * o disponível na trilha; "Pagar fatura" e "Marcar como paga fora do Flux" no topo quando há pendente; o card do
 * pagamento (o pago, o pendente, o limite disponível e "Desmarcar"); o card "Pagamentos"; e a situação paga, com a nota
 * da trava na lista. Os números vêm da API.
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
  /** Correções do QA (05b): "Desmarcar paga fora do Flux" e "Voltar ao fechamento normal" ficam travados durante o pedido. */
  readonly enviando = signal(false);
  readonly titulo = computed(() => MES_VALIDO.test(this.mes()) ? `Fatura de ${nomeDoMes(this.mes())}` : 'Fatura');
  readonly anterior = computed(() => mesVizinho(this.mes(), -1));
  readonly seguinte = computed(() => mesVizinho(this.mes(), 1));
  readonly podeLancar = computed(() => {
    const fatura = this.fatura();
    return fatura !== null && !fatura.cartaoArquivado && this.categorias() !== null;
  });
  /** Decisões 12 e 35 (05b): a fatura paga não se ajusta. */
  readonly podeAjustar = computed(() => this.podeLancar() && this.fatura()?.situacao !== 'PAGA');
  /** Decisão 30: "Pagar fatura" na aberta e nas que já fecharam, com pendente e o cartão ativo. */
  readonly podePagar = computed(() => {
    const fatura = this.fatura();
    return fatura !== null && !fatura.cartaoArquivado && (fatura.situacao === 'ABERTA' || fatura.situacao === 'FECHADA')
      && (fatura.pendente ?? 0) > 0;
  });
  /** Decisão 36: "Marcar como paga fora do Flux" só na fechada com pendente; "Desmarcar" na marcada. */
  readonly podeMarcarPagaFora = computed(() => {
    const fatura = this.fatura();
    return fatura !== null && !fatura.cartaoArquivado && fatura.situacao === 'FECHADA' && (fatura.pendente ?? 0) > 0;
  });
  readonly podeDesmarcarPagaFora = computed(() => {
    const fatura = this.fatura();
    return fatura !== null && !fatura.cartaoArquivado && fatura.pagaFora === true;
  });
  /** O card do pagamento: na aberta, nas que já fecharam e em qualquer uma com pagamento. */
  readonly mostraOPagamento = computed(() => {
    const fatura = this.fatura();
    return fatura !== null && (fatura.situacao !== 'FUTURA' || (fatura.quantidadePagamentos ?? 0) > 0);
  });
  /** "Fecha em 21 dias" (com "· R$ 300,00 pagos" no antecipado, decisão 34), "Fechou em 03/11 · " ou "Começa em 04/12". */
  readonly notaDaSituacao = computed(() => {
    const fatura = this.fatura();
    if (fatura === null) {
      return '';
    }
    if (fatura.situacao === 'ABERTA') {
      const fecha = quandoFecha(this.hoje, fatura.fechamento);
      return (fatura.pago ?? 0) > 0 ? `${fecha} · ${formatarMoeda(fatura.pago)} pagos` : fecha;
    }
    if (fatura.situacao === 'FUTURA') {
      return `Começa em ${diaEMes(fatura.inicio)}`;
    }
    return `Fechou em ${diaEMes(fatura.fechamento)} · `;
  });
  /** Na fechada e na paga: "venceu em 10/11" ou "vence em 10/12"; em laranja quando venceu com pendente (decisão 34). */
  readonly notaDoVencimento = computed(() => {
    const fatura = this.fatura();
    if (fatura === null || fatura.situacao === 'ABERTA' || fatura.situacao === 'FUTURA') {
      return null;
    }
    const venceu = fatura.vencimento < this.hoje;
    return { texto: `${venceu ? 'venceu' : 'vence'} em ${diaEMes(fatura.vencimento)}`, atrasada: venceu && (fatura.pendente ?? 0) > 0 };
  });
  /** "1 pagamento", "2 pagamentos", "paga fora do Flux" ou "nenhum pagamento". */
  readonly notaDoPago = computed(() => {
    const fatura = this.fatura();
    if (fatura === null) {
      return '';
    }
    if (fatura.pagaFora === true) {
      return 'paga fora do Flux';
    }
    const quantos = fatura.quantidadePagamentos ?? 0;
    return quantos === 0 ? 'nenhum pagamento' : quantos === 1 ? '1 pagamento' : `${quantos} pagamentos`;
  });
  /** "nada a pagar", "venceu em 10/11" ou "vence em 10/12". */
  readonly notaDoPendente = computed(() => {
    const fatura = this.fatura();
    if (fatura === null) {
      return '';
    }
    if ((fatura.pendente ?? 0) <= 0) {
      return 'nada a pagar';
    }
    return `${fatura.vencimento < this.hoje ? 'venceu' : 'vence'} em ${diaEMes(fatura.vencimento)}`;
  });
  /** "de R$ 5.000,00" ou, negativo, "acima do limite em R$ 120,00" (decisão 38). */
  readonly notaDoDisponivel = computed(() => {
    const fatura = this.fatura();
    const disponivel = partesDaMoeda(fatura?.disponivel);
    if (fatura === null || disponivel === null) {
      return '';
    }
    return disponivel.negativo ? `acima do limite em ${disponivel.absoluto}` : `de ${formatarMoeda(fatura.limite)}`;
  });
  /** Decisão 38 na trilha: "disponível R$ 732,65 de R$ 5.000,00" ou, negativo, "acima do limite em R$ 120,00". */
  readonly trilhaDoDisponivel = computed(() => {
    const fatura = this.fatura();
    const disponivel = partesDaMoeda(fatura?.disponivel);
    if (fatura === null || disponivel === null) {
      return null;
    }
    return disponivel.negativo ? { acima: true, valor: disponivel.absoluto, de: '' }
      : { acima: false, valor: formatarMoeda(fatura.disponivel), de: formatarMoeda(fatura.limite) };
  });
  /** A nota da lista: na paga, a trava (decisão 35); nas outras, a data em que tudo aparece em Lançamentos (decisão 13). */
  readonly notaDaLista = computed(() => {
    const fatura = this.fatura();
    if (fatura === null) {
      return '';
    }
    return fatura.situacao === 'PAGA' ? 'Paga: só descrição, categoria e observação mudam.'
      : `Em Lançamentos, todos aparecem em ${diaEMes(fatura.vencimento)}, o vencimento.`;
  });
  /** Decisão 38: o disponível negativo deixa a nota da célula em vermelho, como a trilha e Cartões. */
  readonly acimaDoLimite = computed(() => partesDaMoeda(this.fatura()?.disponivel)?.negativo === true);
  /** Decisão 31: "Pago a mais: R$ 50,00.", com o valor da API. */
  readonly pagoAMais = computed(() => {
    const fatura = this.fatura();
    return fatura !== null && (fatura.pagoAMais ?? 0) > 0 ? `Pago a mais: ${formatarMoeda(fatura.pagoAMais)}.` : null;
  });
  readonly formatarMoeda = formatarMoeda;
  readonly notaDasCompras = computed(() => {
    const quantas = this.fatura()?.quantidadeCompras ?? 0;
    return quantas === 1 ? '1 compra ou parcela' : `${quantas} compras e parcelas`;
  });
  readonly notaDosEstornos = computed(() => {
    const quantos = this.fatura()?.quantidadeEstornos ?? 0;
    return quantos === 1 ? '1 estorno' : `${quantos} estornos`;
  });
  /** "fechou em 03/11" na fechada e na paga (05b); "até agora; fecha em 03/12" na aberta e na futura. */
  readonly notaDoTotal = computed(() => {
    const fatura = this.fatura();
    if (fatura === null) {
      return '';
    }
    return fatura.situacao === 'FECHADA' || fatura.situacao === 'PAGA' ? `fechou em ${diaEMes(fatura.fechamento)}`
      : `até agora; fecha em ${diaEMes(fatura.fechamento)}`;
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

  /** {@code avisarPagamento}: depois de "Pagar fatura", o aviso diz se a fatura ficou paga. */
  carregar(avisarPagamento = false): void {
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
      next: (fatura) => {
        this.fatura.set(fatura);
        if (avisarPagamento) {
          this.snackBar.open(fatura.situacao === 'PAGA' ? `Pagamento registrado. A fatura de ${nomeDaFatura(fatura.mes, this.hoje)} está paga.`
            : 'Pagamento registrado.', 'Fechar', { duration: 5000 });
        }
      },
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
    if (this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.servico.voltarAoFechamentoNormal(this.cartaoId(), this.mes()).pipe(finalize(() => this.enviando.set(false))).subscribe({
      next: () => {
        this.snackBar.open('O fechamento voltou ao normal.', 'Fechar', { duration: 5000 });
        this.carregar();
      },
      error: (erro: unknown) => this.snackBar.open(statusDoErro(erro) === 422
        ? detalheDoErro(erro, 'Não foi possível voltar ao fechamento normal. Tente novamente.')
        : 'Não foi possível voltar ao fechamento normal. Tente novamente.', 'Fechar', { duration: 6000 }),
    });
  }

  /** Decisão 30: "Pagar fatura". */
  pagar(): void {
    abrirPagamento(this.dialogo, { cartaoId: this.cartaoId(), mes: this.mes(), pagamentoId: null, hoje: this.hoje }).subscribe((resultado) => {
      if (resultado !== undefined) {
        this.carregar(resultado === 'pago');
      }
    });
  }

  /** Decisão 33: o pagamento abre o diálogo dele. */
  abrirPagamento(pagamento: PagamentoNaFatura): void {
    abrirPagamento(this.dialogo, { cartaoId: null, mes: null, pagamentoId: pagamento.id, hoje: this.hoje }).subscribe((resultado) => {
      if (resultado !== undefined) {
        this.carregar();
      }
    });
  }

  /** Decisão 36: a confirmação diz que nenhuma conta muda e quanto deixa de contar no limite. */
  marcarPagaFora(): void {
    const fatura = this.fatura();
    if (fatura === null) {
      return;
    }
    abrirConfirmacao(this.dialogo, {
      titulo: `Marcar a fatura de ${nomeDaFatura(fatura.mes, this.hoje)} como paga?`,
      icone: 'sucesso',
      perigo: false,
      paragrafos: [['Use para faturas pagas antes de você usar o Flux: nenhuma conta muda, e os ', { forte: formatarMoeda(fatura.pendente) },
        ' pendentes deixam de contar no limite. Dá para desmarcar depois.']],
      confirmar: 'Marcar como paga',
      falha: 'Não foi possível marcar a fatura. Tente novamente.',
      acao: () => this.servico.marcarPagaFora(this.cartaoId(), this.mes()),
    }).subscribe((marcada) => {
      if (marcada === true) {
        this.snackBar.open('Fatura marcada como paga fora do Flux.', 'Fechar', { duration: 4000 });
        this.carregar();
      }
    });
  }

  /** Decisão 36: "Desmarcar"; a fatura volta a ter o pendente. */
  desmarcarPagaFora(): void {
    if (this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.servico.desmarcarPagaFora(this.cartaoId(), this.mes()).pipe(finalize(() => this.enviando.set(false))).subscribe({
      next: () => {
        this.snackBar.open('A fatura voltou a ter o pendente.', 'Fechar', { duration: 4000 });
        this.carregar();
      },
      error: (erro: unknown) => this.snackBar.open(statusDoErro(erro) === 422
        ? detalheDoErro(erro, 'Não foi possível desmarcar a fatura. Tente novamente.')
        : 'Não foi possível desmarcar a fatura. Tente novamente.', 'Fechar', { duration: 6000 }),
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
