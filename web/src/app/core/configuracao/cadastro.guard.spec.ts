import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';

import { ConfiguracaoService } from './configuracao.service';
import { cadastroLigadoGuard } from './cadastro.guard';

describe('cadastroLigadoGuard', () => {
  function configurar(cadastroPublico: boolean): void {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: ConfiguracaoService, useValue: { garantir: () => Promise.resolve({ cadastroPublico }) } },
      ],
    });
  }

  it('permite a rota quando o cadastro público está ligado', async () => {
    configurar(true);
    await expect(TestBed.runInInjectionContext(() => cadastroLigadoGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot))).resolves.toBe(true);
  });

  it('redireciona para entrar quando o cadastro público está desligado', async () => {
    configurar(false);
    const resultado = await TestBed.runInInjectionContext(() => cadastroLigadoGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
    expect(TestBed.inject(Router).serializeUrl(resultado as UrlTree)).toBe('/entrar');
  });
});
