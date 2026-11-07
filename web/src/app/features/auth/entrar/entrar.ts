import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, Router } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';
import { Icone } from '../../../shared/icone/icone';
import { PainelMarca } from './painel-marca/painel-marca';

@Component({
  selector: 'app-entrar',
  imports: [MatButtonModule, ReactiveFormsModule, Icone, PainelMarca],
  templateUrl: './entrar.html',
  styleUrl: './entrar.scss',
})
export class Entrar {
  private readonly formularioBuilder = inject(FormBuilder).nonNullable;
  private readonly auth = inject(AuthService);
  private readonly rota = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly enviando = signal(false);
  readonly erro = signal<string | null>(null);
  readonly senhaVisivel = signal(false);
  readonly formulario = this.formularioBuilder.group({
    email: ['', [Validators.required, Validators.email]],
    senha: ['', Validators.required],
  });

  entrar(): void {
    if (this.formulario.invalid || this.enviando()) {
      this.formulario.markAllAsTouched();
      return;
    }
    this.enviando.set(true);
    this.erro.set(null);
    const { email, senha } = this.formulario.getRawValue();
    this.auth.entrar(email, senha).subscribe({
      next: () => this.redirecionar(),
      error: (erro: unknown) => {
        this.erro.set(erro instanceof HttpErrorResponse && erro.status === 401
          ? 'E-mail ou senha inválidos'
          : 'Não foi possível entrar. Tente novamente.');
        this.enviando.set(false);
      },
    });
  }

  alternarSenha(): void {
    this.senhaVisivel.update((visivel) => !visivel);
  }

  erroEmail(): string | null {
    const controle = this.formulario.controls.email;
    if (!controle.touched) {
      return null;
    }
    if (controle.hasError('required')) {
      return 'Informe o e-mail.';
    }
    return controle.hasError('email') ? 'Informe um e-mail válido.' : null;
  }

  erroSenha(): string | null {
    const controle = this.formulario.controls.senha;
    return controle.touched && controle.hasError('required') ? 'Informe a senha.' : null;
  }

  private redirecionar(): void {
    const retorno = this.rota.snapshot.queryParamMap.get('retorno');
    void this.router.navigateByUrl(retorno !== null && retorno.startsWith('/') && !retorno.startsWith('//')
      ? retorno
      : '/');
  }
}
