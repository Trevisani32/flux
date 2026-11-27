import { convertToParamMap } from '@angular/router';
import { describe, expect, it } from 'vitest';

import {
  ateDoMes,
  diasDoGrafico,
  dozeMesesDepois,
  menorDoGrafico,
  parametrosDaUrl,
  pedidoDaUrl,
} from './apresentacao';
import { projecaoDeBancoHorizonte, projecaoDoPadrao } from './dados-de-teste';

describe('apresentação da projeção', () => {
  it('um dia do gráfico por dia do período, com o saldo do último dia com movimento', () => {
    const dias = diasDoGrafico(projecaoDeBancoHorizonte);
    expect(dias).toHaveLength(37);
    expect(dias[0]).toEqual({
      data: '2026-11-25', saldo: 731.65, rotuloDoEixo: 'hoje',
      leitura: 'quarta, 25/11 (hoje, com as pendências): saldo R$\u00a0731,65',
      titulo: 'Quarta, 25/11 (hoje, com as pendências)', linhas: [], dica: 'Saldo de hoje menos as pendências',
    });
    expect(dias[4]).toEqual({
      data: '2026-11-29', saldo: 331.85, rotuloDoEixo: null,
      leitura: 'domingo, 29/11: saldo R$\u00a0331,85', titulo: 'Domingo, 29/11', linhas: [], dica: 'Nenhum movimento neste dia',
    });
    expect(dias[5].linhas).toEqual([
      { texto: 'Conta de luz Fictícia', valor: '≈ −R$\u00a0189,90' },
      { texto: 'Seguro Fictício', valor: '−R$\u00a089,90' },
    ]);
    expect(menorDoGrafico(projecaoDeBancoHorizonte)).toEqual({ data: '2026-12-04', rotulo: '−R$\u00a02.397,95 · 04/12' });
    expect(menorDoGrafico({ ...projecaoDeBancoHorizonte, resumo: { ...projecaoDeBancoHorizonte.resumo, menor: 1 } })).toBeNull();
  });

  it('os rótulos do eixo', () => {
    const trintaESete = diasDoGrafico(projecaoDeBancoHorizonte);
    expect(trintaESete.map((dia, indice) => [indice, dia.rotuloDoEixo]).filter(([, rotulo]) => rotulo !== null))
      .toEqual([[0, 'hoje'], [7, '02/12'], [14, '09/12'], [21, '16/12'], [28, '23/12'], [36, '31/12']]);
    const longo = diasDoGrafico({ ...projecaoDeBancoHorizonte, ate: '2027-11-25' });
    expect(longo.filter((dia) => dia.rotuloDoEixo !== null).map((dia) => dia.rotuloDoEixo))
      .toEqual([
        'hoje', '01/12', '01/01', '01/02', '01/03', '01/04', '01/05',
        '01/06', '01/07', '01/08', '01/09', '01/10', '01/11', '25/11',
      ]);
  });

  it('os parâmetros da URL vão e voltam', () => {
    expect([
      pedidoDaUrl(convertToParamMap({})), pedidoDaUrl(convertToParamMap({ ate: '2027-02-25' })),
      pedidoDaUrl(convertToParamMap({ contas: '1,4' })), pedidoDaUrl(convertToParamMap({ contas: '' })),
      pedidoDaUrl(convertToParamMap({ contas: 'abc' })),
    ]).toEqual([
      { ate: null, contas: null }, { ate: '2027-02-25', contas: null }, { ate: null, contas: [1, 4] },
      { ate: null, contas: [] }, { ate: null, contas: [] },
    ]);
    expect(parametrosDaUrl(null, null, projecaoDoPadrao)).toEqual({ ate: null, contas: null });
    expect(parametrosDaUrl(null, [1], projecaoDeBancoHorizonte)).toEqual({ ate: null, contas: '1' });
    expect(parametrosDaUrl(null, [4, 2], projecaoDoPadrao)).toEqual({ ate: null, contas: '2,4' });
    expect(parametrosDaUrl(null, [], projecaoDeBancoHorizonte)).toEqual({ ate: null, contas: '' });
    expect([dozeMesesDepois('2026-11-25'), dozeMesesDepois('2028-02-29')]).toEqual(['2027-11-25', '2029-02-28']);
    expect([
      ateDoMes('2026-12', '2026-11-25'), ateDoMes('2027-03', '2026-11-25'), ateDoMes('2027-11', '2026-11-25'),
    ]).toEqual([null, '2027-03-31', '2027-11-25']);
  });
});
