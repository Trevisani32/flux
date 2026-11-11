import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, Subject, map, tap } from 'rxjs';

import { abrirAplicarRegras } from './aplicar-dialogo/aplicar-dialogo';
import { AVISO_SEM_CONTAGEM, avisoDaRegraCriada, avisoDosAplicados } from './apresentacao';
import { abrirDialogoDeRegra, ResultadoDaRegra } from './regra-dialogo/regra-dialogo';
import { EscolhaAMao, Regra, RegrasService, SugestaoDeRegra } from './regras.service';

/**
 * Os caminhos das regras que saem de várias telas (Regras, o resultado da importação, Lançamentos e o
 * diálogo do lançamento): criar, editar, aplicar e oferecer "Criar regra". As inscrições ficam aqui,
 * fora dos componentes: o aviso curto continua valendo depois que o diálogo que o abriu fechou.
 */
@Injectable({ providedIn: 'root' })
export class FluxoDeRegras {
  private readonly dialogo = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly servico = inject(RegrasService);
  private readonly aplicadas = new Subject<number>();

  /** Emite quantos lançamentos ganharam categoria, depois de "Aplicar": as listas abertas recarregam. */
  readonly aplicadas$ = this.aplicadas.asObservable();

  /**
   * "Nova regra", com a sugestão, se veio (decisão 21). Criada, o aviso curto diz quantos sem categoria
   * casam com ela e oferece "Aplicar" (decisão 19); se a contagem falhar, o aviso diz isso. Emite a regra
   * criada, ou undefined.
   */
  novaRegra(sugestao: SugestaoDeRegra | null = null): Observable<Regra | undefined> {
    return abrirDialogoDeRegra(this.dialogo, { regra: null, sugestao }).pipe(
      map((resultado) => resultado?.tipo === 'criada' ? resultado.regra : undefined),
      tap((regra) => {
        if (regra !== undefined) {
          this.avisarCriada(regra);
        }
      }),
    );
  }

  editar(regra: Regra): Observable<ResultadoDaRegra | undefined> {
    return abrirDialogoDeRegra(this.dialogo, { regra, sugestao: null }).pipe(
      tap((resultado) => {
        if (resultado !== undefined) {
          this.snackBar.open(resultado.tipo === 'excluida' ? 'Regra excluída.' : 'Regra salva.', 'Fechar', { duration: 4000 });
        }
      }),
    );
  }

  /** A prévia de "Aplicar regras" (com `regraId`, só a dela). Aplicada, avisa e emite em `aplicadas$`. */
  aplicar(regraId: number | null = null): Observable<number | undefined> {
    return abrirAplicarRegras(this.dialogo, regraId).pipe(
      tap((categorizados) => {
        if (categorizados !== undefined) {
          this.snackBar.open(avisoDosAplicados(categorizados), 'Fechar', { duration: 5000 });
          if (categorizados > 0) {
            this.aplicadas.next(categorizados);
          }
        }
      }),
    );
  }

  /**
   * Decisões 20 a 22: depois de salvar um importado que estava sem categoria e ganhou uma. Se a API
   * tiver uma sugestão, o aviso curto troca o "Lançamento salvo." por ela. A sugestão é opcional: se a
   * chamada falhar, fica o "Lançamento salvo." que já apareceu.
   */
  oferecerRegra(escolha: EscolhaAMao): void {
    this.servico.sugestoes([escolha]).subscribe({
      next: ([sugestao]) => {
        if (sugestao === undefined) {
          return;
        }
        this.snackBar.open(`Lançamento salvo. Criar uma regra para ${sugestao.texto}?`, 'Criar regra', { duration: 10000 })
          .onAction().subscribe(() => this.novaRegra(sugestao).subscribe());
      },
      error: () => undefined,
    });
  }

  private avisarCriada(regra: Regra): void {
    this.servico.aplicacao(regra.id).subscribe({
      next: (aplicacao) => {
        const aviso = this.snackBar.open(avisoDaRegraCriada(aplicacao.total), aplicacao.total > 0 ? 'Aplicar' : 'Fechar', { duration: 8000 });
        if (aplicacao.total > 0) {
          aviso.onAction().subscribe(() => this.aplicar(regra.id).subscribe());
        }
      },
      error: () => this.snackBar.open(AVISO_SEM_CONTAGEM, 'Fechar', { duration: 6000 }),
    });
  }
}
