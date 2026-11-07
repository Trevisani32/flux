import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { ConfiguracaoService } from './configuracao.service';

/** /criar-conta só abre com o cadastro público ligado; senão, volta para o Entrar. */
export const cadastroLigadoGuard: CanActivateFn = () => {
  const configuracao = inject(ConfiguracaoService);
  const router = inject(Router);
  return configuracao.garantir().then((valor) => valor.cadastroPublico ? true : router.createUrlTree(['/entrar']));
};
