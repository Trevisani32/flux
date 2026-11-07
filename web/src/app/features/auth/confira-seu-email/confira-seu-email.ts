import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { EstadoDaNavegacao } from '../../../core/navegacao/estado-da-navegacao';
import { Alerta } from '../../../shared/alerta/alerta';
import { Icone } from '../../../shared/icone/icone';
import { ContaPublicaService } from '../conta-publica.service';
import { MENSAGEM_MUITAS_TENTATIVAS, statusDoErro } from '../erros-http';
import { LayoutPublico } from '../layout-publico/layout-publico';

/**
 * "Confira seu e-mail" depois do cadastro (padrão) ou do "esqueci a senha"
 * (?tipo=senha). O e-mail vem só pelo state da navegação.
 */
@Component({
  selector: 'app-confira-seu-email',
  imports: [MatButtonModule, RouterLink, Alerta, Icone, LayoutPublico],
  templateUrl: './confira-seu-email.html',
})
export class ConfiraSeuEmail {
  private readonly contaPublica = inject(ContaPublicaService);

  readonly tipo: 'cadastro' | 'senha' = inject(ActivatedRoute).snapshot.queryParamMap.get('tipo') === 'senha'
    ? 'senha'
    : 'cadastro';
  readonly email = inject(EstadoDaNavegacao).texto('email');
  readonly enviando = signal(false);
  readonly reenviado = signal(false);
  readonly erro = signal<{ readonly icone: 'alerta' | 'relogio'; readonly texto: string } | null>(null);

  reenviar(): void {
    if (this.email === null || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.reenviado.set(false);
    this.erro.set(null);
    const pedido = this.tipo === 'senha'
      ? this.contaPublica.esqueciASenha(this.email)
      : this.contaPublica.reenviarConfirmacao(this.email);
    pedido.subscribe({
      next: () => {
        this.reenviado.set(true);
        this.enviando.set(false);
      },
      error: (erro: unknown) => {
        this.erro.set(statusDoErro(erro) === 429
          ? { icone: 'relogio', texto: MENSAGEM_MUITAS_TENTATIVAS }
          : { icone: 'alerta', texto: 'Não foi possível reenviar o link. Tente novamente.' });
        this.enviando.set(false);
      },
    });
  }
}
