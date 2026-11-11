import { describe, expect, it } from 'vitest';

import { nomesEmNegrito, paragrafoDoBloqueio, paragrafosDeArquivar, paragrafosDeExcluir, quantidadeDeSubcategorias } from './frases';

describe('frases das categorias', () => {
  it('junta os nomes com vírgula e "e", em negrito', () => {
    expect(nomesEmNegrito(['Aluguel'])).toEqual([{ forte: 'Aluguel' }]);
    expect(nomesEmNegrito(['Combustível', 'Transporte público'])).toEqual([{ forte: 'Combustível' }, ' e ', { forte: 'Transporte público' }]);
    expect(nomesEmNegrito(['Aluguel', 'Condomínio', 'Contas da casa']))
      .toEqual([{ forte: 'Aluguel' }, ', ', { forte: 'Condomínio' }, ' e ', { forte: 'Contas da casa' }]);
  });

  it('arquivar a principal: o texto do mockup com duas subcategorias, e o singular com uma', () => {
    expect(paragrafosDeArquivar('Transporte', ['Combustível', 'Transporte público'])).toEqual([
      ['As 2 subcategorias ativas também serão arquivadas: ', { forte: 'Combustível' }, ' e ', { forte: 'Transporte público' }, '.'],
      ['Elas somem dos seletores, e os lançamentos continuam nos relatórios. Reativar Transporte traz as três de volta.'],
    ]);
    expect(paragrafosDeArquivar('Lazer', ['Viagens'])).toEqual([
      ['A subcategoria ativa também será arquivada: ', { forte: 'Viagens' }, '.'],
      ['Elas somem dos seletores, e os lançamentos continuam nos relatórios. Reativar Lazer traz as duas de volta.'],
    ]);
    const muitas = Array.from({ length: 11 }, (_, indice) => `Sub ${indice + 1}`);
    expect(paragrafosDeArquivar('Grande', muitas)[1][0]).toContain('traz as 12 de volta.');
  });

  it('excluir: com subcategorias, com uma só e sem nenhuma', () => {
    expect(paragrafosDeExcluir(['Aluguel', 'Condomínio', 'Contas da casa'])).toEqual([
      ['As 3 subcategorias também serão excluídas: ', { forte: 'Aluguel' }, ', ', { forte: 'Condomínio' }, ' e ', { forte: 'Contas da casa' }, '.'],
      [{ forte: 'Não dá para desfazer.' }],
    ]);
    expect(paragrafosDeExcluir(['Ração'])[0]).toEqual(['A subcategoria também será excluída: ', { forte: 'Ração' }, '.']);
    expect(paragrafosDeExcluir([])).toEqual([['A categoria sai do Flux de vez. ', { forte: 'Não dá para desfazer.' }]]);
  });

  it('o bloqueio da exclusão: com subcategorias, sem elas e arquivada', () => {
    expect(paragrafoDoBloqueio('Alimentação', true, '42 lançamentos', false)).toEqual([{ forte: 'Alimentação' }, ' e as subcategorias têm 42 lançamentos.',
      ' Para tirá-la dos seletores sem perder o histórico, arquive a categoria.']);
    expect(paragrafoDoBloqueio('Mercado', false, '1 lançamento', true)).toEqual([{ forte: 'Mercado' }, ' tem 1 lançamento.',
      ' Ela continua arquivada, com os lançamentos nos relatórios.']);
  });

  it('o bloqueio com regras de categorização (Fatia 04b): arquivada, as regras ficam pausadas', () => {
    expect(paragrafoDoBloqueio('Transporte', true, '42 lançamentos e 2 regras', false)).toEqual([{ forte: 'Transporte' },
      ' e as subcategorias têm 42 lançamentos e 2 regras.', ' Para tirá-la dos seletores sem perder o histórico, arquive a categoria.',
      ' Arquivada, as 2 regras dela ficam pausadas até você reativá-la.']);
    expect(paragrafoDoBloqueio('Lazer', false, '1 regra', false)).toEqual([{ forte: 'Lazer' }, ' tem 1 regra.',
      ' Para tirá-la dos seletores sem perder o histórico, arquive a categoria.', ' Arquivada, a regra dela fica pausada até você reativá-la.']);
    expect(paragrafoDoBloqueio('Lazer', false, '3 lançamentos e 1 regra', true)).toEqual([{ forte: 'Lazer' }, ' tem 3 lançamentos e 1 regra.',
      ' Ela continua arquivada, com os lançamentos nos relatórios.']);
  });

  it('o bloqueio com outro uso depois das regras (os orçamentos da Fatia 07): a frase das regras continua', () => {
    expect(paragrafoDoBloqueio('Transporte', false, '2 regras e 1 orçamento', false)).toEqual([{ forte: 'Transporte' },
      ' tem 2 regras e 1 orçamento.', ' Para tirá-la dos seletores sem perder o histórico, arquive a categoria.',
      ' Arquivada, as 2 regras dela ficam pausadas até você reativá-la.']);
    expect(paragrafoDoBloqueio('Lazer', true, '42 lançamentos, 1 regra e 3 orçamentos', false).at(-1))
      .toBe(' Arquivada, a regra dela fica pausada até você reativá-la.');
  });

  it('conta as subcategorias no singular e no plural', () => {
    expect(quantidadeDeSubcategorias(1)).toBe('1 subcategoria');
    expect(quantidadeDeSubcategorias(3)).toBe('3 subcategorias');
  });
});
