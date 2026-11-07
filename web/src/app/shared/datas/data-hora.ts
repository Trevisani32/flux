const DATA_DA_VERSAO = new Intl.DateTimeFormat('pt-BR', {
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
});

const DATA_HORA = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
  timeZone: 'America/Sao_Paulo',
});

/** "2026-11-06" vira "6 de novembro de 2026". */
export function dataDaVersao(versao: string): string {
  return DATA_DA_VERSAO.format(new Date(`${versao}T00:00:00Z`));
}

/** "2026-11-08T01:40:00Z" vira "07/11/2026 às 22:40" (horário de São Paulo). */
export function dataHoraCurta(instante: string): string {
  const partes = Object.fromEntries(DATA_HORA.formatToParts(new Date(instante)).map((parte) => [parte.type, parte.value]));
  return `${partes['day']}/${partes['month']}/${partes['year']} às ${partes['hour']}:${partes['minute']}`;
}
