import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { BarraHorizontal } from './barra-horizontal';

@Component({
  imports: [BarraHorizontal],
  template:
    '<app-barra-horizontal [valor]="valor()" [media]="media()" [maximo]="100" [variante]="variante()" />',
})
class Hospedeiro {
  readonly valor = signal(50);
  readonly media = signal<number | null>(25);
  readonly variante = signal<'principal' | 'sub' | 'sem'>('principal');
}

describe('BarraHorizontal', () => {
  it('a largura e o traço da média saem da mesma escala; sem média, sem traço', () => {
    const fixture = TestBed.createComponent(Hospedeiro);
    fixture.detectChanges();
    const elemento = fixture.nativeElement as HTMLElement;
    expect(elemento.querySelector<HTMLElement>('.barra__preenche')?.style.width).toBe('50%');
    expect(elemento.querySelector<HTMLElement>('.barra__media')?.style.left).toBe('25%');
    fixture.componentInstance.media.set(null);
    fixture.detectChanges();
    expect(elemento.querySelector('.barra__media')).toBeNull();
    fixture.componentInstance.variante.set('sem');
    fixture.detectChanges();
    expect(
      elemento.querySelector('app-barra-horizontal')?.classList.contains('grafico-barra--sem'),
    ).toBe(true);
    fixture.componentInstance.variante.set('sub');
    fixture.detectChanges();
    expect(
      elemento.querySelector('app-barra-horizontal')?.classList.contains('grafico-barra--sub'),
    ).toBe(true);
  });
});
