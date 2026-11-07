import { Location } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { Icone } from '../../../shared/icone/icone';
import { ContaPublicaService } from '../conta-publica.service';
import { statusDoErro } from '../erros-http';
import { LayoutPublico } from '../layout-publico/layout-publico';
import { tokenDoFragmento } from '../token-do-fragmento';

export type EstadoDaConfirmacao =
  | { readonly tipo: 'confirmando' }
  | { readonly tipo: 'cadastro' }
  | { readonly tipo: 'troca'; readonly email: string }
  | { readonly tipo: 'expirado' }
  | { readonly tipo: 'falha' };

/** Lê o token do fragmento (/confirmar-email#token=...), tira o token da URL e confirma. Não faz login. */
@Component({
  selector: 'app-confirmar-email',
  imports: [MatButtonModule, RouterLink, Icone, LayoutPublico],
  templateUrl: './confirmar-email.html',
})
export class ConfirmarEmail {
  readonly estado = signal<EstadoDaConfirmacao>({ tipo: 'confirmando' });
  readonly emailDaTroca = computed(() => {
    const atual = this.estado();
    return atual.tipo === 'troca' ? atual.email : '';
  });

  constructor() {
    const token = tokenDoFragmento(inject(ActivatedRoute).snapshot.fragment);
    inject(Location).replaceState('/confirmar-email');
    if (token === null) {
      this.estado.set({ tipo: 'expirado' });
      return;
    }
    inject(ContaPublicaService).confirmarEmail(token).pipe(takeUntilDestroyed()).subscribe({
      next: (resposta) => this.estado.set(resposta.tipo === 'TROCA_DE_EMAIL'
        ? { tipo: 'troca', email: resposta.email }
        : { tipo: 'cadastro' }),
      error: (erro: unknown) => {
        const status = statusDoErro(erro);
        this.estado.set(status === 400 || status === 422 ? { tipo: 'expirado' } : { tipo: 'falha' });
      },
    });
  }
}
