const FORMATADOR_BRL = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

/** Texto mostrado quando não há valor. */
export const SEM_VALOR = '—';

/** Sinal de menos tipográfico (U+2212), o mesmo do mockup. */
export const SINAL_MENOS = '−';

export interface MoedaFormatada {
  /** Valor sem sinal, já formatado ("R$ 1.234,56", com espaço não separável). */
  readonly absoluto: string;
  readonly negativo: boolean;
  readonly zero: boolean;
}

/**
 * Formata um valor em reais sem fazer conta: o sinal e o "é zero" saem das
 * partes do próprio Intl. Devolve null quando não há número finito.
 */
export function partesDaMoeda(valor: number | null | undefined): MoedaFormatada | null {
  if (typeof valor !== 'number' || !Number.isFinite(valor)) {
    return null;
  }
  const partes = FORMATADOR_BRL.formatToParts(valor);
  const zero = partes
    .filter((parte) => parte.type === 'integer' || parte.type === 'fraction')
    .every((parte) => /^0+$/.test(parte.value));
  return {
    absoluto: partes.filter((parte) => parte.type !== 'minusSign').map((parte) => parte.value).join(''),
    negativo: !zero && partes.some((parte) => parte.type === 'minusSign'),
    zero,
  };
}

export function formatarMoeda(valor: number | null | undefined): string {
  const partes = partesDaMoeda(valor);
  if (partes === null) {
    return SEM_VALOR;
  }
  return partes.negativo ? `${SINAL_MENOS}${partes.absoluto}` : partes.absoluto;
}
