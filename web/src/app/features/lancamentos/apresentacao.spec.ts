import { describe, expect, it } from 'vitest';

import { Categoria } from '../categorias/categorias.service';
import { marcaDoDia, metaDoItem, nomeDoMes, nomeDoMesSemAno, opcoesDeCategoria, opcoesDeCategoriasArquivadas, quantidadeDeItens, rotuloDoDia } from './apresentacao';
import { ItemDaLista } from './lancamentos.service';

function item(parcial: Partial<ItemDaLista>): ItemDaLista {
  return {
    tipo: 'DESPESA', id: 1, data: '2026-11-06', descricao: 'Mercado Bom Preço', valor: -287.43, observacao: null,
    contaId: 1, contaNome: 'Banco Horizonte', categoriaId: 7, categoriaNome: 'Mercado', categoriaMaeNome: 'Alimentação',
    categoriaArquivada: false, contaDestinoId: null, contaDestinoNome: null, direcao: null, futuro: false, somenteLeitura: false, origem: 'MANUAL', descricaoBanco: null,
    ...parcial,
  };
}

function categoria(id: number, nome: string, subcategorias: Categoria[] = []): Categoria {
  return { id, nome, natureza: 'DESPESA', categoriaMaeId: null, nomeDaMae: null, arquivadaEm: null, subcategorias };
}

describe('apresentação dos lançamentos', () => {
  it('escreve o mês e o dia em português, com 1º e sem fuso', () => {
    expect(nomeDoMes('2026-11')).toBe('novembro de 2026');
    expect(nomeDoMesSemAno('2026-03')).toBe('Março');
    expect(rotuloDoDia('2026-11-06', false)).toBe('Sexta, 6 de novembro');
    expect(rotuloDoDia('2026-11-01', false)).toBe('Domingo, 1º de novembro');
    expect(rotuloDoDia('2026-11-10', true)).toBe('Ter, 10 nov');
    expect(marcaDoDia('2026-11-09', '2026-11-09')).toBe('hoje');
    expect(marcaDoDia('2026-12-01', '2026-11-30')).toBe('amanhã');
    expect(marcaDoDia('2026-11-08', '2026-11-09')).toBeNull();
  });

  it('conta lançamentos e transferências', () => {
    expect(quantidadeDeItens(14, 2)).toBe('14 lançamentos e 2 transferências');
    expect(quantidadeDeItens(1, 0)).toBe('1 lançamento');
    expect(quantidadeDeItens(0, 1)).toBe('1 transferência');
    expect(quantidadeDeItens(0, 0)).toBe('nenhum lançamento');
    expect(quantidadeDeItens(1000, 0)).toBe('1.000 lançamentos');
  });

  it('monta a meta da receita, da despesa e da transferência', () => {
    expect(metaDoItem(item({}), false, false)).toBe('Alimentação › Mercado · Banco Horizonte');
    expect(metaDoItem(item({}), true, false)).toBe('Mercado · Banco Horizonte');
    expect(metaDoItem(item({}), false, true)).toBe('Alimentação › Mercado');
    expect(metaDoItem(item({ categoriaId: null, categoriaNome: null, categoriaMaeNome: null }), false, false)).toBe('Banco Horizonte');
    const transferencia = item({ tipo: 'TRANSFERENCIA', valor: 1000, categoriaId: null, categoriaNome: null, categoriaMaeNome: null,
      contaDestinoId: 2, contaDestinoNome: 'Poupança' });
    expect(metaDoItem(transferencia, false, false)).toBe('Banco Horizonte → Poupança · fora dos totais');
    expect(metaDoItem(transferencia, true, false)).toBe('Banco Horizonte → Poupança');
    expect(metaDoItem({ ...transferencia, direcao: 'SAIDA' }, false, true)).toBe('Para Poupança · fora dos totais');
    expect(metaDoItem({ ...transferencia, direcao: 'ENTRADA' }, true, true)).toBe('De Banco Horizonte');
  });

  it('monta a meta da parcela e do estorno no cartão (Fatia 05a, decisão 22)', () => {
    const parcela = item({ data: '2026-12-10', contaId: null as unknown as number, contaNome: null as unknown as string, categoriaNome: 'Compras',
      categoriaMaeNome: null, cartaoId: 2, cartaoNome: 'Cartão Horizonte', compraId: 9, estorno: false, parcela: 1, parcelas: 3,
      dataCompra: '2026-11-05' });
    expect(metaDoItem(parcela, false, false)).toBe('Compras · Cartão Horizonte · compra em 05/11');
    expect(metaDoItem(parcela, true, false)).toBe('Cartão Horizonte · compra em 05/11');
    expect(metaDoItem(parcela, false, true)).toBe('Compras · compra em 05/11');
    const estorno = { ...parcela, tipo: 'RECEITA' as const, estorno: true, categoriaNome: null, categoriaId: null, dataCompra: '2026-11-11' };
    expect(metaDoItem(estorno, false, false)).toBe('Cartão Horizonte · em 11/11');
  });

  it('lista as categorias com as subcategorias recuadas e as arquivadas à parte', () => {
    const alimentacao = categoria(1, 'Alimentação', [categoria(2, 'Mercado'), categoria(3, 'Restaurante')]);
    expect(opcoesDeCategoria({ ativas: [alimentacao, categoria(4, 'Lazer')], arquivadas: [] })).toEqual([
      { id: 1, rotulo: 'Alimentação' }, { id: 2, rotulo: '\u2003Mercado' }, { id: 3, rotulo: '\u2003Restaurante' }, { id: 4, rotulo: 'Lazer' },
    ]);
    expect(opcoesDeCategoriasArquivadas({ ativas: [], arquivadas: [categoria(5, 'Educação')] }, { ativas: [], arquivadas: [] }))
      .toEqual([{ id: 5, rotulo: 'Educação' }]);
  });
});
