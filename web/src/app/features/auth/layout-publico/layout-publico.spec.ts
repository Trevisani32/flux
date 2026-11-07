import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';

import { LayoutPublico } from './layout-publico';

@Component({ imports: [LayoutPublico], template: '<app-layout-publico [compacto]="compacto"><p>conteúdo</p></app-layout-publico>' })
class Hospedeiro {
  compacto = false;
}

describe('LayoutPublico', () => {
  function criar(compacto = false): ReturnType<typeof TestBed.createComponent<Hospedeiro>> {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(Hospedeiro);
    fixture.componentInstance.compacto = compacto;
    fixture.detectChanges();
    return fixture;
  }

  it('projeta o conteúdo e mostra links públicos no rodapé', () => {
    const fixture = criar();
    const elemento = fixture.nativeElement as HTMLElement;
    expect(elemento.querySelector('.publico__lado > p')?.textContent).toBe('conteúdo');
    expect(elemento.querySelector('a[href="/termos-de-uso"]')?.textContent).toBe('Termos de uso');
    expect(elemento.querySelector('a[href="/privacidade"]')?.textContent).toBe('Privacidade');
  });

  it('passa compacto para o painel de marca', () => {
    const fixture = criar(true);
    expect((fixture.nativeElement as HTMLElement).querySelector('section.painel')?.className).toContain('painel--compacto');
  });
});
