import { describe, expect, it } from 'vitest';

import { CategoriaDoRelatorio, MesDoRelatorio, TotalDoRelatorio } from './relatorios.service';
import {
  colunasDoGrafico,
  corDaComparacao,
  faixaDosMeses,
  legendaDaMedia,
  leituraDaComparacao,
  linkDaCategoria,
  notaDaMedia,
  pontosDoSaldo,
  quantidadeDeContas,
  rotuloDoSaldo,
  textoDaComparacao,
  textoDasDespesas,
  textoDasReceitas,
} from './apresentacao';

const TOTAL: TotalDoRelatorio = {
  valor: 100,
  quantidade: 2,
  noCartao: 1,
  media: 50,
  comparacao: { tipo: 'ACIMA', percentual: 100 },
};
const CATEGORIA: CategoriaDoRelatorio = {
  id: 4,
  nome: 'Mercado',
  arquivada: false,
  direto: false,
  valor: 10,
  media: 5,
  comparacao: null,
  subcategorias: [],
};
const MESES: readonly MesDoRelatorio[] = [
  {
    mes: '2026-10',
    receitas: 100,
    despesas: 20,
    resultado: 80,
    dataDoSaldo: '2026-10-31',
    saldo: 300,
  },
];

describe('apresentação dos relatórios', () => {
  it('mostra o texto, a leitura e a cor da comparação para despesa e receita', () => {
    const acima = { tipo: 'ACIMA' as const, percentual: 12 };
    expect(textoDaComparacao(acima)).toBe('▲ 12% acima');
    expect(leituraDaComparacao(acima, 'receita')).toBe(' (12% acima da média)');
    expect(corDaComparacao(acima, 'despesa')).toBe('ruim');
    expect(corDaComparacao(acima, 'receita')).toBe('bom');
  });

  it('compõe a nota e a legenda da média', () => {
    expect(notaDaMedia('2026-11', [], false)).toContain('Ainda não há');
    expect(notaDaMedia('2026-11', ['2026-10'], false)).toContain('outubro');
    expect(legendaDaMedia(3, true)).toBe('Média dos 3 meses');
  });

  it('compõe os textos dos cards e o rótulo do saldo', () => {
    expect(textoDasReceitas(TOTAL)).toContain('2 receitas');
    expect(textoDasDespesas(TOTAL)).toContain('1 no cartão');
    expect(
      rotuloDoSaldo({
        data: '2026-10-31',
        hoje: false,
        total: 100,
        investimentos: 0,
        contas: 1,
        contasDeInvestimento: 0,
      }),
    ).toBe('Saldo total em 31/10/2026');
    expect(quantidadeDeContas(2)).toBe('2 contas');
  });

  it('monta a faixa, colunas e pontos dos gráficos', () => {
    expect(faixaDosMeses(MESES, false)).toContain('outubro');
    expect(colunasDoGrafico(MESES)[0].leitura).toContain('receitas');
    expect(pontosDoSaldo(MESES, '2026-11-24')[0].titulo).toContain('Fim de');
  });

  it('gera os links das categorias para a lista', () => {
    expect(linkDaCategoria('2026-10', CATEGORIA)).toEqual({ mes: '2026-10', categoria: 4 });
    expect(linkDaCategoria('2026-10', { ...CATEGORIA, id: null })).toEqual({
      mes: '2026-10',
      categoria: 'sem',
      tipo: 'despesa',
    });
    expect(linkDaCategoria('2026-10', { ...CATEGORIA, direto: true })).toBeNull();
  });
});
