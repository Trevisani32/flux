import { dataCurta } from '../../shared/datas/data-curta';
import { GrupoDeCategorias } from '../categorias/categorias.service';
import { ItemDaLista } from './lancamentos.service';

const MESES = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'];
const MESES_CURTOS = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];
const DIAS = ['Domingo', 'Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado'];
const DIAS_CURTOS = ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb'];
const INTEIRO = new Intl.NumberFormat('pt-BR');

/** "2026-11" vira "novembro de 2026". */
export function nomeDoMes(mes: string): string {
  const [ano, numero] = mes.split('-').map(Number);
  return `${MESES[numero - 1]} de ${ano}`;
}

/** "2026-11" vira "Novembro". */
export function nomeDoMesSemAno(mes: string): string {
  const nome = MESES[Number(mes.slice(5, 7)) - 1];
  return nome.charAt(0).toLocaleUpperCase('pt-BR') + nome.slice(1);
}

/** "Sexta, 6 de novembro" ou, curto, "Sex, 6 nov". O dia 1 é "1º". Sem Date local: não há fuso. */
export function rotuloDoDia(dataIso: string, curto: boolean): string {
  const [ano, mes, dia] = dataIso.split('-').map(Number);
  const semana = new Date(Date.UTC(ano, mes - 1, dia)).getUTCDay();
  const numero = dia === 1 ? '1º' : String(dia);
  return curto ? `${DIAS_CURTOS[semana]}, ${numero} ${MESES_CURTOS[mes - 1]}` : `${DIAS[semana]}, ${numero} de ${MESES[mes - 1]}`;
}

/** "hoje" e "amanhã" ao lado do dia (decisão 6). */
export function marcaDoDia(dataIso: string, hoje: string): 'hoje' | 'amanhã' | null {
  if (dataIso === hoje) {
    return 'hoje';
  }
  const [ano, mes, dia] = hoje.split('-').map(Number);
  return dataIso === new Date(Date.UTC(ano, mes - 1, dia + 1)).toISOString().slice(0, 10) ? 'amanhã' : null;
}

/** "14 lançamentos e 2 transferências", "1 transferência", "nenhum lançamento". */
export function quantidadeDeItens(lancamentos: number, transferencias: number): string {
  const partes: string[] = [];
  if (lancamentos > 0) {
    partes.push(lancamentos === 1 ? '1 lançamento' : `${INTEIRO.format(lancamentos)} lançamentos`);
  }
  if (transferencias > 0) {
    partes.push(transferencias === 1 ? '1 transferência' : `${INTEIRO.format(transferencias)} transferências`);
  }
  return partes.length === 0 ? 'nenhum lançamento' : partes.join(' e ');
}

/**
 * A meta da linha. Receita e despesa: "Alimentação › Mercado · Banco Horizonte" (curto, só
 * "Mercado"); sem a conta quando a lista já é dela. Transferência: "Banco Horizonte →
 * Poupança" ou, com o filtro de uma das contas, "Para Poupança"/"De Banco Horizonte"; fora do
 * celular, com "· fora dos totais" (decisão 26). Lançamento de fatura (Fatia 05a): a categoria, o cartão
 * (sem ele quando a lista já é dele, `contaFiltrada`) e "compra em 02/11"; curto, sem a categoria.
 */
export function metaDoItem(item: ItemDaLista, curto: boolean, contaFiltrada: boolean): string {
  if (item.tipo === 'TRANSFERENCIA') {
    const contas = item.direcao === 'SAIDA' ? `Para ${item.contaDestinoNome}`
      : item.direcao === 'ENTRADA' ? `De ${item.contaNome}`
        : `${item.contaNome} → ${item.contaDestinoNome}`;
    return curto ? contas : `${contas} · fora dos totais`;
  }
  const categoria = item.categoriaNome === null ? null
    : curto || item.categoriaMaeNome === null ? item.categoriaNome
      : `${item.categoriaMaeNome} › ${item.categoriaNome}`;
  if (item.compraId !== undefined && item.compraId !== null) {
    // Fatia 05a (decisão 22): "Educação · Cartão Horizonte · compra em 02/11"; curto, sem a categoria; o estorno, "em 11/11".
    const quando = `${item.estorno === true ? 'em' : 'compra em'} ${dataCurta(item.dataCompra ?? item.data).slice(0, 5)}`;
    return [curto ? null : categoria, contaFiltrada ? null : item.cartaoNome ?? null, quando].filter((parte) => parte !== null).join(' · ');
  }
  return [categoria, contaFiltrada ? null : item.contaNome].filter((parte) => parte !== null).join(' · ');
}

export interface OpcaoDeCategoria {
  readonly id: number;
  readonly rotulo: string;
}

/**
 * As categorias ativas de uma natureza para um select nativo: cada principal e, logo
 * abaixo, as subcategorias recuadas. A principal também pode ser escolhida (decisão 22 da 03a).
 */
export function opcoesDeCategoria(grupo: GrupoDeCategorias): OpcaoDeCategoria[] {
  return grupo.ativas.flatMap((principal) => [
    { id: principal.id, rotulo: principal.nome },
    ...principal.subcategorias.map((sub) => ({ id: sub.id, rotulo: `\u2003${sub.nome}` })),
  ]);
}

/** As arquivadas das duas naturezas, para o grupo "Arquivadas" do filtro (decisão 24). */
export function opcoesDeCategoriasArquivadas(despesas: GrupoDeCategorias, receitas: GrupoDeCategorias): OpcaoDeCategoria[] {
  return [...despesas.arquivadas, ...receitas.arquivadas].flatMap((categoria) => [
    { id: categoria.id, rotulo: categoria.nome },
    ...categoria.subcategorias.map((sub) => ({ id: sub.id, rotulo: `\u2003${sub.nome}` })),
  ]);
}
