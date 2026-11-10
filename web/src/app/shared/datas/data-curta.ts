import { FUSO_DO_NEGOCIO } from './data-por-extenso';

// en-CA escreve a data no formato ISO ("2026-11-09"), o mesmo da API.
const DATA_ISO = new Intl.DateTimeFormat('en-CA', {
  timeZone: FUSO_DO_NEGOCIO,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

/** O dia do instante no fuso do negócio, no formato da API ("2026-11-09"). */
export function dataIsoNoFuso(instante: Date): string {
  return DATA_ISO.format(instante);
}

/** "2026-10-01" vira "01/10/2026". Não passa por Date, então não há fuso para errar. */
export function dataCurta(dataIso: string): string {
  const [ano, mes, dia] = dataIso.split('-');
  return `${dia}/${mes}/${ano}`;
}

/** Instante da API ("2026-11-04T01:30:00Z") como data curta no fuso do negócio ("03/11/2026"). */
export function dataCurtaDoInstante(instante: string): string {
  return dataCurta(dataIsoNoFuso(new Date(instante)));
}
