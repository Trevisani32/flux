import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { Observable } from 'rxjs';

import { Alerta } from '../../../shared/alerta/alerta';
import { dataCurta } from '../../../shared/datas/data-curta';
import { Valor } from '../../../shared/valor/valor';
import { codigoDoErro, detalheDoErro, propriedadeDoErro, statusDoErro } from '../../auth/erros-http';
import { condicaoNoInicio, nomeDaCategoria, notaDoGrupo, quantidadeDeLancamentos, textoDoAplicar } from '../apresentacao';
import { Aplicacao, RegrasService } from '../regras.service';

export interface DadosDoAplicar {
  /** Só o grupo desta regra (decisão 19); nulo = todas. */
  readonly regraId: number | null;
}

const FALHA = 'Não foi possível aplicar as regras. Tente novamente.';

/**
 * "Aplicar regras" (decisões 16 a 18): a prévia agrupada pela regra, com uma caixa em cada grupo, todas
 * marcadas. Desmarcar uma regra deixa os lançamentos dela sem categoria. Fecha com quantos mudaram. A
 * regra que sumiu (excluída em outra aba) dá 422: ao carregar, a mensagem fica no alerta; ao aplicar,
 * ela aparece e a prévia é recarregada sem a regra. Se a recarga falhar (a regra era a única do
 * diálogo, ou a rede caiu), fica o alerta da recarga e só o "Cancelar".
 */
@Component({
  selector: 'app-aplicar-dialogo',
  imports: [MatButtonModule, MatDialogModule, Alerta, Valor],
  templateUrl: './aplicar-dialogo.html',
  styleUrl: './aplicar-dialogo.scss',
})
export class AplicarDialogo {
  private readonly servico = inject(RegrasService);
  private readonly referencia = inject<MatDialogRef<AplicarDialogo, number>>(MatDialogRef);

  readonly dados = inject<DadosDoAplicar>(MAT_DIALOG_DATA);
  readonly aplicacao = signal<Aplicacao | null>(null);
  readonly falhaAoCarregar = signal(false);
  /** A mensagem do 422 ao carregar ("Regra não encontrada."): tentar de novo não muda nada. */
  readonly detalheAoCarregar = signal<string | null>(null);
  readonly desmarcadas = signal<ReadonlySet<number>>(new Set());
  readonly enviando = signal(false);
  readonly erro = signal<string | null>(null);
  /** Depois do 422 de regra que sumiu, a recarga deu certo e tem o que aplicar: o alerta diz para conferir. */
  readonly recarregada = signal(false);
  readonly condicaoNoInicio = condicaoNoInicio;
  readonly nomeDaCategoria = nomeDaCategoria;
  readonly notaDoGrupo = notaDoGrupo;
  readonly quantidadeDeLancamentos = quantidadeDeLancamentos;
  readonly dataCurta = dataCurta;
  /** Quantos lançamentos os grupos marcados pegam: o botão conta, sem somar dinheiro. */
  readonly marcados = computed(() => (this.aplicacao()?.grupos ?? [])
    .filter((grupo) => !this.desmarcadas().has(grupo.regraId))
    .reduce((total, grupo) => total + grupo.quantidade, 0));
  readonly textoDoBotao = computed(() => textoDoAplicar(this.marcados()));

  constructor() {
    this.carregar();
  }

  carregar(): void {
    this.carregarPrevia(false);
  }

  /**
   * A prévia sai de cena antes do pedido: o cabeçalho e o botão nunca contam com a prévia velha. Depois
   * do 422 de regra que sumiu (`depoisDoErro`), "A prévia foi atualizada" só aparece se a recarga der
   * certo e trouxer algo para aplicar (vazia, fica o "Nada para aplicar" com "Fechar"); se ela falhar,
   * fica só o alerta dela, sem o erro do aplicar repetido em cima.
   */
  private carregarPrevia(depoisDoErro: boolean): void {
    this.aplicacao.set(null);
    this.falhaAoCarregar.set(false);
    this.detalheAoCarregar.set(null);
    this.servico.aplicacao(this.dados.regraId).subscribe({
      next: (aplicacao) => {
        this.aplicacao.set(aplicacao);
        this.recarregada.set(depoisDoErro && aplicacao.total > 0);
      },
      error: (erro: unknown) => {
        if (depoisDoErro) {
          this.erro.set(null);
        }
        this.detalheAoCarregar.set(statusDoErro(erro) === 422 ? propriedadeDoErro(erro, 'detail') : null);
        this.falhaAoCarregar.set(true);
      },
    });
  }

  marcado(regraId: number): boolean {
    return !this.desmarcadas().has(regraId);
  }

  alternar(regraId: number): void {
    this.desmarcadas.update((atuais) => {
      const novas = new Set(atuais);
      if (!novas.delete(regraId)) {
        novas.add(regraId);
      }
      return novas;
    });
  }

  cancelar(): void {
    this.referencia.close();
  }

  aplicar(): void {
    const aplicacao = this.aplicacao();
    if (this.enviando() || aplicacao === null || this.marcados() === 0) {
      return;
    }
    const regras = aplicacao.grupos.map((grupo) => grupo.regraId).filter((id) => this.marcado(id));
    this.enviando.set(true);
    this.erro.set(null);
    this.recarregada.set(false);
    this.referencia.disableClose = true;
    this.servico.aplicar(regras).subscribe({
      next: (categorizados) => this.referencia.close(categorizados),
      error: (erro: unknown) => {
        const status = statusDoErro(erro);
        this.erro.set(status === 400 || status === 422 ? detalheDoErro(erro, FALHA) : FALHA);
        this.enviando.set(false);
        this.referencia.disableClose = false;
        if (codigoDoErro(erro) === 'REGRA_INVALIDA') {
          this.carregarPrevia(true);
        }
      },
    });
  }
}

/** Abre a prévia (tela cheia no celular); emite quantos lançamentos ganharam categoria, ou undefined. */
export function abrirAplicarRegras(dialogo: MatDialog, regraId: number | null): Observable<number | undefined> {
  return dialogo.open<AplicarDialogo, DadosDoAplicar, number>(AplicarDialogo, {
    data: { regraId },
    panelClass: ['flux-dialogo', 'flux-dialogo--cheio-no-celular'],
    width: '540px',
    maxWidth: 'calc(100vw - 32px)',
    maxHeight: 'calc(100vh - 32px)',
    autoFocus: 'first-heading',
  }).afterClosed();
}
