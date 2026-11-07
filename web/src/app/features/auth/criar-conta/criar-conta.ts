import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { Router, RouterLink } from '@angular/router';

import { Alerta } from '../../../shared/alerta/alerta';
import { Icone } from '../../../shared/icone/icone';
import { textoObrigatorio } from '../../../shared/validadores/validadores';
import { ContaPublicaService } from '../conta-publica.service';
import { MENSAGEM_MUITAS_TENTATIVAS, detalheDoErro, statusDoErro } from '../erros-http';
import { LayoutPublico } from '../layout-publico/layout-publico';

@Component({
  selector: 'app-criar-conta',
  imports: [MatButtonModule, ReactiveFormsModule, RouterLink, Alerta, Icone, LayoutPublico],
  templateUrl: './criar-conta.html',
})
export class CriarConta {
  private readonly formularioBuilder = inject(FormBuilder).nonNullable;
  private readonly contaPublica = inject(ContaPublicaService);
  private readonly router = inject(Router);

  readonly enviando = signal(false);
  readonly erro = signal<{ readonly icone: 'alerta' | 'relogio'; readonly texto: string } | null>(null);
  readonly senhaVisivel = signal(false);
  readonly formulario = this.formularioBuilder.group({
    nome: ['', [textoObrigatorio, Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    senha: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(72)]],
    aceiteDosTermos: [false, Validators.requiredTrue],
  });

  criar(): void {
    if (this.formulario.invalid || this.enviando()) {
      this.formulario.markAllAsTouched();
      return;
    }
    this.enviando.set(true);
    this.erro.set(null);
    const { nome, email, senha } = this.formulario.getRawValue();
    this.contaPublica.cadastrar({ nome: nome.trim(), email: email.trim(), senha, aceiteDosTermos: true }).subscribe({
      next: () => void this.router.navigate(['/confira-seu-email'], { state: { email: email.trim().toLowerCase() } }),
      error: (erro: unknown) => {
        const status = statusDoErro(erro);
        this.erro.set(status === 429
          ? { icone: 'relogio', texto: MENSAGEM_MUITAS_TENTATIVAS }
          : { icone: 'alerta', texto: status === 422 ? detalheDoErro(erro, 'Não foi possível criar a conta. Tente novamente.') : 'Não foi possível criar a conta. Tente novamente.' });
        this.enviando.set(false);
      },
    });
  }

  alternarSenha(): void {
    this.senhaVisivel.update((visivel) => !visivel);
  }

  erroNome(): string | null {
    const controle = this.formulario.controls.nome;
    if (!controle.touched) {
      return null;
    }
    if (controle.hasError('required')) {
      return 'Informe o nome.';
    }
    return controle.hasError('maxlength') ? 'O nome pode ter no máximo 120 caracteres.' : null;
  }

  erroEmail(): string | null {
    const controle = this.formulario.controls.email;
    if (!controle.touched) {
      return null;
    }
    if (controle.hasError('required')) {
      return 'Informe o e-mail.';
    }
    if (controle.hasError('maxlength')) {
      return 'O e-mail pode ter no máximo 255 caracteres.';
    }
    return controle.hasError('email') ? 'Informe um e-mail válido.' : null;
  }

  erroSenha(): string | null {
    const controle = this.formulario.controls.senha;
    if (!controle.touched) {
      return null;
    }
    if (controle.hasError('required')) {
      return 'Informe a senha.';
    }
    return controle.invalid ? 'A senha precisa ter entre 10 e 72 caracteres.' : null;
  }

  erroAceite(): string | null {
    const controle = this.formulario.controls.aceiteDosTermos;
    return controle.touched && controle.invalid
      ? 'Aceite os termos de uso e a política de privacidade para continuar.'
      : null;
  }
}
