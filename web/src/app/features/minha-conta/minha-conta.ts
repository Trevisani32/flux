import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { Alerta } from '../../shared/alerta/alerta';
import { CabecalhoPagina } from '../../shared/cabecalho-pagina/cabecalho-pagina';
import { dataDaVersao, dataHoraCurta } from '../../shared/datas/data-hora';
import { Icone } from '../../shared/icone/icone';
import { textoObrigatorio } from '../../shared/validadores/validadores';
import { MENSAGEM_MUITAS_TENTATIVAS, detalheDoErro, statusDoErro } from '../auth/erros-http';
import { ExcluirContaDialogo } from './excluir-conta-dialogo/excluir-conta-dialogo';
import { Conta, MinhaContaService } from './minha-conta.service';

@Component({
  selector: 'app-minha-conta',
  imports: [MatButtonModule, ReactiveFormsModule, RouterLink, Alerta, CabecalhoPagina, Icone],
  templateUrl: './minha-conta.html',
  styleUrl: './minha-conta.scss',
})
export class MinhaConta {
  private readonly servico = inject(MinhaContaService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly dialogo = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly formularioBuilder = inject(FormBuilder).nonNullable;

  readonly dataHoraCurta = dataHoraCurta;
  readonly dataDaVersao = dataDaVersao;
  readonly conta = signal<Conta | null>(null);
  readonly falhaAoCarregar = signal(false);
  readonly salvandoNome = signal(false);
  readonly erroNomeEnvio = signal<string | null>(null);
  readonly enviandoEmail = signal(false);
  readonly erroEmailEnvio = signal<{ readonly icone: 'alerta' | 'relogio'; readonly texto: string } | null>(null);
  readonly senhaVisivel = signal(false);
  readonly formularioNome = this.formularioBuilder.group({
    nome: ['', [textoObrigatorio, Validators.maxLength(120)]],
  });
  readonly formularioEmail = this.formularioBuilder.group({
    novoEmail: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    senhaAtual: ['', Validators.required],
  });

  constructor() {
    this.carregar();
  }

  carregar(): void {
    this.falhaAoCarregar.set(false);
    this.servico.carregar().subscribe({
      next: (conta) => {
        this.aplicar(conta);
        this.formularioNome.setValue({ nome: conta.nome });
      },
      error: () => this.falhaAoCarregar.set(true),
    });
  }

  salvarNome(): void {
    if (this.formularioNome.invalid || this.salvandoNome()) {
      this.formularioNome.markAllAsTouched();
      return;
    }
    this.salvandoNome.set(true);
    this.erroNomeEnvio.set(null);
    this.servico.renomear(this.formularioNome.getRawValue().nome.trim()).subscribe({
      next: (conta) => {
        this.aplicar(conta);
        this.formularioNome.setValue({ nome: conta.nome });
        this.snackBar.open('Nome salvo.', 'Fechar', { duration: 5000 });
        this.salvandoNome.set(false);
      },
      error: () => {
        this.erroNomeEnvio.set('Não foi possível salvar o nome. Tente novamente.');
        this.salvandoNome.set(false);
      },
    });
  }

  pedirTrocaDeEmail(): void {
    if (this.formularioEmail.invalid || this.enviandoEmail()) {
      this.formularioEmail.markAllAsTouched();
      return;
    }
    this.enviandoEmail.set(true);
    this.erroEmailEnvio.set(null);
    const { novoEmail, senhaAtual } = this.formularioEmail.getRawValue();
    this.servico.pedirTrocaDeEmail(novoEmail.trim(), senhaAtual).subscribe({
      next: (conta) => {
        this.aplicar(conta);
        this.formularioEmail.reset();
        this.enviandoEmail.set(false);
      },
      error: (erro: unknown) => {
        const status = statusDoErro(erro);
        this.erroEmailEnvio.set(status === 429
          ? { icone: 'relogio', texto: MENSAGEM_MUITAS_TENTATIVAS }
          : { icone: 'alerta', texto: status === 422 ? detalheDoErro(erro, 'Não foi possível pedir a troca. Tente novamente.') : 'Não foi possível pedir a troca. Tente novamente.' });
        this.enviandoEmail.set(false);
      },
    });
  }

  abrirExclusao(): void {
    const conta = this.conta();
    if (conta === null) {
      return;
    }
    this.dialogo.open<ExcluirContaDialogo, { email: string }, boolean>(ExcluirContaDialogo, {
      data: { email: conta.email },
      panelClass: 'flux-dialogo-perigo',
      autoFocus: '#excluir-conta-senha',
      width: '460px',
      maxWidth: 'calc(100vw - 32px)',
    }).afterClosed().subscribe((excluida) => {
      if (excluida === true) {
        this.auth.limparSessao();
        void this.router.navigate(['/entrar'], { state: { aviso: 'conta-excluida' } });
      }
    });
  }

  alternarSenha(): void {
    this.senhaVisivel.update((visivel) => !visivel);
  }

  erroNome(): string | null {
    const controle = this.formularioNome.controls.nome;
    if (!controle.touched) {
      return null;
    }
    if (controle.hasError('required')) {
      return 'Informe o nome.';
    }
    return controle.hasError('maxlength') ? 'O nome pode ter no máximo 120 caracteres.' : null;
  }

  erroNovoEmail(): string | null {
    const controle = this.formularioEmail.controls.novoEmail;
    if (!controle.touched) {
      return null;
    }
    if (controle.hasError('required')) {
      return 'Informe o novo e-mail.';
    }
    if (controle.hasError('maxlength')) {
      return 'O e-mail pode ter no máximo 255 caracteres.';
    }
    return controle.hasError('email') ? 'Informe um e-mail válido.' : null;
  }

  erroSenhaAtual(): string | null {
    const controle = this.formularioEmail.controls.senhaAtual;
    return controle.touched && controle.hasError('required') ? 'Informe a senha atual.' : null;
  }

  private aplicar(conta: Conta): void {
    this.conta.set(conta);
    this.auth.atualizarUsuario({ id: conta.id, nome: conta.nome, email: conta.email });
  }
}
