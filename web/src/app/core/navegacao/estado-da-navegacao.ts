import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';

/**
 * Lê o `state` que outra tela do app passou em `router.navigate(..., { state })`.
 *
 * O Angular também guarda esse `state` no `history.state` e o devolve na navegação
 * inicial (recarregar a página, abrir o endereço direto) e no Voltar/Avançar. Nesses
 * casos o valor é ignorado de propósito: o e-mail digitado não volta depois de
 * recarregar (escolha 6 do mockup) e um aviso como "Senha redefinida." aparece uma vez só.
 */
@Injectable({ providedIn: 'root' })
export class EstadoDaNavegacao {
  private readonly router = inject(Router);

  texto(chave: string): string | null {
    const navegacao = this.router.currentNavigation();
    if (navegacao === null || navegacao.previousNavigation === null || navegacao.trigger !== 'imperative') {
      return null;
    }
    const valor: unknown = navegacao.extras.state?.[chave];
    return typeof valor === 'string' && valor !== '' ? valor : null;
  }
}
