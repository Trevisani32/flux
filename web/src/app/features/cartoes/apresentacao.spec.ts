import { describe, expect, it } from 'vitest';

import {
  comAno, dataMinimaDaCompra, diaEMes, diasDoCartao, diasEntre, dicaDoEstorno, faixaDeMeses, mesCurto, mesSemAno, metaDoItemDaFatura,
  quandoFecha, quantidadeDeCartoesAtivos, rotuloDaParcela, somarDias, textoDaCategoria, textoDaPrevia, textoDoTotal,
} from './apresentacao';
import { Cartao, ItemDaFatura } from './cartoes.service';
import { ParcelaDaCompra, PreviaDaCompra } from './compras.service';

/** O Intl escreve "R$" com espaço não separável: o texto da tela volta com espaço comum. */
function normal(texto: string | null): string | null {
  return texto === null ? null : texto.replace(/\s/g, ' ');
}

function parcela(numero: number, mes: string, valor: number, noFlux = true): ParcelaDaCompra {
  return { numero, mes, vencimento: `${mes}-10`, valor, noFlux };
}

function previa(parcelas: ParcelaDaCompra[], situacao: 'FUTURA' | 'ABERTA' | 'FECHADA' = 'ABERTA'): PreviaDaCompra {
  const mes = parcelas[0].mes;
  return { parcelas, primeiraFatura: { mes, fechamento: `${mes}-03`, vencimento: `${mes}-10`, situacao } };
}

describe('apresentação dos cartões', () => {
  it('escreve datas, meses e quantidades', () => {
    expect(diaEMes('2026-12-03')).toBe('03/12');
    expect(mesSemAno('2026-12')).toBe('dezembro');
    expect(mesCurto('2027-01')).toBe('jan/2027');
    expect(quantidadeDeCartoesAtivos(1)).toBe('1 cartão ativo');
    expect(quantidadeDeCartoesAtivos(2)).toBe('2 cartões ativos');
    expect(diasEntre('2026-11-12', '2026-12-03')).toBe(21);
    expect(diasEntre('2026-12-31', '2027-01-01')).toBe(1);
    expect(somarDias('2026-12-31', 1)).toBe('2027-01-01');
    expect(somarDias('2026-03-01', -1)).toBe('2026-02-28');
    expect(quandoFecha('2026-11-12', '2026-12-03')).toBe('Fecha em 21 dias');
    expect(quandoFecha('2026-12-02', '2026-12-03')).toBe('Fecha amanhã');
    expect(quandoFecha('2026-12-03', '2026-12-03')).toBe('Fecha hoje');
    // Decisão 16: 10 anos para trás, com o 29 de fevereiro virando 28 fora do ano bissexto.
    expect(dataMinimaDaCompra('2026-11-12')).toBe('2016-11-12');
    expect(dataMinimaDaCompra('2028-02-29')).toBe('2018-02-28');
    expect(dataMinimaDaCompra('2034-02-28')).toBe('2024-02-28');
  });

  it('escreve os dias do cartão, o total e a categoria', () => {
    const cartao = { diaFechamento: 3, diaVencimento: 10, contaPagamentoNome: 'Banco Horizonte' } as Cartao;
    expect(diasDoCartao(cartao, false)).toBe('Fecha dia 3 · vence dia 10 · paga com Banco Horizonte');
    expect(diasDoCartao(cartao, true)).toBe('Fecha 3 · vence 10 · Banco Horizonte');
    expect(normal(textoDoTotal(1113.69))).toBe('R$ 1.113,69');
    expect(normal(textoDoTotal(-30))).toBe('Crédito de R$ 30,00');
    expect(normal(textoDoTotal(0))).toBe('R$ 0,00');
    expect(textoDaCategoria(null, null)).toBe('Sem categoria');
    expect(textoDaCategoria('Mercado', 'Alimentação')).toBe('Alimentação › Mercado');
    expect(rotuloDaParcela(1, 3)).toBe('1/3');
    expect(rotuloDaParcela(1, 1)).toBeNull();
    expect(dicaDoEstorno({ mes: '2026-12', vencimento: '2026-12-10' }, '2026-11-12'))
      .toBe('Cai na fatura de dezembro (vence 10/12) e abate o total. Devolveu a compra inteira antes de pagar? Exclua a compra.');
    expect(dicaDoEstorno({ mes: '2016-12', vencimento: '2016-12-10' }, '2026-11-12'))
      .toBe('Cai na fatura de dezembro de 2016 (vence 10/12/2016) e abate o total. Devolveu a compra inteira antes de pagar? Exclua a compra.');
    expect(dicaDoEstorno(null, '2026-11-12'))
      .toBe('Cai na fatura da data do estorno e abate o total. Devolveu a compra inteira antes de pagar? Exclua a compra.');
  });

  it('escreve a linha de baixo do item da fatura', () => {
    const item = { tipo: 'COMPRA', parcelas: 3, parcelaInicial: 1, valorTotal: 450 } as ItemDaFatura;
    expect(normal(metaDoItemDaFatura(item))).toBe('R$ 450,00 em 3x');
    expect(normal(metaDoItemDaFatura({ ...item, parcelas: 10, parcelaInicial: 6, valorTotal: 2490 }))).toBe('R$ 2.490,00 em 10x · no Flux a partir da 6ª');
    expect(metaDoItemDaFatura({ ...item, parcelas: 1 })).toBe('À vista');
    expect(metaDoItemDaFatura({ ...item, tipo: 'ESTORNO', parcelas: 1 })).toBe('Crédito na fatura');
  });

  it('escreve os meses com o ano quando não são deste ano ou a faixa cruza anos', () => {
    expect(comAno('2026-11-12', '2026-12')).toBe(false);
    expect(comAno('2026-11-12', '2016-11')).toBe(true);
    expect(comAno('2026-11-12', '2026-12', '2027-03')).toBe(true);
    expect(faixaDeMeses('2026-12', '2026-12', false)).toBe('dezembro');
    expect(faixaDeMeses('2016-11', '2016-11', true)).toBe('novembro de 2016');
    expect(faixaDeMeses('2027-01', '2027-03', false)).toBe('janeiro a março');
    expect(faixaDeMeses('2027-01', '2027-03', true)).toBe('janeiro a março de 2027');
    expect(faixaDeMeses('2016-12', '2027-08', true)).toBe('dezembro de 2016 a agosto de 2027');
    const antiga = textoDaPrevia(previa([parcela(1, '2016-11', 10)], 'FECHADA'), '2026-11-12');
    expect(antiga.titulo).toBe('À vista, na fatura de novembro de 2016');
    expect(antiga.linha).toBe('Fechou em 03/11/2016 e venceu em 10/11/2016: a compra entra nela mesmo assim.');
    const cruza = textoDaPrevia(previa([parcela(1, '2026-12', 50, false), parcela(2, '2027-01', 50, false), parcela(3, '2027-02', 50)]),
      '2026-11-12');
    expect(cruza.lista[0]).toEqual({ rotulo: '1ª a 2ª', valor: 'dezembro de 2026 a janeiro de 2027', fora: true });
    expect(cruza.nota).toBe('A 1ª caiu na fatura de dezembro. As de antes da 3ª ficam fora do Flux.');
  });

  it('escreve a prévia: à vista, parcelas iguais, com resto e em andamento (decisão 18)', () => {
    const fechada = textoDaPrevia(previa([parcela(1, '2026-11', 59.9)], 'FECHADA'), '2026-11-12');
    expect(fechada).toEqual({ titulo: 'À vista, na fatura de novembro', linha: 'Fechou em 03/11 e venceu em 10/11: a compra entra nela mesmo assim.',
      lista: [], nota: null });
    expect(textoDaPrevia(previa([parcela(1, '2026-12', 59.9)]), '2026-11-12').linha).toBe('Fecha em 03/12 e vence em 10/12.');

    const iguais = textoDaPrevia(previa(Array.from({ length: 10 }, (_, i) => parcela(i + 1, `${i < 1 ? 2026 : 2027}-${String(i < 1 ? 12 : i).padStart(2, '0')}`,
      199))), '2026-11-12');
    expect(normal(iguais.titulo)).toBe('10x de R$ 199,00');
    expect(iguais.linha).toBe('1ª na fatura de dezembro (vence 10/12/2026) · última em setembro de 2027.');
    expect(iguais.lista).toEqual([]);

    const comResto = textoDaPrevia(previa([parcela(1, '2026-12', 33.34), parcela(2, '2027-01', 33.33), parcela(3, '2027-02', 33.33)]), '2026-11-12');
    expect(comResto.titulo).toBe('3 parcelas');
    expect(comResto.lista.map((linha) => [linha.rotulo, normal(linha.valor), linha.fora]))
      .toEqual([['1ª · dez/2026', 'R$ 33,34', false], ['2ª · jan/2027', 'R$ 33,33', false], ['3ª · fev/2027', 'R$ 33,33', false]]);
    expect(comResto.nota).toBe('O que sobra da divisão vai para a 1ª.');

    const andamento = textoDaPrevia(previa([parcela(1, '2026-07', 249, false), parcela(2, '2026-08', 249, false), parcela(3, '2026-09', 249, false),
      parcela(4, '2026-10', 249, false), parcela(5, '2026-11', 249, false), parcela(6, '2026-12', 249), parcela(7, '2027-01', 249)]), '2026-11-12');
    expect(normal(andamento.titulo)).toBe('2 parcelas de R$ 249,00 no Flux');
    expect(andamento.lista[0]).toEqual({ rotulo: '1ª a 5ª', valor: 'julho a novembro', fora: true });
    expect(andamento.lista[1].rotulo).toBe('6ª · dez/2026');
    expect(andamento.nota).toBe('A 1ª caiu na fatura de julho. As de antes da 6ª ficam fora do Flux.');
    const ultima = textoDaPrevia(previa([parcela(1, '2026-11', 50, false), parcela(2, '2026-12', 50)]), '2026-11-12');
    expect(normal(ultima.titulo)).toBe('1 parcela de R$ 50,00 no Flux');
    expect(ultima.lista[0]).toEqual({ rotulo: '1ª', valor: 'novembro', fora: true });
  });
});
