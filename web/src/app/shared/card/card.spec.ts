import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { Card } from './card';

@Component({
  selector: 'app-teste-card',
  imports: [Card],
  template: '<app-card [titulo]="titulo()" [nivel]="nivel()"><a acao href="/contas">Ver todas</a><p>Conteúdo</p></app-card>',
})
class TesteCard {
  readonly titulo = signal<string | undefined>(undefined);
  readonly nivel = signal<2 | 3>(2);
}

describe('Card', () => {
  it('só mostra cabeçalho quando há título e respeita o nível', () => {
    const fixture = TestBed.createComponent(TesteCard);
    fixture.detectChanges();
    const elemento = fixture.nativeElement as HTMLElement;
    expect(elemento.querySelector('.card__cabecalho')).toBeNull();
    expect(elemento.querySelector('h2, h3')).toBeNull();
    expect(elemento.querySelector('p')?.textContent).toBe('Conteúdo');

    fixture.componentInstance.titulo.set('Contas');
    fixture.detectChanges();
    expect(elemento.querySelector('h2')?.textContent).toBe('Contas');
    expect(elemento.querySelector('.card__cabecalho a')?.textContent).toBe('Ver todas');
    expect(elemento.querySelector('app-card')?.classList).toContain('flux-card');

    fixture.componentInstance.nivel.set(3);
    fixture.detectChanges();
    expect(elemento.querySelector('h3')?.textContent).toBe('Contas');
  });
});
