import { describe, expect, it } from 'vitest';

import { convertida, distancia, metaDoLado, observacaoDosLados, paragrafosDoDesfazer } from './conversao';
import { ItemDaLista, LadoDaTransferencia } from './lancamentos.service';

function lado(parcial: Partial<LadoDaTransferencia>): LadoDaTransferencia {
  return { contaId: 1, contaNome: 'Banco Horizonte', data: '2026-12-02', origem: 'IMPORTADO', descricaoBanco: 'TED 341.0001DEV FICTICIO',
    observacao: null, ...parcial };
}

function transferencia(lados: readonly LadoDaTransferencia[] | null | undefined): ItemDaLista {
  return { tipo: 'TRANSFERENCIA', id: 40, data: '2026-12-02', descricao: 'Transferência', valor: 1000, observacao: null, contaId: 1,
    contaNome: 'Banco Horizonte', categoriaId: null, categoriaNome: null, categoriaMaeNome: null, categoriaArquivada: false, contaDestinoId: 2,
    contaDestinoNome: 'Conta conjunta', direcao: null, futuro: false, somenteLeitura: false, origem: 'IMPORTADO', descricaoBanco: null, lados };
}

describe('conversão em transferência (Fatia 04c)', () => {
  it('a convertida tem um lado importado; a manual, a receita e o item sem lados não são', () => {
    const entrada = lado({ contaId: 2, contaNome: 'Conta conjunta', data: '2026-12-03', origem: 'MANUAL', descricaoBanco: null });
    expect(convertida(transferencia([lado({}), entrada]))).toBe(true);
    expect(convertida(transferencia([lado({ origem: 'MANUAL' }), entrada]))).toBe(false);
    expect(convertida(transferencia(undefined))).toBe(false);
    expect(convertida({ ...transferencia([lado({})]), tipo: 'DESPESA' })).toBe(false);
    expect(metaDoLado(lado({}))).toBe('02/12/2026 · importado');
    expect(metaDoLado(entrada)).toBe('03/12/2026 · lançado à mão');
  });

  it('diz a distância em dias, inclusive na virada do mês e do ano, e junta as observações sem repetir', () => {
    expect(distancia('2026-12-02', '2026-12-02')).toBe('no mesmo dia');
    expect(distancia('2026-12-02', '2026-12-03')).toBe('1 dia depois');
    expect(distancia('2026-12-30', '2027-01-03')).toBe('4 dias depois');
    expect(distancia('2026-03-02', '2026-02-26')).toBe('4 dias antes');
    expect(observacaoDosLados([lado({ observacao: ' Passagens ' }), lado({ observacao: 'Passagens' })])).toBe('Passagens');
    expect(observacaoDosLados([lado({ observacao: 'Passagens' }), lado({ observacao: 'Hotel' })])).toBe('Passagens\nHotel');
    expect(observacaoDosLados([lado({ observacao: null }), lado({ observacao: '  ' })])).toBe('');
  });

  it('o desfazer diz o tipo e a conta de cada lado e o que acontece com a descrição', () => {
    const dois = paragrafosDoDesfazer(transferencia([lado({}), lado({ contaId: 2, contaNome: 'Conta conjunta' })]));
    expect(dois).toEqual([
      ['Os dois lançamentos voltam a ser uma ', { forte: 'despesa em Banco Horizonte' }, ' e uma ', { forte: 'receita em Conta conjunta' },
        ', sem categoria e com a descrição do extrato.'],
      ['Nenhum deles é apagado. Se quiser, aplique as regras depois.']]);
    const um = paragrafosDoDesfazer({ ...transferencia([lado({}), lado({ contaId: 3, contaNome: 'Carteira', origem: 'MANUAL' })]),
      contaDestinoId: 3, contaDestinoNome: 'Carteira' });
    expect(um[0][4]).toBe(', sem categoria. O importado volta com a descrição do extrato, e o lançado à mão fica com a descrição que tem.');
    expect(um[0][3]).toEqual({ forte: 'receita em Carteira' });
  });
});