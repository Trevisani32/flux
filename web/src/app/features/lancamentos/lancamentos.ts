import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, convertToParamMap, Router, RouterLink } from '@angular/router';
import { Subject, Subscription, debounceTime } from 'rxjs';

import { AGORA } from '../../core/tempo/agora';
import { Alerta } from '../../shared/alerta/alerta';
import { CabecalhoPagina } from '../../shared/cabecalho-pagina/cabecalho-pagina';
import { dataIsoNoFuso } from '../../shared/datas/data-curta';
import { EstadoVazio } from '../../shared/estado-vazio/estado-vazio';
import { Icone } from '../../shared/icone/icone';
import { Valor } from '../../shared/valor/valor';
import { abrirCompra } from '../cartoes/compra-dialogo/compra-dialogo';
import { CartoesService, ListaDeCartoes } from '../cartoes/cartoes.service';
import { diaEMes, mesSemAno, textoDoTotal } from '../cartoes/apresentacao';
import { CategoriasService, ListaDeCategorias } from '../categorias/categorias.service';
import { ContasService, ListaDeContas } from '../contas/contas.service';
import { FluxoDeRegras } from '../regras/fluxo-de-regras';
import {
  marcaDoDia, nomeDoMes, opcoesDeCategoria, opcoesDeCategoriasArquivadas, quantidadeDeItens, rotuloDoDia,
} from './apresentacao';
import { Filtros, TipoDoFiltro, filtrosDaUrl, mesDe, mesLimite, mesVizinho, parametrosDaUrl, temFiltro } from './filtros';
import { abrirDialogoDeLancamento, contaPadrao } from './lancamento-dialogo/lancamento-dialogo';
import { ItemDaLista, LancamentosService, ListaDoMes } from './lancamentos.service';
import { LinhaDoLancamento } from './linha-do-lancamento/linha-do-lancamento';

interface OpcaoDeTipo {
  readonly valor: TipoDoFiltro | null;
  readonly rotulo: string;
}

/**
 * A lista de um mês, com totais e filtros, que vivem na URL (decisão 13). Mudar um filtro
 * navega; a lista recarrega quando a URL muda. Fatia 05a: o filtro "Conta ou cartão" (decisão 23), a
 * faixa da fatura com o filtro de um cartão e a parcela que abre a compra (decisão 25).
 */
@Component({
  selector: 'app-lancamentos',
  imports: [MatButtonModule, RouterLink, Alerta, CabecalhoPagina, EstadoVazio, Icone, LinhaDoLancamento, Valor],
  templateUrl: './lancamentos.html',
  styleUrl: './lancamentos.scss',
})
export class Lancamentos {
  private readonly rota = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly servico = inject(LancamentosService);
  private readonly contasService = inject(ContasService);
  private readonly cartoesService = inject(CartoesService);
  private readonly categoriasService = inject(CategoriasService);
  private readonly dialogo = inject(MatDialog);
  private readonly fluxoDeRegras = inject(FluxoDeRegras);
  private readonly buscas = new Subject<string>();
  private pedido: Subscription | null = null;

  readonly hoje = dataIsoNoFuso(inject(AGORA)());
  readonly nomeDoMes = nomeDoMes;
  readonly rotuloDoDia = rotuloDoDia;
  readonly marcaDoDia = marcaDoDia;
  readonly diaEMes = diaEMes;
  readonly mesSemAno = mesSemAno;
  readonly textoDoTotal = textoDoTotal;
  readonly tipos: readonly OpcaoDeTipo[] = [
    { valor: null, rotulo: 'Todos' },
    { valor: 'receita', rotulo: 'Receitas' },
    { valor: 'despesa', rotulo: 'Despesas' },
    { valor: 'transferencia', rotulo: 'Transferências' },
  ];
  readonly filtros = signal<Filtros>(filtrosDaUrl(convertToParamMap({}), this.hoje));
  readonly lista = signal<ListaDoMes | null>(null);
  readonly contas = signal<ListaDeContas | null>(null);
  /** Fatia 05a: os cartões do filtro "Conta ou cartão"; sem eles (erro ao carregar), o filtro só tem as contas. */
  readonly cartoes = signal<ListaDeCartoes | null>(null);
  readonly categorias = signal<ListaDeCategorias | null>(null);
  readonly falha = signal(false);
  readonly filtrando = computed(() => temFiltro(this.filtros()));
  readonly noLimite = computed(() => this.filtros().mes >= mesLimite(this.hoje));
  readonly foraDoMesAtual = computed(() => this.filtros().mes !== mesDe(this.hoje));
  readonly temContaAtiva = computed(() => (this.contas()?.ativas.length ?? 0) > 0);
  readonly temConta = computed(() => {
    const contas = this.contas();
    return contas !== null && contas.ativas.length + contas.arquivadas.length > 0;
  });
  readonly subtitulo = computed(() => {
    const lista = this.lista();
    if (lista === null) {
      return '';
    }
    const contas = this.contas();
    const cartoes = this.cartoes();
    const { conta, cartao } = this.filtros();
    const nome = conta !== null && contas !== null ? [...contas.ativas, ...contas.arquivadas].find((item) => item.id === conta)?.nome ?? null
      : cartao !== null && cartoes !== null ? [...cartoes.ativos, ...cartoes.arquivados].find((item) => item.id === cartao)?.nome ?? null
        : null;
    return `${nome ?? 'Todas as contas'} · ${quantidadeDeItens(lista.quantidadeLancamentos, lista.quantidadeTransferencias)}`;
  });
  readonly categoriasDeDespesa = computed(() => this.opcoes((lista) => opcoesDeCategoria(lista.despesas)));
  readonly categoriasDeReceita = computed(() => this.opcoes((lista) => opcoesDeCategoria(lista.receitas)));
  readonly categoriasArquivadas = computed(() => this.opcoes((lista) => opcoesDeCategoriasArquivadas(lista.despesas, lista.receitas)));

  constructor() {
    this.rota.queryParamMap.pipe(takeUntilDestroyed()).subscribe((parametros) => {
      this.filtros.set(filtrosDaUrl(parametros, this.hoje));
      this.carregarLista();
    });
    this.buscas.pipe(debounceTime(300), takeUntilDestroyed()).subscribe((busca) => this.navegar({ ...this.filtros(), busca: busca.trim() }));
    // O "Desfazer" de um importado excluído volta depois que o diálogo fechou (Fatia 04a).
    this.servico.restaurado$.pipe(takeUntilDestroyed()).subscribe(() => this.carregarLista());
    // Fatia 04b: "Aplicar regras" pode terminar depois de um aviso curto, com outra tela aberta por cima.
    this.fluxoDeRegras.aplicadas$.pipe(takeUntilDestroyed()).subscribe(() => this.carregarLista());
    this.carregarApoio();
  }

  /** Com o filtro "Sem categoria" (decisão 17 da 04b): a prévia de todas as regras. */
  aplicarRegras(): void {
    this.fluxoDeRegras.aplicar(null).subscribe();
  }

  /** "Tentar de novo": contas, categorias e a lista. */
  carregarTudo(): void {
    this.carregarApoio();
    this.carregarLista();
  }

  mudarMes(passo: -1 | 1): void {
    this.navegar({ ...this.filtros(), mes: mesVizinho(this.filtros().mes, passo) });
  }

  irParaOMesAtual(): void {
    this.navegar({ ...this.filtros(), mes: mesDe(this.hoje) });
  }

  mudarTipo(tipo: TipoDoFiltro | null): void {
    this.navegar({ ...this.filtros(), tipo });
  }

  /** O filtro "Conta ou cartão": o valor é o id da conta ou "cartao-" e o id do cartão (decisão 23 da 05a). */
  mudarConta(valor: string): void {
    const cartao = valor.startsWith('cartao-') ? Number(valor.slice(7)) : null;
    this.navegar({ ...this.filtros(), conta: valor === '' || cartao !== null ? null : Number(valor), cartao });
  }

  mudarCategoria(valor: string): void {
    this.navegar({ ...this.filtros(), categoria: valor === '' ? null : valor === 'sem' ? 'sem' : Number(valor) });
  }

  /** A busca espera 300 ms sem digitar antes de mudar a URL. */
  buscar(texto: string): void {
    this.buscas.next(texto);
  }

  limparFiltros(): void {
    this.navegar({ mes: this.filtros().mes, conta: null, cartao: null, categoria: null, tipo: null, busca: '' });
  }

  /** Novo (item null) ou Editar; a parcela e o estorno abrem a compra (decisão 25 da 05a). Recarrega a lista quando algo mudou. */
  abrir(item: ItemDaLista | null): void {
    const contas = this.contas();
    const categorias = this.categorias();
    if (contas === null || categorias === null) {
      return;
    }
    if (item !== null && item.compraId !== undefined && item.compraId !== null) {
      abrirCompra(this.dialogo, { tipo: item.estorno === true ? 'ESTORNO' : 'COMPRA', id: item.compraId, cartaoId: null, hoje: this.hoje, categorias })
        .subscribe((resultado) => {
          if (resultado !== undefined) {
            this.carregarLista();
          }
        });
      return;
    }
    abrirDialogoDeLancamento(this.dialogo, {
      item,
      hoje: this.hoje,
      contas,
      categorias,
      contaPadrao: contaPadrao(contas.ativas, this.filtros().conta, this.servico.ultimaConta),
    }).subscribe((resultado) => {
      if (resultado !== undefined) {
        this.carregarLista();
      }
    });
  }

  private navegar(filtros: Filtros): void {
    void this.router.navigate([], { relativeTo: this.rota, queryParams: parametrosDaUrl(filtros, this.hoje) });
  }

  private carregarLista(): void {
    this.pedido?.unsubscribe();
    this.falha.set(false);
    this.pedido = this.servico.listar(this.filtros()).subscribe({
      next: (lista) => this.lista.set(lista),
      error: () => this.falha.set(true),
    });
  }

  private carregarApoio(): void {
    this.contasService.listar().subscribe({
      next: (contas) => this.contas.set(contas),
      error: () => this.falha.set(true),
    });
    this.cartoesService.listar().subscribe({
      next: (cartoes) => this.cartoes.set(cartoes),
      error: () => this.cartoes.set(null),
    });
    this.categoriasService.listar().subscribe({
      next: (categorias) => this.categorias.set(categorias),
      error: () => this.falha.set(true),
    });
  }

  private opcoes<T>(montar: (lista: ListaDeCategorias) => T[]): T[] {
    const lista = this.categorias();
    return lista === null ? [] : montar(lista);
  }
}
