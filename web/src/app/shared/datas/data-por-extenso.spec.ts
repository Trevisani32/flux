import { describe, expect, it } from 'vitest';

import { dataPorExtenso } from './data-por-extenso';

describe('dataPorExtenso', () => {
  it.each([
    ['2026-11-06T12:00:00Z', 'Sexta-feira, 6 de novembro de 2026'],
    ['2026-11-07T02:30:00Z', 'Sexta-feira, 6 de novembro de 2026'],
    ['2026-11-07T03:00:00Z', 'Sábado, 7 de novembro de 2026'],
  ])('formata %s no fuso do negócio', (data, esperado) => {
    expect(dataPorExtenso(new Date(data))).toBe(esperado);
  });
});
