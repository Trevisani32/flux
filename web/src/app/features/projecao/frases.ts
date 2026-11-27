import { TrechoDeTexto } from '../../shared/confirmacao/confirmacao-dialogo';
import { dataCurta } from '../../shared/datas/data-curta';
import { formatarMoeda } from '../../shared/moeda/moeda';
import { comAno, faixaDeMeses, nomeDaFatura } from '../cartoes/apresentacao';
import {
  FaturaFora,
  ItemDaProjecao,
  PrevistoDaFatura,
  TrechoNegativo,
} from './projecao.service';

const DIAS = ['domingo', 'segunda', 'terça', 'quarta', 'quinta', 'sexta', 'sábado'];
const DIAS_CURTOS = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'];

function diaDaSemana(data: string): number {
  const [ano, mes, dia] = data.split('-').map(Number);
  return new Date(Date.UTC(ano, mes - 1, dia)).getUTCDay();
}

function diaCurto(data: string, hoje: string): string {
  return data.slice(0, 4) === hoje.slice(0, 4) ? dataCurta(data).slice(0, 5) : dataCurta(data);
}

export function quandoNegativo(trecho: TrechoNegativo, hoje: string, ate: string): string {
  if (trecho.ate === ate) {
    return `a partir de ${trecho.de === hoje ? 'hoje' : diaCurto(trecho.de, hoje)}`;
  }
  if (trecho.de === trecho.ate) {
    return trecho.de === hoje ? 'hoje' : `em ${diaCurto(trecho.de, hoje)}`;
  }
  const de = trecho.de === hoje ? 'hoje' : diaCurto(trecho.de, hoje);
  return `de ${de} a ${diaCurto(trecho.ate, hoje)}`;
}

export function tituloDoNegativo(nome: string, trecho: TrechoNegativo, hoje: string, ate: string): string {
  return `${nome} fica no negativo ${quandoNegativo(trecho, hoje, ate)}.`;
}

export function naData(data: string, hoje: string): string {
  if (data === hoje) {
    return 'hoje';
  }
  const dia = diaDaSemana(data);
  const artigo = dia === 0 || dia === 6 ? 'no' : 'na';
  return `${artigo} ${DIAS[dia]}, ${diaCurto(data, hoje)}`;
}

export function textoDoMenor(
  menor: number,
  dia: string,
  hoje: string,
  trechos: readonly TrechoNegativo[],
  ate: string,
): TrechoDeTexto[] {
  const texto: TrechoDeTexto[] = ['O menor saldo é ', { forte: formatarMoeda(menor) }, `, ${naData(dia, hoje)}.`];
  if (trechos.length > 1) {
    texto.push(` Fica no negativo de novo ${quandoNegativo(trechos[1], hoje, ate)}.`);
  }
  return texto;
}

export function tituloDoAviso(nome: string, primeiro: string, saldoHoje: number, hoje: string): string {
  if (saldoHoje < 0) {
    return `${nome} está no negativo hoje`;
  }
  return primeiro === hoje ? `${nome} pode ficar no negativo hoje` : `${nome} pode ficar no negativo em ${diaCurto(primeiro, hoje)}`;
}

export function textoDoAviso(menor: number, dia: string, hoje: string, outras: number): string {
  const em = dia === hoje ? 'hoje' : `em ${diaCurto(dia, hoje)}`;
  const resto = outras === 0 ? '' : outras === 1 ? ' Mais 1 conta pode ficar no negativo.'
    : ` Mais ${outras} contas podem ficar no negativo.`;
  return `O menor saldo previsto é ${formatarMoeda(menor)}, ${em}.${resto}`;
}

export function notaDoMenor(dia: string, dias: number, hoje: string, curta: boolean): string {
  if (dia === hoje) {
    return curta ? 'hoje' : `hoje${dias > 0 ? ` · negativo por ${dias} ${dias === 1 ? 'dia' : 'dias'}` : ''}`;
  }
  const nome = curta ? DIAS_CURTOS[diaDaSemana(dia)] : DIAS[diaDaSemana(dia)];
  const negativo = curta || dias === 0 ? '' : ` · negativo por ${dias} ${dias === 1 ? 'dia' : 'dias'}`;
  return `${nome}, ${diaCurto(dia, hoje)}${negativo}`;
}

export function rotuloDoFim(ate: string, hoje: string, curto: boolean): string {
  return `${curto ? 'Em' : 'No fim,'} ${diaCurto(ate, hoje)}`;
}

export function notaDoFim(dias: number): string {
  return `${dias} ${dias === 1 ? 'dia' : 'dias'} depois de hoje`;
}

export function notaDoSaldoHoje(nomes: readonly string[], hoje: string): string {
  const quantidade = nomes.length === 1 ? nomes[0] : `${nomes.length} contas`;
  return `${quantidade} · ${DIAS[diaDaSemana(hoje)]}, ${diaCurto(hoje, hoje)}`;
}

export function periodoDoGrafico(hoje: string, ate: string): string {
  return `De hoje, ${diaCurto(hoje, hoje)}, a ${dataCurta(ate)}`;
}

export function tituloDaFatura(mes: string, cartao: string, hoje: string): string {
  return `Fatura de ${nomeDaFatura(mes, hoje)} do ${cartao}`;
}

export function textoDaFatura(item: Pick<ItemDaProjecao, 'mes' | 'cartaoNome' | 'previsto' | 'previstos'>, hoje: string): string {
  const titulo = tituloDaFatura(item.mes!, item.cartaoNome!, hoje);
  if (item.previstos.length === 0) {
    return titulo;
  }
  if (item.previstos.length === 1) {
    return `${titulo}, com o previsto de ${item.previstos[0].descricao} (${formatarMoeda(item.previsto)})`;
  }
  return `${titulo}, com ${item.previstos.length} previstos (${formatarMoeda(item.previsto)})`;
}

export function textoDaTransferencia(item: Pick<ItemDaProjecao, 'descricao' | 'valor' | 'outraContaNome'>): string {
  return `${item.descricao}, ${item.valor < 0 ? 'para' : 'de'} ${item.outraContaNome}`;
}

export function metaDaPendencia(
  item: Pick<ItemDaProjecao, 'tipo' | 'vencimento' | 'pendente' | 'data' | 'contaNome'>,
  hoje: string,
  variasContas: boolean,
): { readonly pilula: string | null; readonly texto: string } {
  if (item.tipo === 'FATURA') {
    const venceu = item.vencimento! < hoje;
    const texto = `${venceu ? 'venceu em' : 'vence hoje'} ${venceu ? diaCurto(item.vencimento!, hoje) : ''}`.trim();
    return { pilula: venceu && item.pendente! > 0 ? 'Venceu' : null, texto: `${texto} · sai de ${item.contaNome}` };
  }
  if (item.data < hoje) {
    return { pilula: 'Atrasada', texto: `desde ${diaCurto(item.data, hoje)}${variasContas ? ` · ${item.contaNome}` : ''}` };
  }
  return { pilula: 'Previsto', texto: `hoje${variasContas ? ` · ${item.contaNome}` : ''}` };
}

export function linhaDaPrevistaNaFatura(prevista: PrevistoDaFatura, hoje: string): string {
  return `${prevista.descricao} · compra prevista em ${diaCurto(prevista.data, hoje)}`;
}

export function rodapeDoPorConta(fora: readonly string[]): string {
  const inicio = 'O menor de cada conta é o dela; o total não é a soma dos menores.';
  if (fora.length === 0) {
    return inicio;
  }
  const nomes = fora.length === 1 ? fora[0] : fora.length === 2 ? `${fora[0]} e ${fora[1]}`
    : `${fora.slice(0, -1).join(', ')} e ${fora.at(-1)}`;
  return `${inicio} ${nomes} ${fora.length === 1 ? 'fica' : 'ficam'} fora: marque para incluir.`;
}

export function subtituloDoCelular(ate: string, escolhidas: number, ativas: number, hoje: string): string {
  return `Até ${diaCurto(ate, hoje)} · ${escolhidas} de ${ativas} ${ativas === 1 ? 'conta' : 'contas'}`;
}

export function maisDias(quantidade: number, ate: string, hoje: string): string {
  return `e mais ${quantidade} ${quantidade === 1 ? 'dia' : 'dias'} até ${diaCurto(ate, hoje)}`;
}

export function tituloDasFaturasFora(fatura: Pick<FaturaFora, 'meses'>): string {
  return fatura.meses.length === 1 ? 'Uma fatura ficou fora' : `${fatura.meses.length} faturas ficaram fora`;
}

export function textoDasFaturasFora(
  fatura: Pick<FaturaFora, 'meses' | 'cartaoNome' | 'contaNome' | 'valor'>,
  hoje: string,
): string {
  const meses = faixaDeMeses(fatura.meses[0], fatura.meses.at(-1)!, comAno(hoje, fatura.meses[0], fatura.meses.at(-1)!));
  if (fatura.meses.length === 1) {
    return `A fatura de ${meses} do ${fatura.cartaoNome} (${formatarMoeda(fatura.valor)}) sai de ${fatura.contaNome}, `
      + 'que está arquivada: ela ficou fora da projeção. Troque a conta de pagamento do cartão.';
  }
  return `As faturas de ${meses} do ${fatura.cartaoNome} (${formatarMoeda(fatura.valor)}) saem de ${fatura.contaNome}, `
    + 'que está arquivada: elas ficaram fora da projeção. Troque a conta de pagamento do cartão.';
}
