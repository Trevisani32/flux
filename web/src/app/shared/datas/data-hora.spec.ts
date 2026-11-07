import { describe, expect, it } from 'vitest';

import { dataDaVersao, dataHoraCurta } from './data-hora';

describe('datas', () => {
  it('formata a data da versão', () => {
    expect(dataDaVersao('2026-11-06')).toBe('6 de novembro de 2026');
  });

  it('formata data e hora no horário de São Paulo', () => {
    expect(dataHoraCurta('2026-11-08T01:40:00Z')).toBe('07/11/2026 às 22:40');
  });

  it('mantém meia-noite no formato curto', () => {
    expect(dataHoraCurta('2026-11-07T03:00:00Z')).toBe('07/11/2026 às 00:00');
  });
});
