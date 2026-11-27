import { describe, expect, it } from 'vitest';

import { FaturaFora, ItemDaProjecao, TrechoNegativo } from './projecao.service';
import {
  linhaDaPrevistaNaFatura,
  maisDias,
  metaDaPendencia,
  naData,
  notaDoFim,
  notaDoMenor,
  notaDoSaldoHoje,
  periodoDoGrafico,
  rodapeDoPorConta,
  rotuloDoFim,
  subtituloDoCelular,
  textoDaFatura,
  textoDaTransferencia,
  textoDasFaturasFora,
  textoDoAviso,
  textoDoMenor,
  tituloDaFatura,
  tituloDasFaturasFora,
  tituloDoAviso,
  tituloDoNegativo,
  quandoNegativo,
} from './frases';

const HOJE = '2026-11-25';
const TRECHO: TrechoNegativo = { de: '2026-12-01', ate: '2026-12-04' };

const fatura: ItemDaProjecao = {
  tipo: 'FATURA', data: '2026-12-10', descricao: null, valor: -21.9, valorVaria: false,
  contaId: 1, contaNome: 'Banco Horizonte', lancamentoId: null, outraContaId: null,
  outraContaNome: null, cartaoId: 2, cartaoNome: 'Cartão Horizonte', cartaoCor: 'AZUL',
  mes: '2026-12', vencimento: '2026-12-10', pendente: 0, previsto: 21.9,
  previstos: [{
    recorrenciaId: 3,
    periodo: '2026-12-01',
    data: '2026-12-02',
    descricao: 'Spotify Fictício',
    valor: 21.9,
    ocorrencia: null,
  }],
  ocorrencia: null,
};

const fora: FaturaFora = {
  cartaoId: 9, cartaoNome: 'Cartão Sol', contaId: 8, contaNome: 'Conta antiga',
  meses: ['2026-12'], valor: 300,
};

describe('frases da projeção', () => {
  it('o trecho negativo e o título', () => {
    expect([
      quandoNegativo(TRECHO, HOJE, '2026-12-31'),
      quandoNegativo({ de: HOJE, ate: '2026-12-04' }, HOJE, '2026-12-31'),
      quandoNegativo({ de: '2026-12-01', ate: '2026-12-01' }, HOJE, '2026-12-31'),
      quandoNegativo({ de: HOJE, ate: HOJE }, HOJE, '2026-12-31'),
      quandoNegativo({ de: '2026-11-28', ate: '2026-12-31' }, HOJE, '2026-12-31'),
      quandoNegativo({ de: HOJE, ate: '2026-12-31' }, HOJE, '2026-12-31'),
      quandoNegativo({ de: '2027-01-01', ate: '2027-01-04' }, HOJE, '2027-11-25'),
    ]).toEqual([
      'de 01/12 a 04/12', 'de hoje a 04/12', 'em 01/12', 'hoje',
      'a partir de 28/11', 'a partir de hoje', 'de 01/01/2027 a 04/01/2027',
    ]);
    expect([tituloDoNegativo('Banco Horizonte', TRECHO, HOJE, '2026-12-31'), tituloDoNegativo('O total', TRECHO, HOJE, '2026-12-31')])
      .toEqual(['Banco Horizonte fica no negativo de 01/12 a 04/12.', 'O total fica no negativo de 01/12 a 04/12.']);
  });

  it('a data com na e no, e o texto do menor', () => {
    expect(['2026-12-04', '2026-12-05', '2026-12-06', '2026-11-30', '2027-01-15', HOJE].map((data) => naData(data, HOJE)))
      .toEqual(['na sexta, 04/12', 'no sábado, 05/12', 'no domingo, 06/12', 'na segunda, 30/11', 'na sexta, 15/01/2027', 'hoje']);
    expect(textoDoMenor(-2397.95, '2026-12-04', HOJE, [TRECHO], '2026-12-31'))
      .toEqual(['O menor saldo é ', { forte: '−R$\u00a02.397,95' }, ', na sexta, 04/12.']);
    expect(textoDoMenor(-2397.95, '2026-12-04', HOJE, [TRECHO, { de: '2027-01-01', ate: '2027-01-04' }], '2027-11-25'))
      .toEqual([
        'O menor saldo é ',
        { forte: '−R$\u00a02.397,95' },
        ', na sexta, 04/12.',
        ' Fica no negativo de novo de 01/01/2027 a 04/01/2027.',
      ]);
  });

  it('o aviso do Início', () => {
    expect([
      tituloDoAviso('Banco Horizonte', '2026-12-01', 1240.35, HOJE),
      tituloDoAviso('Carteira', HOJE, -317.7, HOJE),
      tituloDoAviso('Banco Horizonte', HOJE, 40.35, HOJE),
    ]).toEqual([
      'Banco Horizonte pode ficar no negativo em 01/12',
      'Carteira está no negativo hoje',
      'Banco Horizonte pode ficar no negativo hoje',
    ]);
    expect([
      textoDoAviso(-2397.95, '2026-12-04', HOJE, 0),
      textoDoAviso(-2397.95, '2026-12-04', HOJE, 1),
      textoDoAviso(-317.7, HOJE, HOJE, 2),
    ]).toEqual([
      'O menor saldo previsto é −R$\u00a02.397,95, em 04/12.',
      'O menor saldo previsto é −R$\u00a02.397,95, em 04/12. Mais 1 conta pode ficar no negativo.',
      'O menor saldo previsto é −R$\u00a0317,70, hoje. Mais 2 contas podem ficar no negativo.',
    ]);
  });

  it('as notas dos números e do gráfico', () => {
    expect([
      notaDoMenor('2026-12-04', 4, HOJE, false), notaDoMenor('2026-12-04', 0, HOJE, true),
      notaDoMenor(HOJE, 1, HOJE, false), notaDoMenor(HOJE, 1, HOJE, true),
      rotuloDoFim('2026-12-31', HOJE, false), rotuloDoFim('2026-12-31', HOJE, true), rotuloDoFim('2027-11-25', HOJE, false),
      notaDoFim(36), notaDoFim(1), notaDoSaldoHoje(['Banco Horizonte'], HOJE), notaDoSaldoHoje(['A', 'B', 'C', 'D', 'E'], HOJE),
      periodoDoGrafico(HOJE, '2026-12-31'),
    ]).toEqual([
      'sexta, 04/12 · negativo por 4 dias', 'sex, 04/12', 'hoje · negativo por 1 dia', 'hoje',
      'No fim, 31/12', 'Em 31/12', 'No fim, 25/11/2027', '36 dias depois de hoje',
      '1 dia depois de hoje', 'Banco Horizonte · quarta, 25/11', '5 contas · quarta, 25/11',
      'De hoje, 25/11, a 31/12/2026',
    ]);
  });

  it('os textos da fatura, da transferência e das pendências', () => {
    expect([
      tituloDaFatura('2026-12', 'Cartão Lua', HOJE),
      tituloDaFatura('2027-01', 'Cartão Lua', HOJE),
    ]).toEqual(['Fatura de dezembro do Cartão Lua', 'Fatura de janeiro de 2027 do Cartão Lua']);
    const faturaComDoisPrevistos = {
      ...fatura,
      previsto: 36.9,
      previstos: [...fatura.previstos, {
        ...fatura.previstos[0], recorrenciaId: 4, descricao: 'Netflix Fictícia', valor: 15,
      }],
    };
    expect([
      textoDaFatura({ ...fatura, previstos: [], previsto: 0 }, HOJE),
      textoDaFatura(fatura, HOJE),
      textoDaFatura(faturaComDoisPrevistos, HOJE),
    ]).toEqual([
      'Fatura de dezembro do Cartão Horizonte',
      'Fatura de dezembro do Cartão Horizonte, com o previsto de Spotify Fictício (R$\u00a021,90)',
      'Fatura de dezembro do Cartão Horizonte, com 2 previstos (R$\u00a036,90)',
    ]);
    expect([
      textoDaTransferencia({ descricao: 'Reserva do mês', valor: -500, outraContaNome: 'Poupança Horizonte' }),
      textoDaTransferencia({ descricao: 'Reserva do mês', valor: 500, outraContaNome: 'Banco Horizonte' }),
    ]).toEqual(['Reserva do mês, para Poupança Horizonte', 'Reserva do mês, de Banco Horizonte']);
    expect([
      metaDaPendencia({ ...fatura, vencimento: '2026-11-05', pendente: 412.3 }, HOJE, false),
      metaDaPendencia({ ...fatura, vencimento: '2026-11-10', pendente: 0 }, HOJE, false),
      metaDaPendencia({ ...fatura, vencimento: HOJE, pendente: 50 }, HOJE, false),
      metaDaPendencia({ ...fatura, tipo: 'PREVISTO', data: '2026-11-20' }, HOJE, true),
      metaDaPendencia({ ...fatura, tipo: 'PREVISTO', data: HOJE }, HOJE, false),
    ]).toEqual([
      { pilula: 'Venceu', texto: 'venceu em 05/11 · sai de Banco Horizonte' },
      { pilula: null, texto: 'venceu em 10/11 · sai de Banco Horizonte' },
      { pilula: null, texto: 'vence hoje · sai de Banco Horizonte' },
      { pilula: 'Atrasada', texto: 'desde 20/11 · Banco Horizonte' },
      { pilula: 'Previsto', texto: 'hoje' },
    ]);
    expect(linhaDaPrevistaNaFatura({
      ...fatura.previstos[0], data: '2026-11-01', descricao: 'Revista Fictícia',
    }, HOJE)).toBe('Revista Fictícia · compra prevista em 01/11');
  });

  it('o rodapé, o subtítulo, os dias que faltam e as faturas fora', () => {
    expect([
      rodapeDoPorConta([]), rodapeDoPorConta(['Investimentos Lua']), rodapeDoPorConta(['A', 'B']),
    ]).toEqual([
      'O menor de cada conta é o dela; o total não é a soma dos menores.',
      'O menor de cada conta é o dela; o total não é a soma dos menores. Investimentos Lua fica fora: marque para incluir.',
      'O menor de cada conta é o dela; o total não é a soma dos menores. A e B ficam fora: marque para incluir.',
    ]);
    expect([
      subtituloDoCelular('2026-12-31', 1, 6, HOJE),
      subtituloDoCelular('2026-12-31', 1, 1, HOJE),
      maisDias(11, '2026-12-31', HOJE),
      maisDias(1, '2026-12-31', HOJE),
    ]).toEqual(['Até 31/12 · 1 de 6 contas', 'Até 31/12 · 1 de 1 conta', 'e mais 11 dias até 31/12', 'e mais 1 dia até 31/12']);
    const doisMeses = { ...fora, meses: ['2026-12', '2027-01'] as const, valor: 450 };
    expect([
      tituloDasFaturasFora(fora),
      textoDasFaturasFora(fora, HOJE),
      tituloDasFaturasFora(doisMeses),
      textoDasFaturasFora(doisMeses, HOJE),
    ]).toEqual([
      'Uma fatura ficou fora',
      'A fatura de dezembro do Cartão Sol (R$\u00a0300,00) sai de Conta antiga, que está arquivada: '
        + 'ela ficou fora da projeção. Troque a conta de pagamento do cartão.',
      '2 faturas ficaram fora',
      'As faturas de dezembro de 2026 a janeiro de 2027 do Cartão Sol (R$\u00a0450,00) saem de Conta antiga, '
        + 'que está arquivada: elas ficaram fora da projeção. Troque a conta de pagamento do cartão.',
    ]);
  });
});
