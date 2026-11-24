import { describe, expect, it } from 'vitest';

import {
  escalaVertical,
  faixas,
  larguraDaColuna,
  marcasDoEixo,
  naEscala,
  textoDoEixo,
} from './escalas';

describe('escalas', () => {
  it('as marcas do eixo são redondas, sempre com o zero, e descem só com valor negativo', () => {
    expect(marcasDoEixo([9850, 7920.4])).toEqual([0, 2500, 5000, 7500, 10000]);
    expect(marcasDoEixo([18420.1, 34512.05])).toEqual([0, 10000, 20000, 30000, 40000]);
    expect(marcasDoEixo([4200])).toEqual([0, 2000, 4000, 6000]);
    expect(marcasDoEixo([-1200, 3000])).toEqual([-2000, 0, 2000, 4000]);
    expect(marcasDoEixo([-500, -120])).toEqual([-600, -400, -200, 0]);
    expect(marcasDoEixo([])).toEqual([0, 1]);
    expect(marcasDoEixo([0])).toEqual([0, 1]);
    expect(marcasDoEixo([0.5])).toEqual([0, 1]);
    expect(marcasDoEixo([3])).toEqual([0, 1, 2, 3]);
    expect(marcasDoEixo([1250])).toEqual([0, 500, 1000, 1500]);
    expect(marcasDoEixo([9999999999999.99])).toEqual([
      0, 2500000000000, 5000000000000, 7500000000000, 10000000000000,
    ]);
  });

  it('o eixo escreve "mil" e o sinal de menos', () => {
    expect(textoDoEixo(0)).toBe('0');
    expect(textoDoEixo(500)).toBe('500');
    expect(textoDoEixo(2500)).toBe('2,5 mil');
    expect(textoDoEixo(1250)).toBe('1,25 mil');
    expect(textoDoEixo(40000)).toBe('40 mil');
    expect(textoDoEixo(1500000)).toBe('1.500 mil');
    expect(textoDoEixo(-5000)).toBe('−5 mil');
    expect(textoDoEixo(2.5)).toBe('2,5');
  });

  it('a escala vertical, as faixas dos meses, a largura das colunas e a barra das categorias', () => {
    const y = escalaVertical([0, 2500, 5000, 7500, 10000], 8, 194);
    expect([y(10000), y(0), y(5000)]).toEqual([8, 202, 105]);
    const yNegativo = escalaVertical([-2000, 0, 2000, 4000], 0, 120);
    expect(yNegativo(0)).toBe(80);
    expect(faixas(12, 40, 376, 64)).toEqual({ inicio: 40, largura: 31.333333333333332 });
    expect(faixas(2, 40, 376, 64)).toEqual({ inicio: 164, largura: 64 });
    expect(faixas(1, 40, 376, 64)).toEqual({ inicio: 196, largura: 64 });
    expect(larguraDaColuna(64)).toBe(14);
    expect(larguraDaColuna(24)).toBe(7);
    expect(larguraDaColuna(10)).toBe(2);
    expect(naEscala(1260.5, 2521)).toBe(50);
    expect(naEscala(0, 2521)).toBe(0);
    expect(naEscala(2521, 2521)).toBe(100);
    expect(naEscala(100, 0)).toBe(0);
  });
});
