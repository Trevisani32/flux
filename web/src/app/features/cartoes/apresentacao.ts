import { dataCurta } from '../../shared/datas/data-curta';
import { formatarMoeda, partesDaMoeda, SEM_VALOR } from '../../shared/moeda/moeda';
import { nomeDoMes } from '../lancamentos/apresentacao';
import { Cartao, ItemDaFatura, SituacaoDaFatura } from './cartoes.service';
import { PreviaDaCompra } from './compras.service';

const MESES_CURTOS = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];

/** Decisão 10 da 05a: o nome da situação na pílula. */
export const ROTULO_DA_SITUACAO: Readonly<Record<SituacaoDaFatura, string>> = { FUTURA: 'Futura', ABERTA: 'Aberta', FECHADA: 'Fechada' };

/** "2026-12-03" vira "03/12". */
export function diaEMes(dataIso: string): string {
  return dataCurta(dataIso).slice(0, 5);
}

/** "2026-12" vira "dezembro". */
export function mesSemAno(mes: string): string {
  return nomeDoMes(mes).split(' ')[0];
}

/**
 * Um mês ou uma faixa de meses, com o ano quando `anoNoTexto`: "dezembro", "janeiro a março"; com o ano, "dezembro de
 * 2016", "janeiro a março de 2027" ou, cruzando anos, "dezembro de 2016 a agosto de 2027".
 */
export function faixaDeMeses(de: string, ate: string, anoNoTexto: boolean): string {
  if (de === ate) {
    return anoNoTexto ? nomeDoMes(de) : mesSemAno(de);
  }
  if (!anoNoTexto) {
    return `${mesSemAno(de)} a ${mesSemAno(ate)}`;
  }
  return de.slice(0, 4) === ate.slice(0, 4) ? `${mesSemAno(de)} a ${nomeDoMes(ate)}` : `${nomeDoMes(de)} a ${nomeDoMes(ate)}`;
}

/** O ano entra no texto quando a fatura não é do ano de hoje ou quando a faixa cruza anos ("2016-11", "2027-08"). */
export function comAno(hoje: string, ...meses: readonly string[]): boolean {
  return meses.some((mes) => mes.slice(0, 4) !== meses[0].slice(0, 4)) || meses[0].slice(0, 4) !== hoje.slice(0, 4);
}

/** "2026-12" vira "dez/2026". */
export function mesCurto(mes: string): string {
  return `${MESES_CURTOS[Number(mes.slice(5, 7)) - 1]}/${mes.slice(0, 4)}`;
}

/** "1 cartão ativo", "2 cartões ativos". */
export function quantidadeDeCartoesAtivos(quantidade: number): string {
  return quantidade === 1 ? '1 cartão ativo' : `${quantidade} cartões ativos`;
}

/** "Fecha dia 3 · vence dia 10 · paga com Banco Horizonte"; curto, "Fecha 3 · vence 10 · Banco Horizonte". */
export function diasDoCartao(cartao: Cartao, curto: boolean): string {
  return curto ? `Fecha ${cartao.diaFechamento} · vence ${cartao.diaVencimento} · ${cartao.contaPagamentoNome}`
    : `Fecha dia ${cartao.diaFechamento} · vence dia ${cartao.diaVencimento} · paga com ${cartao.contaPagamentoNome}`;
}

/** Decisão 11: o total da fatura, "R$ 1.113,69", ou "Crédito de R$ 30,00" quando os estornos passam das compras. */
export function textoDoTotal(total: number): string {
  const partes = partesDaMoeda(total);
  if (partes === null) {
    return SEM_VALOR;
  }
  return partes.negativo ? `Crédito de ${partes.absoluto}` : partes.absoluto;
}

/** "1/3" na compra parcelada; nulo à vista e no estorno. */
export function rotuloDaParcela(parcela: number, parcelas: number): string | null {
  return parcelas > 1 ? `${parcela}/${parcelas}` : null;
}

/** A linha de baixo do item da fatura: "Crédito na fatura", "À vista" ou "R$ 450,00 em 3x · no Flux a partir da 6ª". */
export function metaDoItemDaFatura(item: ItemDaFatura): string {
  if (item.tipo === 'ESTORNO') {
    return 'Crédito na fatura';
  }
  if (item.parcelas === 1) {
    return 'À vista';
  }
  const emAndamento = item.parcelaInicial > 1 ? ` · no Flux a partir da ${item.parcelaInicial}ª` : '';
  return `${formatarMoeda(item.valorTotal)} em ${item.parcelas}x${emAndamento}`;
}

/** A categoria como a tela escreve: "Alimentação › Mercado" ou "Sem categoria". */
export function textoDaCategoria(nome: string | null, nomeDaMae: string | null): string {
  if (nome === null) {
    return 'Sem categoria';
  }
  return nomeDaMae === null ? nome : `${nomeDaMae} › ${nome}`;
}

/** Dias de `de` a `ate` (datas ISO), sem fuso: "2026-11-12" a "2026-12-03" dá 21. */
export function diasEntre(de: string, ate: string): number {
  const [anoDe, mesDe, diaDe] = de.split('-').map(Number);
  const [anoAte, mesAte, diaAte] = ate.split('-').map(Number);
  return Math.round((Date.UTC(anoAte, mesAte - 1, diaAte) - Date.UTC(anoDe, mesDe - 1, diaDe)) / 86_400_000);
}

/** A data ISO `dias` dias depois (ou antes, negativo): "2026-12-03" e 1 dão "2026-12-04". */
export function somarDias(dataIso: string, dias: number): string {
  const [ano, mes, dia] = dataIso.split('-').map(Number);
  return new Date(Date.UTC(ano, mes - 1, dia + dias)).toISOString().slice(0, 10);
}

/** "Fecha em 21 dias", "Fecha amanhã" ou "Fecha hoje", na fatura aberta. */
export function quandoFecha(hoje: string, fechamento: string): string {
  const dias = diasEntre(hoje, fechamento);
  return dias === 0 ? 'Fecha hoje' : dias === 1 ? 'Fecha amanhã' : `Fecha em ${dias} dias`;
}

export interface LinhaDaPrevia {
  readonly rotulo: string;
  readonly valor: string;
  /** A linha das parcelas de antes da primeira no Flux (decisão 17), riscada. */
  readonly fora: boolean;
}

/** Os textos da prévia da compra (decisão 18). */
export interface TextoDaPrevia {
  readonly titulo: string;
  readonly linha: string | null;
  readonly lista: readonly LinhaDaPrevia[];
  readonly nota: string | null;
}

/**
 * Decisão 16: a data da compra e a do estorno vão até 10 anos antes de hoje ("2026-11-12" dá "2016-11-12"); o 29 de
 * fevereiro vira 28 quando o ano de destino não é bissexto, como no servidor.
 */
export function dataMinimaDaCompra(hoje: string): string {
  const [ano, mes, dia] = hoje.split('-').map(Number);
  const ultimoDia = new Date(Date.UTC(ano - 10, mes, 0)).getUTCDate();
  return `${ano - 10}-${String(mes).padStart(2, '0')}-${String(Math.min(dia, ultimoDia)).padStart(2, '0')}`;
}

/**
 * A dica do estorno (decisão 20), com a fatura que a prévia achou para a data dele, quando já há uma; com o ano quando
 * ela não é do ano de hoje.
 */
export function dicaDoEstorno(fatura: { readonly mes: string; readonly vencimento: string } | null, hoje: string): string {
  if (fatura === null) {
    return 'Cai na fatura da data do estorno e abate o total. Devolveu a compra inteira antes de pagar? Exclua a compra.';
  }
  const ano = comAno(hoje, fatura.mes);
  const vence = ano ? dataCurta(fatura.vencimento) : diaEMes(fatura.vencimento);
  return `Cai na fatura de ${faixaDeMeses(fatura.mes, fatura.mes, ano)} (vence ${vence}) e abate o total. Devolveu a compra inteira antes `
    + 'de pagar? Exclua a compra.';
}

/**
 * A prévia como a tela escreve, sem conta com dinheiro: os valores e as faturas vêm da API. À vista, a fatura;
 * parcelada com parcelas iguais e desde a 1ª, "10x de R$ 199,00" e a primeira e a última; senão, a lista. Com a
 * fatura de outro ano, os meses e as datas levam o ano ("novembro de 2016", "27/11/2016").
 */
export function textoDaPrevia(previa: PreviaDaCompra, hoje: string): TextoDaPrevia {
  const fatura = previa.primeiraFatura;
  const todas = previa.parcelas;
  const ano = comAno(hoje, fatura.mes);
  if (todas.length === 1) {
    const data = ano ? dataCurta : diaEMes;
    const linha = fatura.situacao === 'FECHADA'
      ? `Fechou em ${data(fatura.fechamento)} e ${fatura.vencimento < hoje ? 'venceu' : 'vence'} em ${data(fatura.vencimento)}: `
        + 'a compra entra nela mesmo assim.'
      : `Fecha em ${data(fatura.fechamento)} e vence em ${data(fatura.vencimento)}.`;
    return { titulo: `À vista, na fatura de ${faixaDeMeses(fatura.mes, fatura.mes, ano)}`, linha, lista: [], nota: null };
  }
  const noFlux = todas.filter((parcela) => parcela.noFlux);
  const fora = todas.filter((parcela) => !parcela.noFlux);
  const valor = formatarMoeda(noFlux[noFlux.length - 1].valor);
  const iguais = noFlux.every((parcela) => formatarMoeda(parcela.valor) === valor);
  if (fora.length === 0 && iguais) {
    const primeira = noFlux[0];
    const ultima = noFlux[noFlux.length - 1];
    return {
      titulo: `${todas.length}x de ${valor}`,
      linha: `1ª na fatura de ${mesSemAno(primeira.mes)} (vence ${dataCurta(primeira.vencimento)}) · última em ${nomeDoMes(ultima.mes)}.`,
      lista: [],
      nota: null,
    };
  }
  const quantas = noFlux.length === 1 ? '1 parcela' : `${noFlux.length} parcelas`;
  const titulo = fora.length === 0 ? `${todas.length} parcelas` : iguais ? `${quantas} de ${valor} no Flux` : `${quantas} no Flux`;
  const lista: LinhaDaPrevia[] = [];
  if (fora.length > 0) {
    const de = fora[0].mes;
    const ate = fora[fora.length - 1].mes;
    lista.push({ rotulo: fora.length === 1 ? '1ª' : `1ª a ${fora.length}ª`, valor: faixaDeMeses(de, ate, comAno(hoje, de, ate)), fora: true });
  }
  noFlux.forEach((parcela) => lista.push({ rotulo: `${parcela.numero}ª · ${mesCurto(parcela.mes)}`, valor: formatarMoeda(parcela.valor), fora: false }));
  const nota = fora.length > 0
    ? `A 1ª caiu na fatura de ${faixaDeMeses(fatura.mes, fatura.mes, ano)}. As de antes da ${noFlux[0].numero}ª ficam fora do Flux.`
    : 'O que sobra da divisão vai para a 1ª.';
  return { titulo, linha: null, lista, nota };
}
