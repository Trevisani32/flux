import { Location } from '@angular/common';
import { provideLocationMocks } from '@angular/common/testing';
import { Component, inject } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, RouterOutlet, provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { EstadoDaNavegacao } from './estado-da-navegacao';

@Component({ selector: 'app-vazio', template: '' })
class Vazio {}

@Component({ selector: 'app-leitor', template: '' })
class Leitor {
  readonly email = inject(EstadoDaNavegacao).texto('email');
}

@Component({ selector: 'app-raiz-de-teste', imports: [RouterOutlet], template: '<router-outlet />' })
class RaizDeTeste {}

describe('EstadoDaNavegacao', () => {
  let router: Router;
  let location: Location;
  let fixture: ReturnType<typeof TestBed.createComponent<RaizDeTeste>>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: '', component: Vazio }, { path: 'alvo', component: Leitor }]),
        provideLocationMocks(),
      ],
    });
    router = TestBed.inject(Router);
    location = TestBed.inject(Location);
    fixture = TestBed.createComponent(RaizDeTeste);
    // O router-outlet precisa existir antes da navegação inicial, como no app de verdade.
    fixture.detectChanges();
  });

  function leitor(): Leitor {
    const encontrado = fixture.debugElement.query((elemento) => elemento.componentInstance instanceof Leitor);
    return encontrado.componentInstance as Leitor;
  }

  it('devolve o texto passado por outra tela do app', async () => {
    router.initialNavigation();
    await fixture.whenStable();
    await router.navigateByUrl('/alvo', { state: { email: 'ana@teste.local' } });
    fixture.detectChanges();
    expect(leitor().email).toBe('ana@teste.local');
  });

  it('ignora texto vazio, que não é texto e chave ausente', async () => {
    router.initialNavigation();
    await fixture.whenStable();
    await router.navigateByUrl('/alvo', { state: { email: '' } });
    expect(leitor().email).toBeNull();
    await router.navigateByUrl('/');
    await router.navigateByUrl('/alvo', { state: { email: 7 } });
    expect(leitor().email).toBeNull();
    await router.navigateByUrl('/');
    await router.navigateByUrl('/alvo');
    expect(leitor().email).toBeNull();
  });

  it('ignora o history.state na navegação inicial (recarregar a página)', async () => {
    location.go('/alvo', '', { email: 'ana@teste.local', navigationId: 3, ɵrouterPageId: 3 });
    router.initialNavigation();
    await fixture.whenStable();
    expect(location.path()).toBe('/alvo');
    expect(leitor().email).toBeNull();
  });

  it('ignora o history.state no Voltar do navegador', async () => {
    router.initialNavigation();
    await fixture.whenStable();
    await router.navigateByUrl('/alvo', { state: { email: 'ana@teste.local' } });
    await router.navigateByUrl('/');
    location.back();
    await vi.waitFor(() => expect(router.url).toBe('/alvo'));
    expect(leitor().email).toBeNull();
  });
});
