import { BreakpointObserver } from '@angular/cdk/layout';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { AuthService } from '../../core/auth/auth.service';
import { Usuario } from '../../core/auth/sessao';
import { Shell } from './shell';

describe('Shell', () => {
  function criar(matches: boolean): { componente: Shell; fixture: ReturnType<typeof TestBed.createComponent<Shell>>; auth: { usuario: ReturnType<typeof signal<Usuario | null>>; sair: ReturnType<typeof vi.fn> }; navegar: ReturnType<typeof vi.spyOn> } {
    const auth = {
      usuario: signal<Usuario | null>({ id: 1, nome: 'Dev', email: 'dev@flux.local' }),
      sair: vi.fn(() => of(undefined)),
    };
    TestBed.configureTestingModule({
      imports: [Shell],
      providers: [
        provideRouter([]),
        { provide: BreakpointObserver, useValue: { observe: () => of({ matches, breakpoints: {} }) } },
        { provide: AuthService, useValue: auth },
      ],
    });
    const router = TestBed.inject(Router);
    const navegar = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    return { componente: fixture.componentInstance, fixture, auth, navegar };
  }

  it('mostra a lateral fixa no desktop', () => {
    const { fixture } = criar(true);
    const elemento = fixture.nativeElement as HTMLElement;
    expect(elemento.querySelector('[aria-label="Abrir menu"]')).toBeNull();
    expect(elemento.querySelector('.usuario__email')?.textContent).toBe('dev@flux.local');
    expect(elemento.querySelector('#menu-principal')).not.toBeNull();
  });

  it('abre a gaveta no celular', async () => {
    const { fixture } = criar(false);
    const elemento = fixture.nativeElement as HTMLElement;
    const abrir = elemento.querySelector<HTMLButtonElement>('[aria-label="Abrir menu"]');
    expect(abrir?.getAttribute('aria-expanded')).toBe('false');
    expect(abrir?.getAttribute('aria-controls')).toBe('menu-principal');
    abrir?.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(abrir?.getAttribute('aria-expanded')).toBe('true');
    expect(elemento.querySelector('.usuario__email')).toBeNull();
  });

  it('sai e navega para entrar', () => {
    const { componente, auth, navegar } = criar(true);
    componente.sair();
    expect(auth.sair).toHaveBeenCalledOnce();
    expect(navegar).toHaveBeenCalledWith('/entrar');
  });
});
