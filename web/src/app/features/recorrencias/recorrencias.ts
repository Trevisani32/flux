import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';

import { AGORA } from '../../core/tempo/agora';
import { Alerta } from '../../shared/alerta/alerta';
import { CabecalhoPagina } from '../../shared/cabecalho-pagina/cabecalho-pagina';
import { abrirConfirmacao } from '../../shared/confirmacao/confirmacao-dialogo';
import { dataCurta, dataIsoNoFuso } from '../../shared/datas/data-curta';
import { EstadoVazio } from '../../shared/estado-vazio/estado-vazio';
import { Icone } from '../../shared/icone/icone';
import { Valor } from '../../shared/valor/valor';
import { textoDaCategoria } from '../cartoes/apresentacao';
import { CartoesService, ListaDeCartoes } from '../cartoes/cartoes.service';
import { CategoriasService, ListaDeCategorias } from '../categorias/categorias.service';
import { corDaConta } from '../contas/apresentacao';
import { ContasService, ListaDeContas } from '../contas/contas.service';
import { abrirHistorico } from './historico-dialogo/historico-dialogo';
import { LinhaDaOcorrencia } from './linha-da-ocorrencia/linha-da-ocorrencia';
import { dataDaOcorrencia, fraseDaRecorrencia, notaDoAConfirmar, paragrafoDeEncerrar, paragrafoDeExcluir } from './frases';
import { FluxoDaOcorrencia } from './fluxo-da-ocorrencia';
import { abrirRecorrencia } from './recorrencia-dialogo/recorrencia-dialogo';
import { ListaDeRecorrencias, Recorrencia, RecorrenciasService } from './recorrencias.service';

@Component({
  selector: 'app-recorrencias',
  imports: [MatButtonModule, MatDividerModule, MatMenuModule, Alerta, CabecalhoPagina, EstadoVazio, Icone, LinhaDaOcorrencia, Valor],
  templateUrl: './recorrencias.html',
  styleUrl: './recorrencias.scss',
})
export class Recorrencias {
  private readonly servico = inject(RecorrenciasService);
  private readonly contasService = inject(ContasService);
  private readonly cartoesService = inject(CartoesService);
  private readonly categoriasService = inject(CategoriasService);
  private readonly fluxo = inject(FluxoDaOcorrencia);
  private readonly dialogo = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly rota = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly agora = inject(AGORA);

  readonly lista = signal<ListaDeRecorrencias | null>(null);
  readonly contas = signal<ListaDeContas | null>(null);
  readonly cartoes = signal<ListaDeCartoes | null>(null);
  readonly categorias = signal<ListaDeCategorias | null>(null);
  readonly falha = signal(false);
  readonly encerradasAbertas = signal(false);
  readonly hoje = dataIsoNoFuso(this.agora());
  readonly fraseDaRecorrencia = fraseDaRecorrencia;
  readonly dataDaOcorrencia = dataDaOcorrencia;
  readonly notaDoAConfirmar = notaDoAConfirmar;
  readonly textoDaCategoria = textoDaCategoria;
  readonly corDaConta = corDaConta;
  readonly dataCurta = dataCurta;
  readonly temApoio = computed(() => this.contas() !== null && this.cartoes() !== null && this.categorias() !== null);
  private novaPedida = this.rota.snapshot.queryParamMap.get('nova') === 'recorrencia';

  constructor() {
    this.carregar();
    this.fluxo.mudou$.pipe(takeUntilDestroyed()).subscribe(() => this.carregar());
  }

  carregar(): void {
    this.falha.set(false);
    forkJoin({
      lista: this.servico.listar(),
      contas: this.contasService.listar(),
      cartoes: this.cartoesService.listar(),
      categorias: this.categoriasService.listar(),
    }).subscribe({
      next: ({ lista, contas, cartoes, categorias }) => {
        this.lista.set(lista);
        this.contas.set(contas);
        this.cartoes.set(cartoes);
        this.categorias.set(categorias);
        if (this.novaPedida) {
          this.novaPedida = false;
          void this.router.navigate([], { relativeTo: this.rota, queryParams: { nova: null }, replaceUrl: true });
          this.novaRecorrencia();
        }
      },
      error: () => this.falha.set(true),
    });
  }

  novaRecorrencia(): void {
    this.abrirDialogo(null);
  }

  editar(recorrencia: Recorrencia): void {
    this.abrirDialogo(recorrencia);
  }

  historico(recorrencia: Recorrencia): void {
    abrirHistorico(this.dialogo, recorrencia.id, this.hoje).subscribe();
  }

  encerrar(recorrencia: Recorrencia): void {
    abrirConfirmacao(this.dialogo, {
      titulo: `Encerrar ${recorrencia.descricao}?`,
      icone: 'encerrar',
      perigo: false,
      paragrafos: [paragrafoDeEncerrar(recorrencia.confirmadas, recorrencia.cartaoId !== null)],
      confirmar: 'Encerrar',
      falha: 'Não foi possível encerrar a recorrência. Tente novamente.',
      acao: () => this.servico.encerrar(recorrencia.id),
    }).subscribe((encerrada) => {
      if (encerrada === true) {
        this.snackBar.open('Recorrência encerrada.', 'Fechar', { duration: 5000 });
        this.carregar();
      }
    });
  }

  retomar(recorrencia: Recorrencia): void {
    this.servico.retomar(recorrencia.id).subscribe({
      next: () => {
        this.snackBar.open('Recorrência retomada.', 'Fechar', { duration: 5000 });
        this.carregar();
      },
      error: () => this.snackBar.open('Não foi possível retomar a recorrência. Tente novamente.', 'Fechar', { duration: 5000 }),
    });
  }

  excluir(recorrencia: Recorrencia): void {
    abrirConfirmacao(this.dialogo, {
      titulo: `Excluir ${recorrencia.descricao}?`,
      icone: 'lixeira',
      perigo: true,
      paragrafos: [paragrafoDeExcluir(recorrencia.confirmadas, recorrencia.cartaoId !== null)],
      confirmar: 'Excluir',
      falha: 'Não foi possível excluir a recorrência. Tente novamente.',
      acao: () => this.servico.excluir(recorrencia.id),
    }).subscribe((excluida) => {
      if (excluida === true) {
        this.snackBar.open('Recorrência excluída.', 'Fechar', { duration: 5000 });
        this.carregar();
      }
    });
  }

  alternarEncerradas(): void {
    this.encerradasAbertas.update((abertas) => !abertas);
  }

  /** A nota do card "A confirmar": a do desktop ou, com {@code curta}, a do celular (seção 3.4). */
  notaDoCardAConfirmar(curta = false): string {
    const aConfirmar = this.lista()?.aConfirmar;
    if (aConfirmar === undefined) {
      return '';
    }
    return notaDoAConfirmar(
      aConfirmar.ocorrencias.filter((ocorrencia) => ocorrencia.atrasada).length,
      aConfirmar.ate,
      curta,
    );
  }

  textoDasAtrasadas(recorrencia: Recorrencia): string {
    return `${recorrencia.atrasadas} atrasada${recorrencia.atrasadas === 1 ? '' : 's'}`;
  }

  corDaRecorrencia(recorrencia: Recorrencia): string | null {
    if (recorrencia.cartaoId !== null) {
      return corDaConta(recorrencia.cartaoCor);
    }
    const conta = [...(this.contas()?.ativas ?? []), ...(this.contas()?.arquivadas ?? [])]
      .find((item) => item.id === recorrencia.contaId);
    return corDaConta(conta?.cor ?? null);
  }

  private abrirDialogo(recorrencia: Recorrencia | null): void {
    const contas = this.contas();
    const cartoes = this.cartoes();
    const categorias = this.categorias();
    if (contas === null || cartoes === null || categorias === null) {
      return;
    }
    abrirRecorrencia(this.dialogo, { recorrencia, hoje: this.hoje, contas, cartoes, categorias }).subscribe((salva) => {
      if (salva !== undefined) {
        this.snackBar.open(recorrencia === null ? 'Recorrência criada.' : 'Recorrência salva.', 'Fechar', { duration: 5000 });
        this.carregar();
      }
    });
  }
}
