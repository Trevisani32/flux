import { describe, expect, it } from 'vitest';

import {
  dataDaOcorrencia,
  dataLonga,
  dicaDaAtrasada,
  dicaDaFrequencia,
  fraseDaFrequencia,
  fraseDaRecorrencia,
  maisAte,
  notaDoAConfirmar,
  notaDoCartao,
  notaDoDiaAjustado,
  paragrafoDasQueSaemJunto,
  paragrafoDeEncerrar,
  paragrafoDeExcluir,
  quantidadeNaFaixa,
  quandoAConfirmar,
  rotuloDoPeriodo,
} from './frases';

describe('frases das recorrências', () => {
  const hoje = '2026-11-25';

  it('a frequência e a frase da linha', () => {
    expect(fraseDaFrequencia('MENSAL', 1, hoje)).toBe('Todo dia 1');
    expect(fraseDaFrequencia('SEMANAL', null, '2026-11-27')).toBe('Toda sexta');
    expect(fraseDaFrequencia('SEMANAL', null, '2026-11-28')).toBe('Todo sábado');
    expect(fraseDaFrequencia('SEMANAL', null, '2026-11-29')).toBe('Todo domingo');
    expect(fraseDaFrequencia('ANUAL', null, '2026-01-15')).toBe('Todo ano em 15/01');
    expect(fraseDaRecorrencia({
      frequencia: 'SEMANAL',
      diaDoMes: null,
      comecaEm: '2026-11-27',
      terminaEm: null,
    }, hoje)).toBe('Toda sexta, desde 27/11');
    expect(fraseDaRecorrencia({
      frequencia: 'MENSAL',
      diaDoMes: 15,
      comecaEm: hoje,
      terminaEm: '2027-06-15',
    }, hoje)).toBe('Todo dia 15, até 15/06/2027');
    expect(fraseDaRecorrencia({
      frequencia: 'MENSAL',
      diaDoMes: 15,
      comecaEm: '2027-02-01',
      terminaEm: null,
    }, hoje)).toBe('Todo dia 15, desde 01/02/2027');
  });

  it('as datas', () => {
    expect(dataDaOcorrencia('2026-11-26', hoje)).toBe('qui, 26/11');
    expect(dataDaOcorrencia('2027-01-15', hoje)).toBe('sex, 15/01/2027');
    expect(dataLonga('2026-11-30')).toBe('segunda, 30/11/2026');
    expect([
      quandoAConfirmar('2026-11-20', hoje),
      quandoAConfirmar(hoje, hoje),
      quandoAConfirmar('2026-11-26', hoje),
      quandoAConfirmar('2026-11-27', hoje),
    ]).toEqual(['atrasada desde 20/11', 'hoje, 25/11', 'amanhã, 26/11', 'sex, 27/11']);
    expect([
      notaDoAConfirmar(0, '2026-12-02', false),
      notaDoAConfirmar(1, '2026-12-02', false),
      notaDoAConfirmar(2, '2026-12-02', false),
      notaDoAConfirmar(2, '2026-12-02', true),
    ]).toEqual([
      'O que vence até quarta, 02/12',
      'A atrasada e o que vence até quarta, 02/12',
      'As atrasadas e o que vence até quarta, 02/12',
      'Até quarta, 02/12',
    ]);
    expect(maisAte(3, '2026-12-02', hoje)).toBe('e mais 3 até 02/12');
    expect([
      quantidadeNaFaixa(5, 1),
      quantidadeNaFaixa(14, 0),
      quantidadeNaFaixa(3, 2),
    ]).toEqual(['em 5, 1 atrasada', 'em 14', 'em 3, 2 atrasadas']);
    expect(dicaDaAtrasada('2027-01-19')).toBe('Aparece até 19/01/2027 (60 dias). Depois some, sem gravar nada.');
    expect([
      rotuloDoPeriodo('MENSAL', '2026-11-01', hoje),
      rotuloDoPeriodo('MENSAL', '2025-12-01', hoje),
      rotuloDoPeriodo('SEMANAL', '2026-11-23', hoje),
      rotuloDoPeriodo('ANUAL', '2027-01-01', hoje),
    ]).toEqual(['Novembro', 'Dezembro de 2025', 'Semana de 23/11', '2027']);
  });

  it('as dicas e as notas do diálogo', () => {
    expect([
      dicaDaFrequencia('MENSAL', 30, hoje),
      dicaDaFrequencia('MENSAL', 28, hoje),
      dicaDaFrequencia('SEMANAL', null, '2026-11-27'),
      dicaDaFrequencia('SEMANAL', null, '2026-11-28'),
      dicaDaFrequencia('ANUAL', null, '2024-02-29'),
      dicaDaFrequencia('ANUAL', null, '2026-01-15'),
    ]).toEqual([
      'Nos meses mais curtos, vale o último dia.',
      null,
      'Toda sexta-feira, o dia da semana do início.',
      'Todo sábado, o dia da semana do início.',
      'Todo ano em 29/02; nos anos sem esse dia, em 28/02.',
      'Todo ano em 15/01.',
    ]);
    expect(notaDoDiaAjustado('2027-02-28', 30)).toBe('28/02: fevereiro não tem dia 30.');
    expect(notaDoCartao('2026-11-28', '2027-01', '2027-01-05'))
      .toBe('Cada uma vira uma compra à vista. A de 28/11 cai na fatura de janeiro (vence 05/01/2027).');
  });

  it('os parágrafos', () => {
    expect(paragrafoDeEncerrar(9, false)).toEqual([
      'As previstas depois de hoje saem. As ',
      { forte: '9 já confirmadas' },
      ' continuam como lançamentos.',
    ]);
    expect(paragrafoDeEncerrar(1, true)).toEqual([
      'As previstas depois de hoje saem. A ',
      { forte: 'já confirmada' },
      ' continua como compra.',
    ]);
    expect(paragrafoDeEncerrar(0, false)).toEqual(['As previstas depois de hoje saem.']);
    expect(paragrafoDeExcluir(9, false)).toEqual([
      'Sai a recorrência e o histórico dela. Os ',
      { forte: '9 lançamentos confirmados' },
      ' ficam, sem a ligação.',
    ]);
    expect(paragrafoDeExcluir(1, true)).toEqual([
      'Sai a recorrência e o histórico dela. A ',
      { forte: 'compra confirmada' },
      ' fica, sem a ligação.',
    ]);
    expect(paragrafoDeExcluir(0, true)).toEqual(['Sai a recorrência e o histórico dela.']);
    expect(paragrafoDasQueSaemJunto(['Mensalidade do clube Fictícia'], false)).toEqual([
      'A recorrência ',
      { forte: 'Mensalidade do clube Fictícia' },
      ', só desta conta, sai junto.',
    ]);
    expect(paragrafoDasQueSaemJunto(['Netflix', 'Spotify'], true)).toEqual([
      'As recorrências ',
      { forte: 'Netflix' },
      ' e ',
      { forte: 'Spotify' },
      ', só deste cartão, saem junto.',
    ]);
  });
});
