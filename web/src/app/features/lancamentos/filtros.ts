import { ParamMap, Params } from '@angular/router';

export type TipoDoFiltro = 'receita' | 'despesa' | 'transferencia';

/**
 * O mês ("2026-11") e os filtros da lista. Vêm da URL e vão para a API com os mesmos
 * nomes (decisão 13): `?mes=2026-11&conta=3&categoria=12&tipo=despesa&busca=mercado`. Fatia 05a
 * (decisão 23): `?cartao=2` no lugar de `?conta=`, nunca os dois.
 */
export interface Filtros {
  readonly mes: string;
  readonly conta: number | null;
  /** Opcional: quem monta os filtros sem ele (a importação, desde a 04a) fica sem cartão. */
  readonly cartao?: number | null;
  /** Uma categoria (a principal leva as subcategorias, decisão 11) ou "sem" (Sem categoria). */
  readonly categoria: number | 'sem' | null;
  readonly tipo: TipoDoFiltro | null;
  readonly busca: string;
}

const MES = /^\d{4}-(0[1-9]|1[0-2])$/;
const ID = /^[1-9]\d{0,14}$/;
const TIPOS: readonly TipoDoFiltro[] = ['receita', 'despesa', 'transferencia'];
/** O mesmo limite da API. */
export const TAMANHO_MAXIMO_DA_BUSCA = 100;

/** O mês de uma data ISO: "2026-11-09" vira "2026-11". */
export function mesDe(dataIso: string): string {
  return dataIso.slice(0, 7);
}

/**
 * Lê a URL. O que não se entende é ignorado; sem mês (ou com um mês inválido), vale o de hoje. Com conta e cartão
 * juntos, vale a conta.
 */
export function filtrosDaUrl(parametros: ParamMap, hoje: string): Filtros {
  const mes = parametros.get('mes');
  const conta = parametros.get('conta');
  const cartao = parametros.get('cartao');
  const categoria = parametros.get('categoria');
  const tipo = parametros.get('tipo');
  const daConta = conta !== null && ID.test(conta) ? Number(conta) : null;
  return {
    mes: mes !== null && MES.test(mes) ? mes : mesDe(hoje),
    conta: daConta,
    cartao: daConta === null && cartao !== null && ID.test(cartao) ? Number(cartao) : null,
    categoria: categoria === 'sem' ? 'sem' : categoria !== null && ID.test(categoria) ? Number(categoria) : null,
    tipo: TIPOS.find((item) => item === tipo) ?? null,
    busca: (parametros.get('busca') ?? '').trim().slice(0, TAMANHO_MAXIMO_DA_BUSCA),
  };
}

/** Parâmetros para a URL. O padrão fica de fora (null): sem filtro e o mês de hoje. */
export function parametrosDaUrl(filtros: Filtros, hoje: string): Params {
  return {
    mes: filtros.mes === mesDe(hoje) ? null : filtros.mes,
    conta: filtros.conta,
    cartao: filtros.cartao ?? null,
    categoria: filtros.categoria,
    tipo: filtros.tipo,
    busca: filtros.busca === '' ? null : filtros.busca,
  };
}

/** Parâmetros de GET /api/lancamentos: o mês sempre; os filtros só quando têm valor. */
export function parametrosDaApi(filtros: Filtros): Record<string, string> {
  const parametros: Record<string, string> = { mes: filtros.mes };
  if (filtros.conta !== null) {
    parametros['conta'] = String(filtros.conta);
  }
  if (filtros.cartao !== undefined && filtros.cartao !== null) {
    parametros['cartao'] = String(filtros.cartao);
  }
  if (filtros.categoria !== null) {
    parametros['categoria'] = String(filtros.categoria);
  }
  if (filtros.tipo !== null) {
    parametros['tipo'] = filtros.tipo;
  }
  if (filtros.busca !== '') {
    parametros['busca'] = filtros.busca;
  }
  return parametros;
}

/** Algum filtro além do mês. */
export function temFiltro(filtros: Filtros): boolean {
  return filtros.conta !== null || (filtros.cartao ?? null) !== null || filtros.categoria !== null || filtros.tipo !== null || filtros.busca !== '';
}

/** O mês anterior (-1) ou o seguinte (1): "2026-12" e 1 dão "2027-01". */
export function mesVizinho(mes: string, passo: -1 | 1): string {
  const [ano, numero] = mes.split('-').map(Number);
  const indice = ano * 12 + numero - 1 + passo;
  return `${Math.floor(indice / 12)}-${String((indice % 12) + 1).padStart(2, '0')}`;
}

/** O último mês que as setas alcançam: 12 meses depois do mês de hoje (decisões 14 e 21). */
export function mesLimite(hoje: string): string {
  const [ano, numero] = mesDe(hoje).split('-');
  return `${Number(ano) + 1}-${numero}`;
}
