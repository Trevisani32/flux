import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';
import { ConfiguracaoService } from '../../../core/configuracao/configuracao.service';
import { EstadoDaNavegacao } from '../../../core/navegacao/estado-da-navegacao';
import { Alerta } from '../../../shared/alerta/alerta';
import { Icone } from '../../../shared/icone/icone';
import { ContaPublicaService } from '../conta-publica.service';
import { MENSAGEM_MUITAS_TENTATIVAS, codigoDoErro, statusDoErro } from '../erros-http';
import { LayoutPublico } from '../layout-publico/layout-publico';

/** Mensagem que vem de outra tela, pelo state da navegação. */
export type AvisoDoEntrar = 'senha-redefinida' | 'conta-excluida';

/** Mensagem que resulta do envio, mostrada acima do botão. */
export type MensagemDoEntrar =
  | { readonly tipo: 'credenciais' }
  | { readonly tipo: 'nao-confirmado'; readonly email: string }
  | { readonly tipo: 'reenviado'; readonly email: string }
  | { readonly tipo: 'muitas-tentativas' }
  | { readonly tipo: 'falha-reenvio' }
  | { readonly tipo: 'falha' };

@Component({
  selector: 'app-entrar',
  imports: [MatButtonModule, ReactiveFormsModule, RouterLink, Alerta, Icone, LayoutPublico],
  templateUrl: './entrar.html',
})
export class Entrar {
  private readonly formularioBuilder = inject(FormBuilder).nonNullable;
  private readonly auth = inject(AuthService);
  private readonly contaPublica = inject(ContaPublicaService);
  private readonly rota = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly muitasTentativas = MENSAGEM_MUITAS_TENTATIVAS;
  readonly cadastroPublico = inject(ConfiguracaoService).cadastroPublico;
  readonly aviso = signal<AvisoDoEntrar | null>(lerAviso(inject(EstadoDaNavegacao).texto('aviso')));
  readonly enviando = signal(false);
  readonly reenviando = signal(false);
  readonly mensagem = signal<MensagemDoEntrar | null>(null);
  /** No celular, a faixa escura fica só com a marca enquanto há o alerta de e-mail não confirmado (mockup 1117). */
  readonly faixaCompacta = computed(() => {
    const tipo = this.mensagem()?.tipo;
    return tipo === 'nao-confirmado' || tipo === 'reenviado';
  });
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
    this.mensagem.set(null);
    const { email, senha } = this.formulario.getRawValue();
    this.auth.entrar(email, senha).subscribe({
      next: () => this.redirecionar(),
      error: (erro: unknown) => {
        this.mensagem.set(this.mensagemDoErro(erro, email.trim().toLowerCase()));
        this.enviando.set(false);
      },
    });
  }

  reenviarLink(email: string): void {
    if (this.reenviando()) {
      return;
    }
    this.reenviando.set(true);
    this.contaPublica.reenviarConfirmacao(email).subscribe({
      next: () => {
        this.mensagem.set({ tipo: 'reenviado', email });
        this.reenviando.set(false);
      },
      error: (erro: unknown) => {
        this.mensagem.set(statusDoErro(erro) === 429 ? { tipo: 'muitas-tentativas' } : { tipo: 'falha-reenvio' });
        this.reenviando.set(false);
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

  private mensagemDoErro(erro: unknown, email: string): MensagemDoEntrar {
    switch (statusDoErro(erro)) {
      case 401:
        return { tipo: 'credenciais' };
      case 403:
        return codigoDoErro(erro) === 'EMAIL_NAO_CONFIRMADO' ? { tipo: 'nao-confirmado', email } : { tipo: 'falha' };
      case 429:
        return { tipo: 'muitas-tentativas' };
      default:
        return { tipo: 'falha' };
    }
  }

  private redirecionar(): void {
    const retorno = this.rota.snapshot.queryParamMap.get('retorno');
    void this.router.navigateByUrl(retorno !== null && retorno.startsWith('/') && !retorno.startsWith('//')
      ? retorno
      : '/');
  }
}

function lerAviso(valor: string | null): AvisoDoEntrar | null {
  return valor === 'senha-redefinida' || valor === 'conta-excluida' ? valor : null;
}
