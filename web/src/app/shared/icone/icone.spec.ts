import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { Icone } from './icone';
import { ICONES } from './icones';

describe('Icone', () => {
  it('desenha os traços e aplica o tamanho configurado', () => {
    const fixture = TestBed.createComponent(Icone);
    fixture.componentRef.setInput('nome', 'olho');
    fixture.detectChanges();

    const elemento = fixture.nativeElement as HTMLElement;
    const caminhos = elemento.querySelectorAll('path');
    expect(caminhos).toHaveLength(2);
    expect(caminhos[0].getAttribute('d')).toBe(ICONES.olho[0]);
    expect(elemento.getAttribute('aria-hidden')).toBe('true');
    expect(elemento.classList).toContain('flux-icone');
    expect(elemento.style.width).toBe('20px');

    fixture.componentRef.setInput('tamanho', 16);
    fixture.detectChanges();
    expect(elemento.style.width).toBe('16px');
    expect(elemento.style.height).toBe('16px');
  });
});
