import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-entrar',
  imports: [MatButtonModule, MatFormFieldModule, MatInputModule, ReactiveFormsModule],
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

  private redirecionar(): void {
    const retorno = this.rota.snapshot.queryParamMap.get('retorno');
    void this.router.navigateByUrl(retorno !== null && retorno.startsWith('/') && !retorno.startsWith('//')
      ? retorno
      : '/');
  }
}
