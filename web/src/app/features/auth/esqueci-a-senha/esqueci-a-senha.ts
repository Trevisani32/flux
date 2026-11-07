import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { Router, RouterLink } from '@angular/router';

import { Alerta } from '../../../shared/alerta/alerta';
import { Icone } from '../../../shared/icone/icone';
import { ContaPublicaService } from '../conta-publica.service';
import { MENSAGEM_MUITAS_TENTATIVAS, statusDoErro } from '../erros-http';
import { LayoutPublico } from '../layout-publico/layout-publico';

@Component({
  selector: 'app-esqueci-a-senha',
  imports: [MatButtonModule, ReactiveFormsModule, RouterLink, Alerta, Icone, LayoutPublico],
  templateUrl: './esqueci-a-senha.html',
})
export class EsqueciASenha {
  private readonly contaPublica = inject(ContaPublicaService);
  private readonly router = inject(Router);

  readonly enviando = signal(false);
  readonly erro = signal<{ readonly icone: 'alerta' | 'relogio'; readonly texto: string } | null>(null);
  readonly formulario = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  enviar(): void {
    if (this.formulario.invalid || this.enviando()) {
      this.formulario.markAllAsTouched();
      return;
    }
    this.enviando.set(true);
    this.erro.set(null);
    const email = this.formulario.getRawValue().email.trim();
    this.contaPublica.esqueciASenha(email).subscribe({
      next: () => void this.router.navigate(['/confira-seu-email'], {
        queryParams: { tipo: 'senha' },
        state: { email: email.toLowerCase() },
      }),
      error: (erro: unknown) => {
        this.erro.set(statusDoErro(erro) === 429
          ? { icone: 'relogio', texto: MENSAGEM_MUITAS_TENTATIVAS }
          : { icone: 'alerta', texto: 'Não foi possível enviar o link. Tente novamente.' });
        this.enviando.set(false);
      },
    });
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
}
