import { FormControl } from '@angular/forms';
import { describe, expect, it } from 'vitest';

import { escreverValorDigitado, lerValorDigitado, valorDigitado } from './valor-digitado';

describe('lerValorDigitado', () => {
  it('lê vírgula decimal, ponto de milhar e o sinal de menos', () => {
    expect(lerValorDigitado('1.234,56')).toEqual({ valor: 1234.56 });
    expect(lerValorDigitado('1234,56')).toEqual({ valor: 1234.56 });
    expect(lerValorDigitado('  4.218,4 ')).toEqual({ valor: 4218.4 });
    expect(lerValorDigitado('1.234')).toEqual({ valor: 1234 });
    expect(lerValorDigitado('-120')).toEqual({ valor: -120 });
    expect(lerValorDigitado('−120,00')).toEqual({ valor: -120 });
    expect(lerValorDigitado('0,5')).toEqual({ valor: 0.5 });
    expect(lerValorDigitado('12,')).toEqual({ valor: 12 });
    expect(lerValorDigitado('007')).toEqual({ valor: 7 });
    expect(lerValorDigitado('9.999.999.999.999,99')).toEqual({ valor: 9999999999999.99 });
  });

  it('nunca devolve menos zero', () => {
    const lido = lerValorDigitado('-0,00');
    expect(lido).toEqual({ valor: 0 });
    expect('valor' in lido && Object.is(lido.valor, -0)).toBe(false);
  });

  it('recusa o que não é um valor em pt-BR', () => {
    expect(lerValorDigitado('')).toEqual({ erro: 'vazio' });
    expect(lerValorDigitado('   ')).toEqual({ erro: 'vazio' });
    expect(lerValorDigitado('abc')).toEqual({ erro: 'invalido' });
    expect(lerValorDigitado('12.5')).toEqual({ erro: 'invalido' });
    expect(lerValorDigitado('1234.56')).toEqual({ erro: 'invalido' });
    expect(lerValorDigitado('1.23,4')).toEqual({ erro: 'invalido' });
    expect(lerValorDigitado('1,2,3')).toEqual({ erro: 'invalido' });
    expect(lerValorDigitado('R$ 10')).toEqual({ erro: 'invalido' });
    expect(lerValorDigitado('10,005')).toEqual({ erro: 'casas' });
    expect(lerValorDigitado('99999999999999')).toEqual({ erro: 'grande' });
    expect(lerValorDigitado('-10.000.000.000.000,00')).toEqual({ erro: 'grande' });
  });
});

describe('escreverValorDigitado', () => {
  it('escreve no formato do campo, com o sinal de menos tipográfico', () => {
    expect(escreverValorDigitado(1234.5)).toBe('1.234,50');
    expect(escreverValorDigitado(0)).toBe('0,00');
    expect(escreverValorDigitado(-0)).toBe('0,00');
    expect(escreverValorDigitado(-120)).toBe('−120,00');
    expect(escreverValorDigitado(9999999999999.99)).toBe('9.999.999.999.999,99');
  });
});

describe('valorDigitado', () => {
  it('devolve o tipo do erro ou null', () => {
    expect(valorDigitado(new FormControl('1.234,56'))).toBeNull();
    expect(valorDigitado(new FormControl(''))).toEqual({ valor: 'vazio' });
    expect(valorDigitado(new FormControl('10,005'))).toEqual({ valor: 'casas' });
    expect(valorDigitado(new FormControl(null))).toEqual({ valor: 'vazio' });
  });
});
