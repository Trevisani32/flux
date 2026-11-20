import { convertToParamMap } from '@angular/router';
import { describe, expect, it } from 'vitest';

import { filtrosDaUrl, mesLimite, mesVizinho, parametrosDaApi, parametrosDaUrl, temFiltro } from './filtros';

const HOJE = '2026-11-09';

describe('filtros', () => {
  it('lê a URL, com o mês de hoje como padrão', () => {
    expect(filtrosDaUrl(convertToParamMap({}), HOJE)).toEqual({ mes: '2026-11', conta: null, cartao: null, categoria: null, tipo: null, busca: '' });
    expect(filtrosDaUrl(convertToParamMap({ mes: '2026-10', conta: '3', categoria: '12', tipo: 'despesa', busca: '  mercado ' }), HOJE))
      .toEqual({ mes: '2026-10', conta: 3, cartao: null, categoria: 12, tipo: 'despesa', busca: 'mercado' });
    expect(filtrosDaUrl(convertToParamMap({ categoria: 'sem', tipo: 'transferencia' }), HOJE).categoria).toBe('sem');
  });

  it('ignora o que não entende na URL', () => {
    expect(filtrosDaUrl(convertToParamMap({ mes: '2026-13', conta: 'abc', categoria: '-1', tipo: 'DESPESA', busca: 'x'.repeat(150) }), HOJE))
      .toEqual({ mes: '2026-11', conta: null, cartao: null, categoria: null, tipo: null, busca: 'x'.repeat(100) });
    expect(filtrosDaUrl(convertToParamMap({ conta: '0' }), HOJE).conta).toBeNull();
  });

  it('lê o cartão (Fatia 05a, decisão 23); com conta e cartão juntos, vale a conta', () => {
    expect(filtrosDaUrl(convertToParamMap({ cartao: '2' }), HOJE)).toMatchObject({ conta: null, cartao: 2 });
    expect(filtrosDaUrl(convertToParamMap({ conta: '3', cartao: '2' }), HOJE)).toMatchObject({ conta: 3, cartao: null });
    expect(filtrosDaUrl(convertToParamMap({ cartao: 'x' }), HOJE).cartao).toBeNull();
    expect(parametrosDaUrl({ mes: '2026-11', conta: null, cartao: 2, categoria: null, tipo: null, busca: '' }, HOJE)).toMatchObject({ cartao: 2 });
    expect(parametrosDaApi({ mes: '2026-12', conta: null, cartao: 2, categoria: null, tipo: null, busca: '' }))
      .toEqual({ mes: '2026-12', cartao: '2' });
    expect(temFiltro({ mes: '2026-10', conta: null, cartao: 2, categoria: null, tipo: null, busca: '' })).toBe(true);
  });

  it('a URL fica sem o padrão: sem filtro e sem o mês de hoje', () => {
    expect(parametrosDaUrl({ mes: '2026-11', conta: null, cartao: null, categoria: null, tipo: null, busca: '' }, HOJE))
      .toEqual({ mes: null, conta: null, cartao: null, categoria: null, tipo: null, busca: null });
    expect(parametrosDaUrl({ mes: '2026-10', conta: 3, cartao: null, categoria: 'sem', tipo: 'receita', busca: 'salário' }, HOJE))
      .toEqual({ mes: '2026-10', conta: 3, cartao: null, categoria: 'sem', tipo: 'receita', busca: 'salário' });
  });

  it('a API recebe o mês sempre e os filtros só com valor', () => {
    expect(parametrosDaApi({ mes: '2026-11', conta: null, cartao: null, categoria: null, tipo: null, busca: '' })).toEqual({ mes: '2026-11' });
    expect(parametrosDaApi({ mes: '2026-10', conta: 3, cartao: null, categoria: 12, tipo: 'transferencia', busca: 'reserva' }))
      .toEqual({ mes: '2026-10', conta: '3', categoria: '12', tipo: 'transferencia', busca: 'reserva' });
    expect(temFiltro({ mes: '2026-10', conta: null, cartao: null, categoria: null, tipo: null, busca: '' })).toBe(false);
    expect(temFiltro({ mes: '2026-10', conta: null, cartao: null, categoria: null, tipo: null, busca: 'x' })).toBe(true);
  });

  it('anda de mês em mês, virando o ano, até 12 meses depois de hoje', () => {
    expect(mesVizinho('2026-12', 1)).toBe('2027-01');
    expect(mesVizinho('2027-01', -1)).toBe('2026-12');
    expect(mesVizinho('2026-11', -1)).toBe('2026-10');
    expect(mesLimite(HOJE)).toBe('2027-11');
    expect(mesLimite('2026-01-31')).toBe('2027-01');
  });
});
