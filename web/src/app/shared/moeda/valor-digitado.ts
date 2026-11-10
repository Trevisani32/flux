import { AbstractControl, ValidationErrors } from '@angular/forms';

import { SINAL_MENOS } from './moeda';

export type ErroDoValor = 'vazio' | 'invalido' | 'casas' | 'grande';

export type ValorLido = { readonly valor: number } | { readonly erro: ErroDoValor };

/** Mesmo limite da API: 13 dígitos antes da vírgula cabem num number sem perder centavo. */
const DIGITOS_INTEIROS = 13;

// Sinal opcional ("-" ou "−"), parte inteira com pontos de milhar bem colocados ou sem
// ponto nenhum, e vírgula decimal opcional.
const FORMATO = /^([-−]?)(\d{1,3}(?:\.\d{3})+|\d+)(?:,(\d*))?$/;

const ESCRITA = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/**
 * Lê um valor digitado em pt-BR: "1.234,56", "1234,56", "-120" e "−120,00". Não faz
 * conta com dinheiro: só monta o número a partir dos dígitos ("1234.56").
 */
export function lerValorDigitado(texto: string): ValorLido {
  const limpo = texto.replace(/\s/g, '');
  if (limpo === '') {
    return { erro: 'vazio' };
  }
  const partes = FORMATO.exec(limpo);
  if (partes === null) {
    return { erro: 'invalido' };
  }
  const [, sinal, inteiro, fracao = ''] = partes;
  if (fracao.length > 2) {
    return { erro: 'casas' };
  }
  const digitos = inteiro.replace(/\./g, '').replace(/^0+(?=\d)/, '');
  if (digitos.length > DIGITOS_INTEIROS) {
    return { erro: 'grande' };
  }
  const valor = Number(`${sinal === '' ? '' : '-'}${digitos}.${fracao.padEnd(2, '0')}`);
  // Number("-0.00") é -0, que viraria "−0,00" na tela.
  return { valor: valor === 0 ? 0 : valor };
}

/** Escreve um valor no formato do campo: "1.234,56" e "−120,00". */
export function escreverValorDigitado(valor: number): string {
  return ESCRITA.format(valor === 0 ? 0 : valor).replace('-', SINAL_MENOS);
}

/** Validador do campo de valor: { valor: 'vazio' | 'invalido' | 'casas' | 'grande' }. */
export function valorDigitado(controle: AbstractControl): ValidationErrors | null {
  const lido = lerValorDigitado(typeof controle.value === 'string' ? controle.value : '');
  return 'erro' in lido ? { valor: lido.erro } : null;
}
