import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatMenuModule } from '@angular/material/menu';
import { describe, expect, it } from 'vitest';

import { BotaoUsuario } from './botao-usuario';
import { inicialDoNome } from './inicial';

@Component({
  selector: 'app-teste-botao-usuario',
  imports: [MatMenuModule, BotaoUsuario],
  template: '<mat-menu #m="matMenu"></mat-menu><app-botao-usuario [menu]="m" nome="Dev" email="dev@flux.local" [compacto]="compacto()" />',
})
class TesteBotaoUsuario {
  readonly compacto = signal(false);
}

describe('BotaoUsuario', () => {
  it.each([
    ['Dev', 'D'],
    ['  ágata', 'Á'],
    ['', ''],
    ['   ', ''],
  ])('encontra a inicial de %s', (nome, esperado) => {
    expect(inicialDoNome(nome)).toBe(esperado);
  });

  it('mantém o nome acessível no texto visível e alterna o modo compacto', () => {
    const fixture = TestBed.createComponent(TesteBotaoUsuario);
    fixture.detectChanges();
    const elemento = fixture.nativeElement as HTMLElement;
    const botao = elemento.querySelector<HTMLButtonElement>('.usuario');
    expect(elemento.querySelector('.usuario__nome')?.textContent).toBe('Dev');
    expect(elemento.querySelector('.usuario__email')?.textContent).toBe('dev@flux.local');
    expect(elemento.querySelector('.usuario__avatar')?.textContent).toBe('D');
    expect(elemento.querySelector('.usuario__avatar')?.getAttribute('aria-hidden')).toBe('true');
    expect(botao?.hasAttribute('aria-label')).toBe(false);
    expect(botao?.getAttribute('aria-haspopup')).toBe('menu');

    fixture.componentInstance.compacto.set(true);
    fixture.detectChanges();
    expect(botao?.classList).toContain('usuario--compacto');
    expect(elemento.querySelector('.usuario__email')).toBeNull();
  });
});
