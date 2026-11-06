import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthService } from './auth.service';

export const JA_REPETIDA = new HttpContextToken<boolean>(() => false);

export const authInterceptor: HttpInterceptorFn = (requisicao, next) => {
  if (!requisicao.url.startsWith('/api/') || requisicao.url.startsWith('/api/auth/')) {
    return next(requisicao);
  }

  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.tokenAcesso();
  const comAutorizacao = token === null
    ? requisicao
    : requisicao.clone({ setHeaders: { Authorization: `Bearer ${token}` } });

  return next(comAutorizacao).pipe(catchError((erro: unknown) => {
    if (!(erro instanceof HttpErrorResponse) || erro.status !== 401 || requisicao.context.get(JA_REPETIDA)) {
      return throwError(() => erro);
    }
    return auth.renovar().pipe(
      switchMap(() => {
        const novoToken = auth.tokenAcesso();
        const repetida = requisicao.clone({
          context: requisicao.context.set(JA_REPETIDA, true),
          setHeaders: novoToken === null ? {} : { Authorization: `Bearer ${novoToken}` },
        });
        return next(repetida);
      }),
      catchError(() => {
        auth.limparSessao();
        void router.navigate(['/entrar'], { queryParams: { retorno: router.url } });
        return throwError(() => erro);
      }),
    );
  }));
};
