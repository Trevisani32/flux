import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { TipoValor, Valor } from './valor';

describe('Valor', () => {
  function textoSemEspacoEspecial(texto: string | null): string {
    return (texto ?? '').replace(/[\u00A0\u202F]/g, ' ');
  }

  function criar(valor: number | null, tipo: TipoValor): HTMLElement {
    const fixture = TestBed.createComponent(Valor);
    fixture.componentRef.setInput('valor', valor);
    fixture.componentRef.setInput('tipo', tipo);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it.each([
    // Fatia 05b: o pagamento de fatura leva o "−" em cor neutra (não é despesa).
    [-265.2, 'pagamento', '−R$ 265,20', 'menos R$ 265,20', 'flux-valor--neutro'],
    [7200, 'sinal', '+R$ 7.200,00', 'mais R$ 7.200,00', 'flux-valor--positivo'],
    [-287.43, 'sinal', '−R$ 287,43', 'menos R$ 287,43', 'flux-valor--negativo'],
    [0, 'sinal', 'R$ 0,00', null, 'flux-valor--neutro'],
    [500, 'neutro', 'R$ 500,00', null, 'flux-valor--neutro'],
    [-500, 'neutro', 'R$ 500,00', null, 'flux-valor--neutro'],
    [12847.9, 'saldo', 'R$ 12.847,90', null, null],
    [-120, 'saldo', '−R$ 120,00', 'menos R$ 120,00', 'flux-valor--negativo'],
    [null, 'sinal', '—', 'sem valor', 'flux-valor--neutro'],
  ] as const)('apresenta %s como %s', (valor, tipo, visivel, falado, classe) => {
    const elemento = criar(valor, tipo);
    const texto = elemento.querySelector('.valor__texto');
    expect(textoSemEspacoEspecial(texto?.textContent ?? null)).toBe(visivel);
    expect(texto?.getAttribute('aria-hidden')).toBe(falado === null ? null : 'true');
    expect(textoSemEspacoEspecial(elemento.querySelector('.flux-visualmente-oculto')?.textContent ?? null)).toBe(falado ?? '');
    expect(elemento.classList).toContain('flux-valor');
    expect(elemento.classList).toContain('flux-num');
    if (classe !== null) {
      expect(elemento.classList).toContain(classe);
    } else {
      expect([...elemento.classList]).not.toContain('flux-valor--positivo');
      expect([...elemento.classList]).not.toContain('flux-valor--negativo');
      expect([...elemento.classList]).not.toContain('flux-valor--neutro');
    }
  });

  it('o tipo previsto tem o sinal e a cor neutra', () => {
    const despesa = criar(-189.9, 'previsto');
    expect(textoSemEspacoEspecial(despesa.querySelector('.valor__texto')?.textContent ?? null)).toBe('−R$ 189,90');
    expect(textoSemEspacoEspecial(despesa.querySelector('.flux-visualmente-oculto')?.textContent ?? null)).toBe('menos R$ 189,90');
    expect(despesa.classList).toContain('flux-valor--neutro');
    const receita = criar(8200, 'previsto');
    expect(textoSemEspacoEspecial(receita.querySelector('.valor__texto')?.textContent ?? null)).toBe('+R$ 8.200,00');
    expect(textoSemEspacoEspecial(receita.querySelector('.flux-visualmente-oculto')?.textContent ?? null)).toBe('mais R$ 8.200,00');
    expect(receita.classList).toContain('flux-valor--neutro');
  });
});
