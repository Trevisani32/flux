import { Location } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';
import { Alerta } from '../../../shared/alerta/alerta';
import { Icone } from '../../../shared/icone/icone';
import { senhasIguais } from '../../../shared/validadores/validadores';
import { ContaPublicaService } from '../conta-publica.service';
import { MENSAGEM_MUITAS_TENTATIVAS, codigoDoErro, detalheDoErro, statusDoErro } from '../erros-http';
import { LayoutPublico } from '../layout-publico/layout-publico';
import { tokenDoFragmento } from '../token-do-fragmento';

/** Lê o token do fragmento (/redefinir-senha#token=...) e tira o token da URL. O link só é conferido ao salvar. */
@Component({
  selector: 'app-redefinir-senha',
  imports: [MatButtonModule, ReactiveFormsModule, RouterLink, Alerta, Icone, LayoutPublico],
  templateUrl: './redefinir-senha.html',
})
export class RedefinirSenha {
  private readonly contaPublica = inject(ContaPublicaService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly token = tokenDoFragmento(inject(ActivatedRoute).snapshot.fragment);

  readonly expirado = signal(this.token === null);
  readonly enviando = signal(false);
  readonly senhaVisivel = signal(false);
  readonly erro = signal<{ readonly icone: 'alerta' | 'relogio'; readonly texto: string } | null>(null);
  readonly formulario = inject(FormBuilder).nonNullable.group({
    novaSenha: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(72)]],
    confirmarNovaSenha: ['', Validators.required],
  }, { validators: senhasIguais });

  constructor() {
    inject(Location).replaceState('/redefinir-senha');
  }

  salvar(): void {
    if (this.token === null) {
      this.expirado.set(true);
      return;
    }
    if (this.formulario.invalid || this.enviando()) {
      this.formulario.markAllAsTouched();
      return;
    }
    this.enviando.set(true);
    this.erro.set(null);
    this.contaPublica.redefinirSenha(this.token, this.formulario.getRawValue().novaSenha).subscribe({
      next: () => {
        this.auth.limparSessao();
        void this.router.navigate(['/entrar'], { state: { aviso: 'senha-redefinida' } });
      },
      error: (erro: unknown) => {
        this.enviando.set(false);
        const status = statusDoErro(erro);
        if (status === 422 && codigoDoErro(erro) === 'LINK_INVALIDO') {
          this.expirado.set(true);
        } else if (status === 429) {
          this.erro.set({ icone: 'relogio', texto: MENSAGEM_MUITAS_TENTATIVAS });
        } else {
          this.erro.set({
            icone: 'alerta',
            texto: status === 422
              ? detalheDoErro(erro, 'Não foi possível salvar a senha. Tente novamente.')
              : 'Não foi possível salvar a senha. Tente novamente.',
          });
        }
      },
    });
  }

  alternarSenha(): void {
    this.senhaVisivel.update((visivel) => !visivel);
  }

  erroNovaSenha(): string | null {
    const controle = this.formulario.controls.novaSenha;
    return controle.touched && controle.invalid ? 'A nova senha precisa ter entre 10 e 72 caracteres.' : null;
  }

  erroConfirmacao(): string | null {
    return this.formulario.controls.confirmarNovaSenha.touched && this.formulario.hasError('senhasDiferentes')
      ? 'As senhas não conferem.'
      : null;
  }
}
