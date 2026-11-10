import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { Observable } from 'rxjs';

import { Alerta } from '../../../shared/alerta/alerta';
import { Icone } from '../../../shared/icone/icone';
import { textoObrigatorio } from '../../../shared/validadores/validadores';
import { camposDoErro, codigoDoErro, detalheDoErro, statusDoErro } from '../../auth/erros-http';
import { CategoriasService, Categoria, Natureza } from '../categorias.service';

export interface DadosDoDialogoDeCategoria {
  /** null = Nova categoria. */
  readonly categoria: Categoria | null;
  /** Natureza inicial: a da aba (nova) ou a da categoria (edição). */
  readonly natureza: Natureza;
  /** "Dentro de" inicial (Nova subcategoria, ou a principal atual na edição). */
  readonly maeId: number | null;
  /** As principais ativas de cada natureza, em ordem. */
  readonly principais: Readonly<Record<Natureza, readonly Categoria[]>>;
  /** Na edição de uma principal: ela tem subcategorias (ativas ou arquivadas) e não pode ir para dentro de outra. */
  readonly temSubcategorias: boolean;
}

/** "Nova categoria" e "Editar categoria". "Dentro de" decide se é principal ou subcategoria. */
@Component({
  selector: 'app-categoria-dialogo',
  imports: [MatButtonModule, MatDialogModule, ReactiveFormsModule, Alerta, Icone],
  templateUrl: './categoria-dialogo.html',
  styleUrl: './categoria-dialogo.scss',
})
export class CategoriaDialogo {
  private readonly servico = inject(CategoriasService);
  private readonly referencia = inject<MatDialogRef<CategoriaDialogo, Categoria>>(MatDialogRef);

  readonly dados = inject<DadosDoDialogoDeCategoria>(MAT_DIALOG_DATA);
  readonly editando = this.dados.categoria !== null;
  readonly formulario = new FormGroup({
    natureza: new FormControl<Natureza>({ value: this.dados.natureza, disabled: this.editando }, { nonNullable: true }),
    nome: new FormControl(this.dados.categoria?.nome ?? '', { nonNullable: true, validators: [textoObrigatorio, Validators.maxLength(60)] }),
    maeId: new FormControl<number | null>({ value: this.dados.maeId, disabled: this.dados.temSubcategorias }),
  });
  private readonly natureza = toSignal(this.formulario.controls.natureza.valueChanges, {
    initialValue: this.formulario.controls.natureza.value,
  });
  /** As principais que podem receber esta categoria: da mesma natureza e nunca ela mesma. */
  readonly opcoes = computed(() =>
    this.dados.principais[this.natureza()].filter((principal) => principal.id !== this.dados.categoria?.id));
  readonly enviando = signal(false);
  readonly erroGeral = signal<string | null>(null);
  readonly erroDoNomeDaApi = signal<string | null>(null);

  constructor() {
    this.formulario.controls.natureza.valueChanges.pipe(takeUntilDestroyed())
      .subscribe(() => this.formulario.controls.maeId.setValue(null));
    this.formulario.controls.nome.valueChanges.pipe(takeUntilDestroyed())
      .subscribe(() => this.erroDoNomeDaApi.set(null));
  }

  /** Fecha sem resultado (undefined), como o Esc e o clique fora. */
  cancelar(): void {
    this.referencia.close();
  }

  erroDoNome(): string | null {
    const daApi = this.erroDoNomeDaApi();
    if (daApi !== null) {
      return daApi;
    }
    const controle = this.formulario.controls.nome;
    if (!controle.touched || controle.valid) {
      return null;
    }
    return controle.hasError('required') ? 'Informe o nome.' : 'Use no máximo 60 caracteres.';
  }

  salvar(): void {
    if (this.enviando()) {
      return;
    }
    this.formulario.markAllAsTouched();
    if (this.formulario.invalid) {
      return;
    }
    const { natureza, nome, maeId } = this.formulario.getRawValue();
    this.enviando.set(true);
    // Esc e clique fora esperam a resposta: fechar agora perderia o recarregamento da lista.
    this.referencia.disableClose = true;
    this.erroGeral.set(null);
    this.erroDoNomeDaApi.set(null);
    const chamada: Observable<Categoria> = this.dados.categoria === null
      ? this.servico.criar({ nome: nome.trim(), natureza, categoriaMaeId: maeId })
      : this.servico.atualizar(this.dados.categoria.id, { nome: nome.trim(), categoriaMaeId: maeId });
    chamada.subscribe({
      next: (salva) => this.referencia.close(salva),
      error: (erro: unknown) => {
        const status = statusDoErro(erro);
        if (status === 400 && camposDoErro(erro)['nome'] !== undefined) {
          this.erroDoNomeDaApi.set(camposDoErro(erro)['nome']);
        } else if (status === 422 && codigoDoErro(erro) === 'NOME_EM_USO') {
          this.erroDoNomeDaApi.set(detalheDoErro(erro, 'Já existe uma categoria ativa com este nome.'));
        } else if (status === 422 || status === 404) {
          this.erroGeral.set(detalheDoErro(erro, 'Não foi possível salvar a categoria. Tente novamente.'));
        } else {
          this.erroGeral.set('Não foi possível salvar a categoria. Tente novamente.');
        }
        this.enviando.set(false);
        this.referencia.disableClose = false;
      },
    });
  }
}
