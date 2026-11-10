import { describe, expect, it } from 'vitest';

import { dataCurta, dataCurtaDoInstante, dataIsoNoFuso } from './data-curta';

describe('datas curtas', () => {
  it('calcula o dia no fuso de São Paulo, não no do computador', () => {
    expect(dataIsoNoFuso(new Date('2026-11-09T02:59:00Z'))).toBe('2026-11-08');
    expect(dataIsoNoFuso(new Date('2026-11-09T03:00:00Z'))).toBe('2026-11-09');
  });

  it('escreve a data da API como dd/mm/aaaa', () => {
    expect(dataCurta('2026-10-01')).toBe('01/10/2026');
  });

  it('escreve o instante da API como a data em São Paulo', () => {
    expect(dataCurtaDoInstante('2026-11-04T01:30:00Z')).toBe('03/11/2026');
    expect(dataCurtaDoInstante('2026-11-04T03:30:00.123456Z')).toBe('04/11/2026');
  });
});
