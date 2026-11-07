import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { CabecalhoPagina } from './cabecalho-pagina';

@Component({
  selector: 'app-teste-cabecalho-pagina',
  imports: [CabecalhoPagina],
  template: '<app-cabecalho-pagina titulo="Início" [subtitulo]="subtitulo()"><button type="button">Ação</button></app-cabecalho-pagina>',
})
class TesteCabecalhoPagina {
  readonly subtitulo = signal<string | undefined>(undefined);
}

describe('CabecalhoPagina', () => {
  it('mostra título, subtítulo opcional e ação projetada', () => {
    const fixture = TestBed.createComponent(TesteCabecalhoPagina);
    fixture.detectChanges();
    const elemento = fixture.nativeElement as HTMLElement;
    expect(elemento.querySelector('h1')?.textContent).toBe('Início');
    expect(elemento.querySelector('.cabecalho__subtitulo')).toBeNull();
    expect(elemento.querySelector('.cabecalho__acoes button')?.textContent).toBe('Ação');

    fixture.componentInstance.subtitulo.set('Hoje');
    fixture.detectChanges();
    expect(elemento.querySelector('.cabecalho__subtitulo')?.textContent).toBe('Hoje');
  });
});
