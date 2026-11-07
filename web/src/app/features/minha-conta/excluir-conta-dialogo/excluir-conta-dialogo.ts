import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';

import { Alerta } from '../../../shared/alerta/alerta';
import { Icone } from '../../../shared/icone/icone';
import { MENSAGEM_MUITAS_TENTATIVAS, detalheDoErro, statusDoErro } from '../../auth/erros-http';
import { MinhaContaService } from '../minha-conta.service';

export interface DadosDaExclusao {
  readonly email: string;
}

/** Diálogo "Excluir sua conta?". Fecha com true depois do 204 da API. */
@Component({
  selector: 'app-excluir-conta-dialogo',
  imports: [MatButtonModule, MatDialogModule, ReactiveFormsModule, Alerta, Icone],
  templateUrl: './excluir-conta-dialogo.html',
  styleUrl: './excluir-conta-dialogo.scss',
})
export class ExcluirContaDialogo {
  private readonly servico = inject(MinhaContaService);
  private readonly referencia = inject<MatDialogRef<ExcluirContaDialogo, boolean>>(MatDialogRef);

  readonly dados = inject<DadosDaExclusao>(MAT_DIALOG_DATA);
  readonly senha = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  /** O [formGroup] no <form> é o que faz o (ngSubmit) funcionar e impede a submissão nativa. */
  readonly formulario = new FormGroup({ senha: this.senha });
  readonly senhaVisivel = signal(false);
  readonly enviando = signal(false);
  readonly erroSenha = signal<string | null>(null);
  readonly erro = signal<{ readonly icone: 'alerta' | 'relogio'; readonly texto: string } | null>(null);

  excluir(): void {
    if (this.senha.invalid || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.erroSenha.set(null);
    this.erro.set(null);
    this.servico.excluir(this.senha.value).subscribe({
      next: () => this.referencia.close(true),
      error: (erro: unknown) => {
        const status = statusDoErro(erro);
        if (status === 422) {
          this.erroSenha.set(detalheDoErro(erro, 'Senha atual incorreta.'));
        } else {
          this.erro.set(status === 429
            ? { icone: 'relogio', texto: MENSAGEM_MUITAS_TENTATIVAS }
            : { icone: 'alerta', texto: 'Não foi possível excluir a conta. Tente novamente.' });
        }
        this.enviando.set(false);
      },
    });
  }

  alternarSenha(): void {
    this.senhaVisivel.update((visivel) => !visivel);
  }
}
