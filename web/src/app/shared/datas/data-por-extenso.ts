/** Fuso em que o negócio calcula "hoje" (produto.md, Regras gerais). */
export const FUSO_DO_NEGOCIO = 'America/Sao_Paulo';

const FORMATO_POR_EXTENSO = new Intl.DateTimeFormat('pt-BR', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  timeZone: FUSO_DO_NEGOCIO,
});

/** "Sexta-feira, 6 de novembro de 2026", no fuso do negócio. */
export function dataPorExtenso(data: Date): string {
  const texto = FORMATO_POR_EXTENSO.format(data);
  return texto.charAt(0).toLocaleUpperCase('pt-BR') + texto.slice(1);
}
