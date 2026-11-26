import { TrechoDeTexto } from '../../shared/confirmacao/confirmacao-dialogo';
import { dataCurta } from '../../shared/datas/data-curta';
import { diaEMes, mesSemAno } from '../cartoes/apresentacao';
import { nomesEmNegrito } from '../categorias/frases';
import { marcaDoDia, nomeDoMesSemAno } from '../lancamentos/apresentacao';

/** A mensal no dia D, a semanal no dia da semana do início e a anual no dia e no mês do início (Fatia 07a). */
export type Frequencia = 'MENSAL' | 'SEMANAL' | 'ANUAL';

const SEMANA = ['domingo', 'segunda', 'terça', 'quarta', 'quinta', 'sexta', 'sábado'];
const SEMANA_CURTA = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'];
const SEMANA_INTEIRA = ['domingo', 'segunda-feira', 'terça-feira', 'quarta-feira', 'quinta-feira', 'sexta-feira', 'sábado'];

/** O dia da semana de uma data ISO (0 = domingo), sem Date local: não há fuso. */
function diaDaSemana(dataIso: string): number {
  const [ano, mes, dia] = dataIso.split('-').map(Number);
  return new Date(Date.UTC(ano, mes - 1, dia)).getUTCDay();
}

/** "26/11" no ano de hoje; "15/01/2027" noutro ano. */
function diaComAno(dataIso: string, hoje: string): string {
  return dataIso.slice(0, 4) === hoje.slice(0, 4) ? diaEMes(dataIso) : dataCurta(dataIso);
}

/** "Todo dia 1", "Toda sexta", "Todo sábado", "Todo ano em 15/01". */
export function fraseDaFrequencia(frequencia: Frequencia, diaDoMes: number | null, comecaEm: string): string {
  if (frequencia === 'MENSAL') {
    return `Todo dia ${diaDoMes}`;
  }
  if (frequencia === 'ANUAL') {
    return `Todo ano em ${diaEMes(comecaEm)}`;
  }
  const dia = diaDaSemana(comecaEm);
  return `${dia === 0 || dia === 6 ? 'Todo' : 'Toda'} ${SEMANA[dia]}`;
}

/** A frase da linha: "Toda sexta, desde 27/11" quando começa depois de hoje; "Todo dia 15, até 15/06/2027" com fim. */
export function fraseDaRecorrencia(
  recorrencia: {
    readonly frequencia: Frequencia;
    readonly diaDoMes: number | null;
    readonly comecaEm: string;
    readonly terminaEm: string | null;
  },
  hoje: string,
): string {
  const desde = recorrencia.comecaEm > hoje ? `, desde ${diaComAno(recorrencia.comecaEm, hoje)}` : '';
  const ate = recorrencia.terminaEm === null ? '' : `, até ${dataCurta(recorrencia.terminaEm)}`;
  return fraseDaFrequencia(recorrencia.frequencia, recorrencia.diaDoMes, recorrencia.comecaEm) + desde + ate;
}

/** "qui, 26/11"; noutro ano, "sex, 15/01/2027". */
export function dataDaOcorrencia(dataIso: string, hoje: string): string {
  return `${SEMANA_CURTA[diaDaSemana(dataIso)]}, ${diaComAno(dataIso, hoje)}`;
}

/** "segunda, 30/11/2026", no subtítulo do diálogo de confirmar. */
export function dataLonga(dataIso: string): string {
  return `${SEMANA[diaDaSemana(dataIso)]}, ${dataCurta(dataIso)}`;
}

/** Na lista "A confirmar": "atrasada desde 20/11", "hoje, 25/11", "amanhã, 26/11" ou "sex, 27/11". */
export function quandoAConfirmar(dataIso: string, hoje: string): string {
  if (dataIso < hoje) {
    return `atrasada desde ${diaComAno(dataIso, hoje)}`;
  }
  const marca = marcaDoDia(dataIso, hoje);
  return marca === null ? dataDaOcorrencia(dataIso, hoje) : `${marca}, ${diaEMes(dataIso)}`;
}

/** A nota do card "A confirmar": "A atrasada e o que vence até quarta, 02/12" (no celular, "Até quarta, 02/12"). */
export function notaDoAConfirmar(atrasadas: number, ate: string, curta: boolean): string {
  const quando = `até ${SEMANA[diaDaSemana(ate)]}, ${diaEMes(ate)}`;
  if (curta) {
    return quando.charAt(0).toLocaleUpperCase('pt-BR') + quando.slice(1);
  }
  const quem = atrasadas === 0 ? 'O que vence' : atrasadas === 1 ? 'A atrasada e o que vence' : 'As atrasadas e o que vence';
  return `${quem} ${quando}`;
}

/** O rodapé da lista cortada: "e mais 3 até 02/12". */
export function maisAte(quantidade: number, ate: string, hoje: string): string {
  return `e mais ${quantidade} até ${diaComAno(ate, hoje)}`;
}

/** "em 5, 1 atrasada", "em 14", na faixa dos previstos de Lançamentos. */
export function quantidadeNaFaixa(quantidade: number, atrasadas: number): string {
  const resto = atrasadas === 0 ? '' : atrasadas === 1 ? ', 1 atrasada' : `, ${atrasadas} atrasadas`;
  return `em ${quantidade}${resto}`;
}

/** "Aparece até 19/01/2027 (60 dias). Depois some, sem gravar nada." */
export function dicaDaAtrasada(visivelAte: string): string {
  return `Aparece até ${dataCurta(visivelAte)} (60 dias). Depois some, sem gravar nada.`;
}

/** O período no histórico: "Novembro" (noutro ano, "Novembro de 2025"), "Semana de 23/11" ou "2026". */
export function rotuloDoPeriodo(frequencia: Frequencia, periodo: string, hoje: string): string {
  if (frequencia === 'ANUAL') {
    return periodo.slice(0, 4);
  }
  if (frequencia === 'SEMANAL') {
    return `Semana de ${diaComAno(periodo, hoje)}`;
  }
  const mes = nomeDoMesSemAno(periodo.slice(0, 7));
  return periodo.slice(0, 4) === hoje.slice(0, 4) ? mes : `${mes} de ${periodo.slice(0, 4)}`;
}

/** A dica da frequência no diálogo; nula quando não há o que dizer. */
export function dicaDaFrequencia(frequencia: Frequencia, diaDoMes: number | null, comecaEm: string | null): string | null {
  if (frequencia === 'MENSAL') {
    return diaDoMes !== null && diaDoMes >= 29 ? 'Nos meses mais curtos, vale o último dia.' : null;
  }
  if (comecaEm === null) {
    return null;
  }
  if (frequencia === 'SEMANAL') {
    const dia = diaDaSemana(comecaEm);
    return `${dia === 0 || dia === 6 ? 'Todo' : 'Toda'} ${SEMANA_INTEIRA[dia]}, o dia da semana do início.`;
  }
  return diaEMes(comecaEm) === '29/02' ? 'Todo ano em 29/02; nos anos sem esse dia, em 28/02.' : `Todo ano em ${diaEMes(comecaEm)}.`;
}

/** A nota do dia que mudou na prévia: "28/02: fevereiro não tem dia 30." */
export function notaDoDiaAjustado(dataIso: string, diaPedido: number): string {
  return `${diaEMes(dataIso)}: ${mesSemAno(dataIso.slice(0, 7))} não tem dia ${diaPedido}.`;
}

/** A nota da prévia no cartão: "Cada uma vira uma compra à vista. A de 28/11 cai na fatura de janeiro (vence 05/01/2027)." */
export function notaDoCartao(data: string, faturaMes: string, vencimento: string): string {
  const fatura = `${mesSemAno(faturaMes)} (vence ${dataCurta(vencimento)})`;
  return `Cada uma vira uma compra à vista. A de ${diaEMes(data)} cai na fatura de ${fatura}.`;
}

/** "Encerrar Academia Fictícia?": o que acontece com as previstas e as já confirmadas. */
export function paragrafoDeEncerrar(confirmadas: number, noCartao: boolean): TrechoDeTexto[] {
  const inicio = 'As previstas depois de hoje saem.';
  if (confirmadas === 0) {
    return [inicio];
  }
  if (confirmadas === 1) {
    return [`${inicio} A `, { forte: 'já confirmada' }, ` continua como ${noCartao ? 'compra' : 'lançamento'}.`];
  }
  return [`${inicio} As `, { forte: `${confirmadas} já confirmadas` }, ` continuam como ${noCartao ? 'compras' : 'lançamentos'}.`];
}

/** "Excluir Academia Fictícia?": sai o histórico; os lançamentos (ou as compras) confirmados ficam. */
export function paragrafoDeExcluir(confirmadas: number, noCartao: boolean): TrechoDeTexto[] {
  const inicio = 'Sai a recorrência e o histórico dela.';
  if (confirmadas === 0) {
    return [inicio];
  }
  if (confirmadas === 1) {
    return noCartao ? [`${inicio} A `, { forte: 'compra confirmada' }, ' fica, sem a ligação.']
      : [`${inicio} O `, { forte: 'lançamento confirmado' }, ' fica, sem a ligação.'];
  }
  return noCartao ? [`${inicio} As `, { forte: `${confirmadas} compras confirmadas` }, ' ficam, sem a ligação.']
    : [`${inicio} Os `, { forte: `${confirmadas} lançamentos confirmados` }, ' ficam, sem a ligação.'];
}

/** Na exclusão da conta ou do cartão: "A recorrência Mensalidade do clube Fictícia, só desta conta, sai junto." */
export function paragrafoDasQueSaemJunto(nomes: readonly string[], doCartao: boolean): TrechoDeTexto[] {
  const onde = doCartao ? 'só deste cartão' : 'só desta conta';
  return nomes.length === 1 ? ['A recorrência ', { forte: nomes[0] }, `, ${onde}, sai junto.`]
    : ['As recorrências ', ...nomesEmNegrito(nomes), `, ${onde}, saem junto.`];
}
