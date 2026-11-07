import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { EstadoVazio } from './estado-vazio';

@Component({
  selector: 'app-teste-estado-vazio',
  imports: [EstadoVazio],
  template: '<app-estado-vazio icone="carteira" titulo="Sem dados" [texto]="texto()" [nivel]="nivel()"><button type="button">Criar</button></app-estado-vazio>',
})
class TesteEstadoVazio {
  readonly texto = signal<string | undefined>(undefined);
  readonly nivel = signal<2 | 3>(2);
}

describe('EstadoVazio', () => {
  it('exibe os conteúdos opcionais e o nível de título', () => {
    const fixture = TestBed.createComponent(TesteEstadoVazio);
    fixture.detectChanges();
    const elemento = fixture.nativeElement as HTMLElement;
    const estado = elemento.querySelector('app-estado-vazio');
    expect(estado?.querySelector('h2')?.textContent).toBe('Sem dados');
    expect(estado?.querySelector('.estado-vazio__texto')).toBeNull();
    expect(estado?.querySelector('button')?.textContent).toBe('Criar');
    expect(estado?.classList).toContain('flux-card');
    expect(estado?.classList).toContain('flux-estado-vazio');
    expect(estado?.querySelector('.estado-vazio__selo')?.getAttribute('aria-hidden')).toBe('true');

    fixture.componentInstance.texto.set('Texto de apoio');
    fixture.componentInstance.nivel.set(3);
    fixture.detectChanges();
    expect(estado?.querySelector('h3')?.textContent).toBe('Sem dados');
    expect(estado?.querySelector('.estado-vazio__texto')?.textContent).toBe('Texto de apoio');
  });
});
