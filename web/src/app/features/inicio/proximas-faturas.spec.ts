import { describe, expect, it } from 'vitest';

import { Cartao } from '../cartoes/cartoes.service';
import { metaDaProximaFatura, proximasFaturas, tituloDaProximaFatura } from './proximas-faturas';

function cartao(
  nome: string,
  vencimento: string,
  faturasAPagar: Cartao['faturasAPagar'],
  lancamentos = 0,
): Cartao {
  return {
    id: nome.length,
    nome,
    limite: 1000,
    diaFechamento: 3,
    diaVencimento: 10,
    contaPagamentoId: 1,
    contaPagamentoNome: 'Conta',
    contaPagamentoArquivada: false,
    cor: 'ROXO',
    arquivadoEm: null,
    faturaAberta: {
      mes: '2026-12',
      inicio: '2026-11-04',
      fechamento: '2026-12-03',
      vencimento,
      total: 70,
      lancamentos,
    },
    proximaFatura: {
      mes: '2027-01',
      inicio: '2026-12-04',
      fechamento: '2027-01-03',
      vencimento: '2027-01-10',
      total: 0,
      lancamentos: 0,
    },
    emAberto: 70,
    lancamentosEmAberto: lancamentos,
    pendente: 70,
    disponivel: 930,
    usoDoLimite: 7,
    faturasAPagar,
  };
}

describe('próximas faturas', () => {
  it('junta as fechadas com pendente e a aberta com lançamento de cada cartão ativo, na ordem do vencimento', () => {
    const lista = proximasFaturas([
      cartao(
        'Lua',
        '2026-12-10',
        [{ mes: '2026-11', fechamento: '2026-11-03', vencimento: '2026-11-10', pendente: 100 }],
        1,
      ),
      cartao('Zeta', '2026-12-08', [
        { mes: '2026-11', fechamento: '2026-11-03', vencimento: '2026-11-10', pendente: 30 },
      ]),
      cartao('Alfa', '2026-12-08', [
        { mes: '2026-11', fechamento: '2026-11-03', vencimento: '2026-11-10', pendente: 20 },
      ]),
      cartao('Sol', '2026-12-08', [], 0),
    ]);

    expect(lista.map((fatura) => [fatura.cartaoNome, fatura.mes])).toEqual([
      ['Alfa', '2026-11'],
      ['Lua', '2026-11'],
      ['Zeta', '2026-11'],
      ['Lua', '2026-12'],
    ]);
    expect(lista).not.toContainEqual(expect.objectContaining({ cartaoNome: 'Sol', aberta: true }));
  });

  it('os textos: aberta, fechada e vencida, com o ano quando a fatura não é do ano de hoje', () => {
    const [fechada, aberta] = proximasFaturas([
      cartao(
        'Lua',
        '2026-12-10',
        [{ mes: '2026-11', fechamento: '2026-11-03', vencimento: '2026-11-10', pendente: 100 }],
        1,
      ),
    ]);

    expect(tituloDaProximaFatura(fechada, '2026-11-24')).toBe('Cartão Lua · novembro');
    expect(metaDaProximaFatura(fechada, '2026-11-24')).toEqual({
      venceu: 'venceu em 10/11',
      resto: ' · pendente',
    });
    expect(metaDaProximaFatura(aberta, '2026-11-24').resto).toBe(
      'aberta · fecha 03/12 · vence 10/12',
    );
    expect(tituloDaProximaFatura({ ...fechada, mes: '2027-01' }, '2026-11-24')).toBe(
      'Cartão Lua · janeiro de 2027',
    );
  });
});
