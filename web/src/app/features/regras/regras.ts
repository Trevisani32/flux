import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatMenuModule } from '@angular/material/menu';

import { Alerta } from '../../shared/alerta/alerta';
import { CabecalhoPagina } from '../../shared/cabecalho-pagina/cabecalho-pagina';
import { abrirConfirmacao } from '../../shared/confirmacao/confirmacao-dialogo';
import { EstadoVazio } from '../../shared/estado-vazio/estado-vazio';
import { Icone } from '../../shared/icone/icone';
import { codigoDoErro, detalheDoErro, statusDoErro } from '../auth/erros-http';
import { condicaoNoInicio, fraseDaCondicao, metaDaRegra, motivoDaPausa, nomeDaCategoria } from './apresentacao';
import { FluxoDeRegras } from './fluxo-de-regras';
import { Regra, RegrasService } from './regras.service';

/**
 * A tela de Regras (decisões 9 a 13): a lista numerada, a primeira que casar vale. "Subir" e "Descer"
 * ficam na linha no desktop e no "⋯" no celular; enquanto a ordem nova vai para a API, os dois travam.
 */
@Component({
  selector: 'app-regras',
  imports: [MatButtonModule, MatMenuModule, MatDividerModule, Alerta, CabecalhoPagina, EstadoVazio, Icone],
  templateUrl: './regras.html',
  styleUrl: './regras.scss',
})
export class Regras {
  private readonly servico = inject(RegrasService);
  private readonly fluxo = inject(FluxoDeRegras);
  private readonly dialogo = inject(MatDialog);

  readonly lista = signal<readonly Regra[] | null>(null);
  readonly falhaAoCarregar = signal(false);
  readonly ordenando = signal(false);
  readonly erroDaOrdem = signal<string | null>(null);
  readonly subtitulo = computed(() => {
    const regras = this.lista();
    const explica = 'A primeira que casar com a descrição escolhe a categoria.';
    if (regras === null || regras.length === 0) {
      return explica;
    }
    return `${explica} ${regras.length === 1 ? '1 regra.' : `${regras.length} regras.`}`;
  });
  readonly condicaoNoInicio = condicaoNoInicio;
  readonly fraseDaCondicao = fraseDaCondicao;
  readonly metaDaRegra = metaDaRegra;
  readonly motivoDaPausa = motivoDaPausa;
  readonly nomeDaCategoria = nomeDaCategoria;

  constructor() {
    this.carregar();
  }

  carregar(): void {
    this.falhaAoCarregar.set(false);
    this.servico.listar().subscribe({
      next: (regras) => this.lista.set(regras),
      error: () => this.falhaAoCarregar.set(true),
    });
  }

  novaRegra(): void {
    this.fluxo.novaRegra().subscribe((regra) => {
      if (regra !== undefined) {
        this.carregar();
      }
    });
  }

  editar(regra: Regra): void {
    this.fluxo.editar(regra).subscribe((resultado) => {
      if (resultado !== undefined) {
        this.carregar();
      }
    });
  }

  aplicar(regraId: number | null): void {
    this.fluxo.aplicar(regraId).subscribe();
  }

  excluir(regra: Regra): void {
    abrirConfirmacao(this.dialogo, {
      titulo: `Excluir a regra ${fraseDaCondicao(regra.condicao, regra.texto)}?`,
      icone: 'lixeira',
      perigo: true,
      paragrafos: [['Os lançamentos que ela já categorizou continuam como estão. As regras de baixo sobem uma posição.']],
      confirmar: 'Excluir regra',
      falha: 'Não foi possível excluir a regra. Tente novamente.',
      acao: () => this.servico.excluir(regra.id),
    }).subscribe((excluida) => {
      if (excluida === true) {
        this.carregar();
      }
    });
  }

  /**
   * Troca a regra de lugar com a vizinha (decisão 9) e manda a lista inteira. Se outra aba mudou as
   * regras, a API recusa, a página avisa e recarrega.
   */
  mover(regra: Regra, passo: -1 | 1): void {
    const atual = this.lista();
    if (atual === null || this.ordenando()) {
      return;
    }
    const indice = atual.findIndex((item) => item.id === regra.id);
    const vizinha = indice + passo;
    if (indice < 0 || vizinha < 0 || vizinha >= atual.length) {
      return;
    }
    const ids = atual.map((item) => item.id);
    [ids[indice], ids[vizinha]] = [ids[vizinha], ids[indice]];
    this.ordenando.set(true);
    this.erroDaOrdem.set(null);
    this.servico.reordenar(ids).subscribe({
      next: (regras) => {
        this.lista.set(regras);
        this.ordenando.set(false);
      },
      error: (erro: unknown) => {
        this.ordenando.set(false);
        if (statusDoErro(erro) === 422 && codigoDoErro(erro) === 'ORDEM_DESATUALIZADA') {
          this.erroDaOrdem.set(detalheDoErro(erro, 'A lista de regras mudou.'));
          this.carregar();
        } else {
          this.erroDaOrdem.set('Não foi possível mudar a ordem. Tente novamente.');
        }
      },
    });
  }
}
