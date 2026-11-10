import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';

import { Alerta } from '../../shared/alerta/alerta';
import { CabecalhoPagina } from '../../shared/cabecalho-pagina/cabecalho-pagina';
import { abrirConfirmacao } from '../../shared/confirmacao/confirmacao-dialogo';
import { dataCurtaDoInstante } from '../../shared/datas/data-curta';
import { EstadoVazio } from '../../shared/estado-vazio/estado-vazio';
import { Icone } from '../../shared/icone/icone';
import { detalheDoErro, statusDoErro } from '../auth/erros-http';
import { CategoriaDialogo, DadosDoDialogoDeCategoria } from './categoria-dialogo/categoria-dialogo';
import { Categoria, CategoriasService, GrupoDeCategorias, ListaDeCategorias, Natureza } from './categorias.service';
import { paragrafosDeArquivar, paragrafosDeExcluir, quantidadeDeSubcategorias } from './frases';

@Component({
  selector: 'app-categorias',
  imports: [MatButtonModule, MatMenuModule, MatDividerModule, RouterLink, Alerta, CabecalhoPagina, EstadoVazio, Icone],
  templateUrl: './categorias.html',
  styleUrl: './categorias.scss',
})
export class Categorias {
  private readonly servico = inject(CategoriasService);
  private readonly dialogo = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly dataCurtaDoInstante = dataCurtaDoInstante;
  readonly quantidadeDeSubcategorias = quantidadeDeSubcategorias;
  /** A aba vem da URL: ?natureza=receita; sem o parâmetro (ou com outro valor), despesas. */
  readonly natureza = toSignal(
    inject(ActivatedRoute).queryParamMap.pipe(map((parametros): Natureza => parametros.get('natureza') === 'receita' ? 'RECEITA' : 'DESPESA')),
    { initialValue: 'DESPESA' as Natureza },
  );
  readonly lista = signal<ListaDeCategorias | null>(null);
  readonly falhaAoCarregar = signal(false);
  readonly arquivadasAbertas = signal(false);
  readonly erroAoReativar = signal<string | null>(null);
  readonly grupo = computed<GrupoDeCategorias | null>(() => {
    const lista = this.lista();
    if (lista === null) {
      return null;
    }
    return this.natureza() === 'RECEITA' ? lista.receitas : lista.despesas;
  });

  constructor() {
    this.carregar();
  }

  carregar(): void {
    this.falhaAoCarregar.set(false);
    this.servico.listar().subscribe({
      next: (lista) => this.lista.set(lista),
      error: () => this.falhaAoCarregar.set(true),
    });
  }

  /** Quantas categorias ativas a natureza tem, contando as subcategorias (número da aba). */
  quantidadeAtiva(grupo: GrupoDeCategorias): number {
    return grupo.ativas.reduce((total, principal) => total + 1 + principal.subcategorias.length, 0);
  }

  novaCategoria(maeId: number | null = null): void {
    this.abrirDialogo(null, this.natureza(), maeId, false);
  }

  editar(categoria: Categoria): void {
    const temSubcategorias = categoria.categoriaMaeId === null
      && (categoria.subcategorias.length > 0 || this.arquivadasDe(categoria).length > 0);
    this.abrirDialogo(categoria, categoria.natureza, categoria.categoriaMaeId, temSubcategorias);
  }

  arquivar(categoria: Categoria): void {
    const ativas = categoria.arquivadaEm === null ? categoria.subcategorias : [];
    if (ativas.length === 0) {
      this.servico.arquivar(categoria.id).subscribe({
        next: () => {
          this.carregar();
          this.snackBar.open(`${categoria.nome} arquivada.`, 'Desfazer', { duration: 8000 })
            .onAction().subscribe(() => this.reativar(categoria));
        },
        error: () => this.snackBar.open('Não foi possível arquivar a categoria. Tente novamente.', 'Fechar', { duration: 5000 }),
      });
      return;
    }
    abrirConfirmacao(this.dialogo, {
      titulo: `Arquivar ${categoria.nome}?`,
      icone: 'arquivar',
      perigo: false,
      paragrafos: paragrafosDeArquivar(categoria.nome, ativas.map((sub) => sub.nome)),
      confirmar: 'Arquivar',
      falha: 'Não foi possível arquivar a categoria. Tente novamente.',
      acao: () => this.servico.arquivar(categoria.id),
    }).subscribe((arquivada) => {
      if (arquivada === true) {
        this.carregar();
      }
    });
  }

  reativar(categoria: Categoria): void {
    this.erroAoReativar.set(null);
    this.servico.reativar(categoria.id).subscribe({
      next: () => {
        this.carregar();
        this.snackBar.open(`${categoria.nome} reativada.`, 'Fechar', { duration: 5000 });
      },
      error: (erro: unknown) => {
        this.erroAoReativar.set(statusDoErro(erro) === 422
          ? detalheDoErro(erro, 'Não foi possível reativar a categoria. Tente novamente.')
          : 'Não foi possível reativar a categoria. Tente novamente.');
        this.arquivadasAbertas.set(true);
      },
    });
  }

  excluir(categoria: Categoria): void {
    // Todas as subcategorias, ativas e arquivadas, vão junto (decisão 21).
    const subcategorias = categoria.categoriaMaeId !== null ? []
      : [...categoria.subcategorias, ...this.arquivadasDe(categoria)].map((sub) => sub.nome);
    abrirConfirmacao(this.dialogo, {
      titulo: `Excluir ${categoria.nome}?`,
      icone: 'lixeira',
      perigo: true,
      paragrafos: paragrafosDeExcluir(subcategorias),
      confirmar: 'Excluir categoria',
      falha: 'Não foi possível excluir a categoria. Tente novamente.',
      acao: () => this.servico.excluir(categoria.id),
    }).subscribe((excluida) => {
      if (excluida === true) {
        this.carregar();
      }
    });
  }

  alternarArquivadas(): void {
    this.arquivadasAbertas.update((abertas) => !abertas);
  }

  /** Subcategorias arquivadas de uma principal ativa (ficam na seção Arquivadas, fora dela). */
  private arquivadasDe(principal: Categoria): readonly Categoria[] {
    const lista = this.lista();
    if (lista === null || principal.arquivadaEm !== null) {
      return [];
    }
    const grupo = principal.natureza === 'RECEITA' ? lista.receitas : lista.despesas;
    return grupo.arquivadas.filter((categoria) => categoria.categoriaMaeId === principal.id);
  }

  private abrirDialogo(categoria: Categoria | null, natureza: Natureza, maeId: number | null, temSubcategorias: boolean): void {
    const lista = this.lista();
    const principais = { DESPESA: lista?.despesas.ativas ?? [], RECEITA: lista?.receitas.ativas ?? [] };
    this.dialogo.open<CategoriaDialogo, DadosDoDialogoDeCategoria, Categoria>(CategoriaDialogo, {
      data: { categoria, natureza, maeId, principais, temSubcategorias },
      panelClass: 'flux-dialogo',
      width: '460px',
      maxWidth: 'calc(100vw - 32px)',
      maxHeight: 'calc(100vh - 32px)',
      autoFocus: '#categoria-nome',
    }).afterClosed().subscribe((salva) => {
      if (salva !== undefined) {
        this.carregar();
      }
    });
  }
}
