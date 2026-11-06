import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';

function senhasIguais(controle: AbstractControl): ValidationErrors | null {
  const { novaSenha, confirmarNovaSenha } = controle.value as { novaSenha?: string; confirmarNovaSenha?: string };
  const confirmacao = controle.get('confirmarNovaSenha');
  if (novaSenha !== confirmarNovaSenha) {
    if (!confirmacao?.hasError('senhasDiferentes')) {
      confirmacao?.setErrors({ ...confirmacao.errors, senhasDiferentes: true });
    }
    return { senhasDiferentes: true };
  }
  if (confirmacao?.hasError('senhasDiferentes')) {
    const { senhasDiferentes, ...demaisErros } = confirmacao.errors ?? {};
    void senhasDiferentes;
    confirmacao.setErrors(Object.keys(demaisErros).length === 0 ? null : demaisErros);
  }
  return null;
}

@Component({
  selector: 'app-trocar-senha',
  imports: [MatButtonModule, MatFormFieldModule, MatInputModule, ReactiveFormsModule],
  templateUrl: './trocar-senha.html',
  styleUrl: './trocar-senha.scss',
})
export class TrocarSenha {
  private readonly formularioBuilder = inject(FormBuilder).nonNullable;
  private readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  readonly erro = signal<string | null>(null);
  readonly formulario = this.formularioBuilder.group({
    senhaAtual: ['', Validators.required],
    novaSenha: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(72)]],
    confirmarNovaSenha: ['', Validators.required],
  }, { validators: senhasIguais });

  salvar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    this.erro.set(null);
    const { senhaAtual, novaSenha } = this.formulario.getRawValue();
    this.http.put<void>('/api/usuarios/me/senha', { senhaAtual, novaSenha }).subscribe({
      next: () => {
        this.auth.limparSessao();
        this.snackBar.open('Senha alterada. Entre novamente.', 'Fechar', { duration: 5000 });
        void this.router.navigateByUrl('/entrar');
      },
      error: (erro: unknown) => {
        this.erro.set(erro instanceof HttpErrorResponse && erro.status === 422
          ? (erro.error?.detail ?? 'Não foi possível trocar a senha. Tente novamente.')
          : 'Não foi possível trocar a senha. Tente novamente.');
      },
    });
  }
}
