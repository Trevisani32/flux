import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, tap } from 'rxjs';

import { Alerta } from '../../../shared/alerta/alerta';
import { abrirConfirmacao } from '../../../shared/confirmacao/confirmacao-dialogo';
import { Icone } from '../../../shared/icone/icone';
import { escreverValorDigitado } from '../../../shared/moeda/valor-digitado';
import { camposDoErro, detalheDoErro, statusDoErro } from '../../auth/erros-http';
import { metaDoLado, observacaoDosLados, paragrafosDoDesfazer } from '../conversao';
import { ItemDaLista, LancamentosService } from '../lancamentos.service';

export interface DadosDaConvertida {
  /** A transferência convertida, com os `lados` (a saída primeiro). */
  readonly item: ItemDaLista;
}

/**
 * Como o diálogo fecha quando algo mudou: "salvo", "desfeita" ou "desatualizada" (a API disse que a
 * transferência sumiu ou ficou só leitura em outra aba; quem abriu recarrega). Sem mudança, sem resultado.
 */
export type ResultadoDaConvertida = 'salvo' | 'desfeita' | 'desatualizada';

type CampoDaConvertida = 'descricao' | 'observacao';

const FALHA = 'Não foi possível salvar. Tente novamente.';

/**
 * A transferência convertida (decisões 31 a 33): valor, contas e a data de cada lado travados, com o
 * "No extrato" de cada importado; mudam só a descrição e a observação, nos dois lados. No lugar de
 * "Excluir", "Desfazer transferência", com confirmação. A observação mostra a dos dois lados (decisão 29:
 * cada lado ficou com a sua), e salvar grava o texto nos dois.
 */
@Component({
  selector: 'app-convertida-dialogo',
  imports: [MatButtonModule, MatDialogModule, ReactiveFormsModule, Alerta, Icone],
  templateUrl: './convertida-dialogo.html',
  styleUrl: './convertida-dialogo.scss',
})
export class ConvertidaDialogo {
  private readonly servico = inject(LancamentosService);
  private readonly referencia = inject<MatDialogRef<ConvertidaDialogo, ResultadoDaConvertida>>(MatDialogRef);
  private readonly dialogo = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly item = inject<DadosDaConvertida>(MAT_DIALOG_DATA).item;
  readonly lados = this.item.lados ?? [];
  readonly doisImportados = this.lados.every((lado) => lado.origem === 'IMPORTADO');
  readonly tituloDoAlerta = this.doisImportados ? 'Convertida de lançamentos importados' : 'Convertida de um lançamento importado';
  readonly textoDoAlerta = this.doisImportados ? 'Contas, datas e valor vêm dos extratos e não mudam.'
    : 'Contas, datas e valor não mudam numa transferência convertida.';
  readonly valor = escreverValorDigitado(this.item.valor);
  readonly metaDoLado = metaDoLado;
  readonly formulario = new FormGroup({
    descricao: new FormControl(this.item.descricao, { nonNullable: true }),
    observacao: new FormControl(observacaoDosLados(this.lados), { nonNullable: true }),
  });
  readonly enviando = signal(false);
  readonly erroGeral = signal<string | null>(null);
  readonly erros = signal<Partial<Record<CampoDaConvertida, string>>>({});
  /** Depois de um 404 ou 422 ao salvar ou ao desfazer: só "Fechar", que pede a recarga da lista. */
  readonly desatualizada = signal(false);

  constructor() {
    for (const campo of ['descricao', 'observacao'] as const) {
      this.formulario.controls[campo].valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
        this.erros.update(({ [campo]: removido, ...outros }) => {
          void removido;
          return outros;
        });
      });
    }
  }

  erro(campo: CampoDaConvertida): string | null {
    return this.erros()[campo] ?? null;
  }

  cancelar(): void {
    this.referencia.close(this.desatualizada() ? 'desatualizada' : undefined);
  }

  /** O pedido repete o valor, as contas e a data da saída (decisão 31): só a descrição e a observação mudam. */
  salvar(): void {
    if (this.enviando() || this.desatualizada()) {
      return;
    }
    this.erroGeral.set(null);
    const valores = this.formulario.getRawValue();
    const descricao = valores.descricao.trim();
    const observacao = valores.observacao.trim();
    if (observacao.length > 1000) {
      this.erros.set({ observacao: 'Use no máximo 1.000 caracteres.' });
      return;
    }
    this.erros.set({});
    const [saida, entrada] = this.lados;
    this.enviando.set(true);
    this.referencia.disableClose = true;
    this.servico.atualizarTransferencia(this.item.id, { valor: this.item.valor, data: saida.data, contaOrigemId: saida.contaId,
      contaDestinoId: entrada.contaId, descricao: descricao === '' ? null : descricao, observacao: observacao === '' ? null : observacao })
      .subscribe({
        next: () => {
          this.snackBar.open('Transferência salva.', 'Fechar', { duration: 4000 });
          this.referencia.close('salvo');
        },
        error: (erro: unknown) => {
          this.enviando.set(false);
          this.referencia.disableClose = false;
          this.tratarErro(erro);
        },
      });
  }

  /**
   * Decisão 32: confirmação por cima; desfeita, fecha com "desfeita". O 404 ou 422 do desfazer (outra aba
   * desfez a transferência ou arquivou uma conta) aparece na confirmação e deixa esta convertida desatualizada.
   */
  desfazer(): void {
    if (this.enviando() || this.desatualizada()) {
      return;
    }
    abrirConfirmacao(this.dialogo, {
      titulo: 'Desfazer a transferência?',
      icone: 'reativar',
      perigo: false,
      paragrafos: paragrafosDoDesfazer(this.item),
      confirmar: 'Desfazer transferência',
      falha: 'Não foi possível desfazer. Tente novamente.',
      acao: () => this.servico.desfazerTransferencia(this.item.id).pipe(tap({ error: (erro: unknown) => this.marcarDesatualizada(erro) })),
    }).subscribe((resultado) => {
      if (resultado === true) {
        this.snackBar.open('Transferência desfeita.', 'Fechar', { duration: 4000 });
        this.referencia.close('desfeita');
      }
    });
  }

  private tratarErro(erro: unknown): void {
    const status = statusDoErro(erro);
    if (status === 400) {
      const campos = camposDoErro(erro);
      const conhecidos: Partial<Record<CampoDaConvertida, string>> = {};
      if (campos['descricao'] !== undefined) {
        conhecidos.descricao = campos['descricao'];
      }
      if (campos['observacao'] !== undefined) {
        conhecidos.observacao = campos['observacao'];
      }
      this.erros.set(conhecidos);
      if (Object.keys(conhecidos).length === 0) {
        this.erroGeral.set('Confira os campos e tente de novo.');
      }
    } else if (!this.marcarDesatualizada(erro)) {
      this.erroGeral.set(FALHA);
    }
  }

  /** 404 ou 422: a transferência sumiu ou ficou só leitura em outra aba, e os campos não valem mais. */
  private marcarDesatualizada(erro: unknown): boolean {
    const status = statusDoErro(erro);
    if (status !== 404 && status !== 422) {
      return false;
    }
    this.erroGeral.set(detalheDoErro(erro, FALHA));
    this.desatualizada.set(true);
    return true;
  }
}

/** Abre a convertida; o Observable emite como ela fechou, ou undefined sem mudança. */
export function abrirConvertida(dialogo: MatDialog, dados: DadosDaConvertida): Observable<ResultadoDaConvertida | undefined> {
  return dialogo.open<ConvertidaDialogo, DadosDaConvertida, ResultadoDaConvertida>(ConvertidaDialogo, {
    data: dados,
    panelClass: 'flux-dialogo',
    width: '540px',
    maxWidth: 'calc(100vw - 32px)',
    maxHeight: 'calc(100vh - 32px)',
    autoFocus: 'first-heading',
  }).afterClosed();
}