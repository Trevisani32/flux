import { describe, expect, it } from 'vitest';

import { MoedaPipe } from './moeda.pipe';

describe('MoedaPipe', () => {
  const pipe = new MoedaPipe();

  function normalizar(texto: string): string {
    return texto.replace(/[\u00A0\u202F]/g, ' ');
  }

  it('formata valores em reais', () => {
    expect(normalizar(pipe.transform(1234.56))).toBe('R$ 1.234,56');
    expect(normalizar(pipe.transform(0))).toBe('R$ 0,00');
    expect(normalizar(pipe.transform(-0))).toBe('R$ 0,00');
    expect(normalizar(pipe.transform(-0.004))).toBe('R$ 0,00');
    expect(normalizar(pipe.transform(1_000_000))).toBe('R$ 1.000.000,00');
    expect(normalizar(pipe.transform(123_456_789_012.34))).toBe('R$ 123.456.789.012,34');
  });

  it('usa o sinal de menos tipográfico', () => {
    expect(pipe.transform(-89.9)).toMatch(/^−R\$[\u00A0\u202F]89,90$/);
    expect(pipe.transform(-89.9)).not.toMatch(/^-/);
  });

  it.each([null, undefined, NaN, Infinity, '10.5' as unknown as number])('mostra travessão sem número: %s', (valor) => {
    expect(pipe.transform(valor)).toBe('—');
  });
});
