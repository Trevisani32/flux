import { HttpErrorResponse } from '@angular/common/http';

import { dataCurta, dataIsoNoFuso } from '../../shared/datas/data-curta';
import { dataHoraCurta } from '../../shared/datas/data-hora';
import { formatarMoeda } from '../../shared/moeda/moeda';
import { codigoDoErro, detalheDoErro, propriedadeDoErro, statusDoErro } from '../auth/erros-http';
import { dataMinimaDaCompra, diaEMes, nomeDaFatura } from '../cartoes/apresentacao';
import { nomeDoMes } from '../lancamentos/apresentacao';
import { dataLimite } from '../lancamentos/lancamento-dialogo/lancamento-dialogo';
import { FaturaParaImportar, FormatoCsv, Importacao, LinhaDaPrevia, Origem, Parecido, Previa, Resultado } from './importacao.service';

/** 5 MB, como o max-file-size da API (decisão 35). */
export const LIMITE_DO_ARQUIVO = 5 * 1024 * 1024;
export const MENSAGEM_DO_413 = 'O arquivo pode ter no máximo 5 MB.';
const FALHA_AO_LER = 'Não foi possível ler o arquivo. Tente de novo.';
const MEGA = 1024 * 1024;

/** O modelo pronto da decisão 6, igual ao FormatoCsv.NUBANK_CONTA da API. */
export const NUBANK_CONTA: FormatoCsv = {
  separador: 'VIRGULA', linhasDeCabecalho: 1, colunaData: 1, formatoData: 'DD_MM_AAAA', colunaDescricao: 4, colunaValor: 2,
  colunaSaidas: null, colunaIdentificador: 3, separadorDecimal: 'PONTO',
};

/**
 * Fatia 05c (decisão 39): o modelo pronto "Nubank (fatura)", igual ao FormatoCsv.NUBANK_FATURA da API: date,title,amount,
 * com a data AAAA-MM-DD, o ponto decimal e as compras positivas.
 */
export const NUBANK_FATURA: FormatoCsv = {
  separador: 'VIRGULA', linhasDeCabecalho: 1, colunaData: 1, formatoData: 'AAAA_MM_DD', colunaDescricao: 2, colunaValor: 3,
  colunaSaidas: null, colunaIdentificador: null, separadorDecimal: 'PONTO', comprasPositivas: true,
};

export type PassoDaImportacao = 'arquivo' | 'perfil' | 'revisao' | 'pronto';

/** Um passo do indicador "1 Arquivo, 2 Perfil do CSV, 3 Revisar, 4 Pronto". */
export interface PassoDoIndicador {
  readonly numero: number;
  readonly nome: string;
  readonly estado: 'feito' | 'atual' | 'depois';
  readonly nota: string | null;
}

const NOMES_DOS_PASSOS: Record<PassoDaImportacao, string> = { arquivo: 'Arquivo', perfil: 'Perfil do CSV', revisao: 'Revisar', pronto: 'Pronto' };

/**
 * Os passos, com o atual marcado. No passo do arquivo, o 2 diz "(só CSV)"; depois dele, o OFX pula
 * o 2, e os números não mudam ("1 Arquivo, 3 Revisar, 4 Pronto").
 */
export function passosDaImportacao(atual: PassoDaImportacao, formato: 'OFX' | 'CSV' | null): PassoDoIndicador[] {
  const ordem: PassoDaImportacao[] = ['arquivo', 'perfil', 'revisao', 'pronto'];
  const posicao = ordem.indexOf(atual);
  return ordem
    .map((passo, i): PassoDoIndicador => ({
      numero: i + 1,
      nome: NOMES_DOS_PASSOS[passo],
      estado: i < posicao ? 'feito' : i === posicao ? 'atual' : 'depois',
      nota: passo === 'perfil' && atual === 'arquivo' ? '(só CSV)' : null,
    }))
    .filter((passo) => !(passo.numero === 2 && atual !== 'arquivo' && formato === 'OFX'));
}

const SEPARADORES: Record<FormatoCsv['separador'], string> = { VIRGULA: 'vírgula', PONTO_E_VIRGULA: 'ponto e vírgula', TAB: 'tabulação' };
const FORMATOS_DE_DATA: Record<FormatoCsv['formatoData'], string> = { DD_MM_AAAA: 'DD/MM/AAAA', DD_MM_AA: 'DD/MM/AA', AAAA_MM_DD: 'AAAA-MM-DD' };

/** O formato é o do modelo pronto da conta (decisão 6)? */
export function ehNubank(formato: FormatoCsv): boolean {
  return (Object.keys(NUBANK_CONTA) as (keyof FormatoCsv)[]).every((campo) => formato[campo] === NUBANK_CONTA[campo]);
}

/** Fatia 05c: o formato é o do modelo pronto da fatura (decisão 39), com as compras positivas? */
export function ehNubankFatura(formato: FormatoCsv): boolean {
  return (Object.keys(NUBANK_FATURA) as (keyof FormatoCsv)[]).every((campo) => formato[campo] === NUBANK_FATURA[campo]);
}

/**
 * O resumo do perfil salvo (decisão 12): "Nubank (conta)" e "vírgula · DD/MM/AAAA · ponto decimal". No cartão (Fatia
 * 05c), "Nubank (fatura)" e, com as compras positivas na coluna única, "compras positivas".
 */
export function resumoDoPerfil(formato: FormatoCsv): { readonly modelo: string; readonly detalhes: string } {
  const detalhes = [SEPARADORES[formato.separador], FORMATOS_DE_DATA[formato.formatoData],
    formato.separadorDecimal === 'PONTO' ? 'ponto decimal' : 'vírgula decimal'];
  if (formato.colunaSaidas !== null) {
    detalhes.push('entradas e saídas separadas');
  } else if (formato.comprasPositivas === true) {
    detalhes.push('compras positivas');
  }
  const modelo = ehNubankFatura(formato) ? 'Nubank (fatura)' : ehNubank(formato) ? 'Nubank (conta)' : 'Personalizado';
  return { modelo, detalhes: detalhes.join(' · ') };
}

/** O dia de um instante no fuso do negócio: "2026-11-06T12:00:00Z" vira "06/11/2026". */
function diaDoInstante(instante: string): string {
  return dataCurta(dataIsoNoFuso(new Date(instante)));
}

/** "7,2 MB" com uma casa, para cima: 5 MB e 1 byte é "5,1 MB", nunca "5,0 MB". */
function megas(bytes: number): string {
  return (Math.ceil((bytes / MEGA) * 10) / 10).toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
}

/** O erro do navegador, antes de enviar: "O arquivo tem 7,2 MB. O limite é 5 MB." */
export function erroDeTamanho(bytes: number): string | null {
  return bytes > LIMITE_DO_ARQUIVO ? `O arquivo tem ${megas(bytes)} MB. O limite é 5 MB.` : null;
}

/** "38 KB" ou "1,1 MB". */
export function tamanhoDoArquivo(bytes: number): string {
  return bytes < MEGA ? `${Math.max(1, Math.ceil(bytes / 1024))} KB` : `${megas(bytes)} MB`;
}

/**
 * A mensagem de um erro do arquivo. O 413 é tratado pelo status: o do nginx vem em HTML, sem
 * ProblemDetail (decisão 35). O 422 traz a mensagem no "detail".
 */
export function mensagemDoArquivo(erro: unknown): string {
  const status = statusDoErro(erro);
  if (status === 413) {
    return MENSAGEM_DO_413;
  }
  return status === 422 || status === 400 ? detalheDoErro(erro, FALHA_AO_LER) : FALHA_AO_LER;
}

/** O 422 que leva ao passo do perfil (CSV sem perfil). */
export function pedePerfil(erro: unknown): boolean {
  return statusDoErro(erro) === 422 && codigoDoErro(erro) === 'PERFIL_NECESSARIO';
}

/** O 422 do perfil salvo que não lê nenhuma linha: o passo do perfil abre com o alerta. */
export function perfilNaoLe(erro: unknown): boolean {
  return statusDoErro(erro) === 422 && codigoDoErro(erro) === 'PERFIL_NAO_LE';
}

/**
 * O 422 da categoria que saiu entre a revisão e a confirmação (`CATEGORIA_INVALIDA`, com `linha` e
 * `data`): a mensagem da API e a linha que precisa de outra categoria. Nulo para os outros erros.
 */
export function mensagemDaCategoria(erro: unknown, linhas: readonly LinhaDaPrevia[]): string | null {
  if (statusDoErro(erro) !== 422 || codigoDoErro(erro) !== 'CATEGORIA_INVALIDA' || !(erro instanceof HttpErrorResponse)) {
    return null;
  }
  const detalhe = detalheDoErro(erro, 'Uma das categorias escolhidas foi arquivada ou excluída. Revise as linhas marcadas.');
  const numero: unknown = (erro.error as Record<string, unknown>)['linha'];
  if (typeof numero !== 'number') {
    return detalhe;
  }
  const data = propriedadeDoErro(erro, 'data');
  const descricao = linhas.find((linha) => linha.linha === numero)?.descricao;
  return `${detalhe} Linha ${numero}${data === null ? '' : ` (${data})`}${descricao ? `: ${descricao}` : ''}.`;
}

export interface Contagens {
  readonly paraImportar: number;
  readonly novas: number;
  readonly provaveis: number;
  readonly jaImportadas: number;
  readonly deFora: number;
}

/** Os três grupos da revisão (decisão 14): contar não é fazer conta com dinheiro. */
export function contagens(linhas: readonly LinhaDaPrevia[]): Contagens {
  const novas = linhas.filter((linha) => linha.situacao === 'NOVA').length;
  const provaveis = linhas.filter((linha) => linha.situacao === 'PROVAVEL').length;
  const jaImportadas = linhas.filter((linha) => linha.situacao === 'JA_IMPORTADA').length;
  return { paraImportar: novas + provaveis, novas, provaveis, jaImportadas, deFora: linhas.length - novas - provaveis - jaImportadas };
}

/** "Importar 7 lançamentos" (decisão 21); sem nenhuma marcada, o aviso no lugar do texto. */
export function textoDoBotaoImportar(marcadas: number): string {
  if (marcadas === 0) {
    return 'Marque ao menos uma linha.';
  }
  return marcadas === 1 ? 'Importar 1 lançamento' : `Importar ${marcadas.toLocaleString('pt-BR')} lançamentos`;
}

/**
 * A pílula da situação; "Data depois de 10/11/2027" usa o limite de hoje + 12 meses. Na fatura (Fatia 05c), também a
 * data antes da faixa da compra ("Data antes de 23/11/2016"), o pagamento da fatura (decisão 42) e as parcelas que outra
 * já cobre; a linha de saldo vale também para a de total.
 */
export function rotuloDaSituacao(linha: LinhaDaPrevia, dataSaldoInicial: string | null, hoje: string): string {
  switch (linha.situacao) {
    case 'NOVA':
      return 'Nova';
    case 'PROVAVEL':
      return 'Provável duplicada';
    case 'JA_IMPORTADA':
      return linha.parecido?.excluido ? 'Já importada (excluída)' : 'Já importada';
    case 'ANTES_DO_SALDO_INICIAL':
      return `Antes do saldo inicial (${dataCurta(dataSaldoInicial ?? '')})`;
    default:
      return rotuloDeFora(linha, hoje);
  }
}

function rotuloDeFora(linha: LinhaDaPrevia, hoje: string): string {
  switch (linha.motivo) {
    case 'VALOR_ZERO':
      return 'Valor zero';
    case 'LINHA_DE_SALDO':
      return 'Linha de saldo';
    case 'DATA_ALEM_DO_LIMITE':
      return `Data depois de ${dataCurta(dataLimite(hoje))}`;
    case 'DATA_ANTES_DO_LIMITE':
      return `Data antes de ${dataCurta(dataMinimaDaCompra(hoje))}`;
    case 'PAGAMENTO_DA_FATURA':
      return 'Pagamento da fatura: registre pela conta';
    case 'COMPRA_JA_NO_FLUX':
      return `A compra já está no Flux a partir da parcela ${linha.parcelaQueCobre ?? ''}/${linha.parcelas ?? ''}`;
    case 'PARCELA_DO_ARQUIVO':
      return `Entra com a parcela ${linha.parcelaQueCobre ?? ''}/${linha.parcelas ?? ''} deste arquivo`;
    default:
      return `Linha ${linha.linha}: ${linha.erro ?? 'ilegível'}.`;
  }
}

/**
 * Fatia 05c (decisões 41 e 43): o que a linha da fatura para importar é, ao lado da pílula. A parcela diz as parcelas que
 * cria, até a fatura da última ("Cria a compra com as parcelas 3 a 10 (até julho de 2027)"; curto, no celular, "cria as
 * parcelas 3 a 10"); a compra à vista diz "À vista"; o estorno não diz nada (ele tem a pílula "Estorno").
 */
export function textoDaLinhaDaFatura(linha: LinhaDaPrevia, faturaMes: string, hoje: string): { readonly longo: string; readonly curto: string } | null {
  if (linha.parcela != null && linha.parcelas != null) {
    const ultima = somarMeses(faturaMes, linha.parcelas - linha.parcela);
    return {
      longo: `Cria a compra com as parcelas ${linha.parcela} a ${linha.parcelas} (até ${nomeDaFatura(ultima, hoje)})`,
      curto: `cria as parcelas ${linha.parcela} a ${linha.parcelas}`,
    };
  }
  return (linha.valor ?? 0) < 0 ? { longo: 'À vista', curto: 'À vista' } : null;
}

/**
 * Fatia 05c (decisão 41): a provável duplicada da fatura, embaixo da descrição. A parcela: "Parece a parcela 1/3 de
 * Sapataria Passo Firme, lançada à mão."; a compra à vista: "Parece Mercado Bom Preço, lançada à mão (R$ 312,45 em
 * 04/11)."; a importada de outro arquivo, "importada em 06/11" no lugar de "lançada à mão". No celular, curto: "lançada à
 * mão" ou "importada em 06/11".
 */
export function fraseDoParecidoNaFatura(linha: LinhaDaPrevia, parecido: Parecido): { readonly longo: string; readonly curto: string } {
  const origem = parecido.manual ? 'lançada à mão' : `importada em ${diaDoInstante(parecido.criadoEm).slice(0, 5)}`;
  if (linha.parcela != null && linha.parcelas != null) {
    return { longo: `Parece a parcela ${linha.parcela}/${linha.parcelas} de ${parecido.descricao}, ${origem}.`, curto: origem };
  }
  return { longo: `Parece ${parecido.descricao}, ${origem} (${formatarMoeda(Math.abs(linha.valor ?? 0))} em ${diaEMes(parecido.data)}).`,
    curto: origem };
}

/** Fatia 05c (decisão 41): a parcela já importada diz de qual compra ("A parcela 2/3 de Fone Fictício já está no Flux"). */
export function fraseDaJaImportadaNaFatura(linha: LinhaDaPrevia): string | null {
  return linha.parcela != null && linha.parcelas != null && linha.parecido !== null
    ? `A parcela ${linha.parcela}/${linha.parcelas} de ${linha.parecido.descricao} já está no Flux` : null;
}

/** Fatia 05c ("Corrigir ao importar"): o título do aviso da revisão, "1 parcela já importada fica com o valor do banco". */
export function tituloDasCorrecoes(quantas: number): string {
  return quantas === 1 ? '1 parcela já importada fica com o valor do banco'
    : `${quantas.toLocaleString('pt-BR')} parcelas já importadas ficam com o valor do banco`;
}

/**
 * Fatia 05c: cada parcela do aviso, "A parcela 2/3 de Fone Fictício: R$ 33,34 no Flux, R$ 33,33 no arquivo." (os valores
 * sem o sinal, como na provável duplicada).
 */
export function fraseDaCorrecao(linha: LinhaDaPrevia): string {
  return `A parcela ${linha.parcela ?? ''}/${linha.parcelas ?? ''} de ${linha.parecido?.descricao ?? linha.descricaoDaCompra ?? ''}: `
    + `${formatarMoeda(Math.abs(linha.valorNoFlux ?? 0))} no Flux, ${formatarMoeda(Math.abs(linha.valor ?? 0))} no arquivo.`;
}

/** Fatia 05c: "1 parcela" ou "2 parcelas", na barra e no botão da revisão sem linha marcada. */
export function parcelasParaCorrigir(quantas: number): string {
  return quantas === 1 ? '1 parcela' : `${quantas.toLocaleString('pt-BR')} parcelas`;
}

/**
 * Fatia 05c: a barra da revisão da fatura conta as parcelas que as compras parceladas marcadas criam: "a compra em 10x
 * cria 8 parcelas" ou "as 2 compras parceladas criam 12 parcelas". Contar não é fazer conta com dinheiro.
 */
export function textoDasParcelas(marcadas: readonly LinhaDaPrevia[]): string | null {
  const parceladas = marcadas.filter((linha) => linha.parcela != null && linha.parcelas != null);
  if (parceladas.length === 0) {
    return null;
  }
  const parcelas = parceladas.reduce((soma, linha) => soma + (linha.parcelas ?? 0) - (linha.parcela ?? 0) + 1, 0);
  return parceladas.length === 1 ? `a compra em ${parceladas[0].parcelas}x cria ${parcelas === 1 ? '1 parcela' : `${parcelas} parcelas`}`
    : `as ${parceladas.length} compras parceladas criam ${parcelas} parcelas`;
}

/**
 * Fatia 05c (decisão 40): a opção do seletor da fatura, "Dezembro de 2026 · fecha 03/12"; a que já fechou, "Novembro de
 * 2026 · fechou 03/11".
 */
export function rotuloDaFatura(fatura: FaturaParaImportar, hoje: string): string {
  const nome = nomeDoMes(fatura.mes);
  return `${nome.charAt(0).toLocaleUpperCase('pt-BR')}${nome.slice(1)} · ${fatura.fechamento < hoje ? 'fechou' : 'fecha'} ${diaEMes(fatura.fechamento)}`;
}

/**
 * Fatia 05c (decisão 40): a dica do seletor da fatura, com a data que a sugeriu: "Sugerida pela compra mais recente
 * (21/11). Todas as linhas vão para esta fatura, como no banco."; sem ela (só parcelas no arquivo), "Sugerida pela data de
 * hoje. …".
 */
export function dicaDaFatura(dataDaSugestao: string | null): string {
  const pela = dataDaSugestao === null ? 'Sugerida pela data de hoje.' : `Sugerida pela compra mais recente (${diaEMes(dataDaSugestao)}).`;
  return `${pela} Todas as linhas vão para esta fatura, como no banco.`;
}

/**
 * O período do arquivo: no mesmo ano, "de 05/11 a 06/11/2026"; ao cruzar anos, cada data leva o seu ano.
 * Vale igualmente para a conta e para a fatura.
 */
export function periodoDoArquivo(dataInicio: string, dataFim: string): string {
  if (dataInicio.slice(0, 4) !== dataFim.slice(0, 4)) {
    return `de ${dataCurta(dataInicio)} a ${dataCurta(dataFim)}`;
  }
  return `de ${diaEMes(dataInicio)} a ${dataCurta(dataFim)}`;
}

export function resumoDaFatura(previa: Previa): string {
  const formato = previa.formato === 'OFX' ? 'OFX de cartão'
    : previa.perfil !== null && ehNubankFatura(previa.perfil) ? 'CSV da fatura do Nubank' : 'CSV';
  const linhas = previa.linhas.length === 1 ? '1 linha' : `${previa.linhas.length.toLocaleString('pt-BR')} linhas`;
  const periodo = previa.dataInicio !== null && previa.dataFim !== null ? ` · ${periodoDoArquivo(previa.dataInicio, previa.dataFim)}` : '';
  return `${formato} · ${linhas}${periodo}`;
}

/** Fatia 05c: o subtítulo da revisão da fatura, "fatura-dezembro.csv em Cartão Horizonte · fatura de dezembro de 2026 · fecha 03/12 · vence 10/12". */
export function subtituloDaRevisaoDaFatura(previa: Previa, hoje: string): string {
  const fatura = previa.fatura;
  const daFatura = fatura == null ? '' : ` · fatura de ${nomeDoMes(fatura.mes)} · ${fatura.fechamento < hoje ? 'fechou' : 'fecha'} `
    + `${diaEMes(fatura.fechamento)} · vence ${diaEMes(fatura.vencimento)}`;
  return `${previa.nomeArquivo} em ${previa.cartaoNome ?? ''}${daFatura}`;
}

/**
 * Fatia 05c: o subtítulo curto da revisão da fatura, no celular: "fatura-dezembro.csv · Cartão Horizonte · fatura de
 * dezembro" (com o ano quando a fatura não é do ano de hoje).
 */
export function subtituloCurtoDaRevisaoDaFatura(previa: Previa, hoje: string): string {
  const daFatura = previa.fatura == null ? '' : ` · fatura de ${nomeDaFatura(previa.fatura.mes, hoje)}`;
  return `${previa.nomeArquivo} · ${previa.cartaoNome ?? ''}${daFatura}`;
}

/** "2026-12" mais 7 meses: "2027-07". */
function somarMeses(mes: string, meses: number): string {
  const contagem = Number(mes.slice(0, 4)) * 12 + Number(mes.slice(5, 7)) - 1 + meses;
  return `${Math.floor(contagem / 12)}-${String((contagem % 12) + 1).padStart(2, '0')}`;
}

/**
 * Embaixo da descrição da provável duplicada (decisão 17), com a data de quando o parecido foi
 * lançado à mão, importado ou excluído (a data do lançamento é a mesma da linha).
 */
export function fraseDoParecido(parecido: Parecido): string {
  if (parecido.excluido && parecido.excluidoEm !== null) {
    return `Parece com "${parecido.descricao}", excluído em ${diaDoInstante(parecido.excluidoEm).slice(0, 5)}.`;
  }
  const dia = diaDoInstante(parecido.criadoEm).slice(0, 5);
  return parecido.manual ? `Parece com "${parecido.descricao}", lançado à mão em ${dia}.`
    : `Parece com "${parecido.descricao}", importado em ${dia}.`;
}

/** O começo do grupo "Já importadas": de onde as linhas vieram. */
export function fraseDasOrigens(origens: readonly Origem[]): string {
  if (origens.length === 1) {
    return `Vieram de ${origens[0].nomeArquivo}, importado em ${diaDoInstante(origens[0].importadaEm)}, e não entram de novo.`;
  }
  return origens.length > 1 ? `Vieram de ${origens.length} importações anteriores e não entram de novo.`
    : 'Já entraram antes e não entram de novo.';
}

function contagem(n: number, um: string, varios: string): string {
  return n === 1 ? `1 ${um}` : `${n.toLocaleString('pt-BR')} ${varios}`;
}

/**
 * O lado de um item do histórico: "7 importados" e "34 já existiam · 6 desmarcados · 4 de fora", só as partes que não são
 * zero; com excluídos depois, eles. A desfeita diz quando e quantos saíram.
 */
export function resumoDoHistorico(item: Importacao): { readonly titulo: string; readonly nota: string } {
  if (item.desfeitaEm !== null) {
    return { titulo: `Desfeita em ${dataHoraCurta(item.desfeitaEm).slice(0, 5)}`,
      nota: contagem(item.importadas, 'lançamento saiu', 'lançamentos saíram') };
  }
  const titulo = contagem(item.importadas, 'importado', 'importados');
  if (item.excluidos > 0) {
    return { titulo, nota: contagem(item.excluidos, 'excluído depois', 'excluídos depois') };
  }
  const partes = [
    item.jaImportadas > 0 ? contagem(item.jaImportadas, 'já existia', 'já existiam') : null,
    item.desmarcadas > 0 ? contagem(item.desmarcadas, 'desmarcado', 'desmarcados') : null,
    item.deFora > 0 ? `${item.deFora.toLocaleString('pt-BR')} de fora` : null,
  ].filter((parte): parte is string => parte !== null);
  return { titulo, nota: partes.join(' · ') };
}

/** A mesma comparação dos nomes da API (TextoComparavel.chave): sem acento, minúsculas, espaços colapsados. */
export function chaveDaDescricao(texto: string): string {
  return texto.normalize('NFD').replace(/\p{M}+/gu, '').normalize('NFC').toLowerCase().replace(/[\s\p{Z}]+/gu, ' ').trim();
}

function juntar(partes: readonly string[]): string {
  return partes.length <= 1 ? (partes[0] ?? '') : `${partes.slice(0, -1).join(', ')} e ${partes[partes.length - 1]}`;
}

/**
 * Fatia 05c: a nota do resultado da fatura, embaixo do título: "em Cartão Horizonte, fatura de dezembro · a compra em 10x
 * criou as parcelas 3 a 10 (11 lançamentos no total)"; com mais de uma parcelada, "as 2 compras parceladas criaram 12
 * parcelas (15 lançamentos no total)". Nula na conta e quando nada foi importado.
 */
export function notaDoResultadoDaFatura(resultado: Resultado, hoje: string): string | null {
  if (resultado.cartaoNome == null || resultado.fatura == null || resultado.importadas === 0) {
    return null;
  }
  const parceladas = resultado.parceladas ?? [];
  const base = `em ${resultado.cartaoNome}, fatura de ${nomeDaFatura(resultado.fatura, hoje)}`;
  const total = `${(resultado.lancamentos ?? 0).toLocaleString('pt-BR')} lançamentos no total`;
  if (parceladas.length === 1) {
    const [parcelada] = parceladas;
    return `${base} · a compra em ${parcelada.parcelas}x criou as parcelas ${parcelada.parcelaInicial} a ${parcelada.parcelas} (${total})`;
  }
  if (parceladas.length > 1) {
    const parcelas = parceladas.reduce((soma, parcelada) => soma + parcelada.parcelas - parcelada.parcelaInicial + 1, 0);
    return `${base} · as ${parceladas.length} compras parceladas criaram ${parcelas} parcelas (${total})`;
  }
  return base;
}

/** Fatia 05c: a linha do histórico da importação de fatura, "Cartão Horizonte · fatura de dezembro · 12/11/2026 10:00". */
export function linhaDoHistoricoDaFatura(item: Importacao, hoje: string): string {
  return `${item.cartaoNome ?? ''} · fatura de ${nomeDaFatura(item.faturaMes ?? '', hoje)} · ${dataHoraCurta(item.criadaEm)}`;
}

/**
 * O título e o texto do resultado (decisão 27). `porRegras` (Fatia 04b): quantas linhas a revisão mandou com
 * a categoria da regra; nunca mais que as importadas (uma linha pode ter virado "já importada" no meio). Na fatura (Fatia
 * 05c), o título não diz o lugar: ele está na nota (`notaDoResultadoDaFatura`).
 */
export function frasesDoResultado(resultado: Resultado, porRegras = 0): { readonly titulo: string; readonly texto: string } {
  const n = resultado.importadas;
  const corrigidas = resultado.corrigidas ?? 0;
  const lugar = resultado.cartaoNome != null ? '' : ` em ${resultado.contaNome ?? ''}`;
  const titulo = n === 0 ? (corrigidas > 0 ? 'Nada novo foi importado' : 'Nada foi importado')
    : `${n.toLocaleString('pt-BR')} ${n === 1 ? 'lançamento importado' : 'lançamentos importados'}${lugar}`;
  const frases: string[] = [];
  // Fatia 05c ("Corrigir ao importar"): as parcelas já importadas que passaram ao valor do banco.
  if (corrigidas > 0) {
    frases.push(corrigidas === 1 ? '1 parcela já importada ficou com o valor do banco.'
      : `${corrigidas.toLocaleString('pt-BR')} parcelas já importadas ficaram com o valor do banco.`);
  }
  if (resultado.entraramPorOutra > 0) {
    frases.push(resultado.entraramPorOutra === 1 ? '1 linha já tinha entrado por outra importação e ficou de fora.'
      : `${resultado.entraramPorOutra} linhas já tinham entrado por outra importação e ficaram de fora.`);
  }
  const partes: string[] = [];
  const categorizados = Math.min(porRegras, n);
  if (categorizados > 0) {
    partes.push(categorizados === 1 ? '1 categorizado por regra' : `${categorizados} categorizados por regras`);
  }
  if (resultado.desmarcadas > 0) {
    partes.push(resultado.desmarcadas === 1 ? '1 ficou desmarcado' : `${resultado.desmarcadas} ficaram desmarcados`);
  }
  if (resultado.jaImportadas > 0) {
    partes.push(resultado.jaImportadas === 1 ? '1 já tinha sido importado' : `${resultado.jaImportadas} já tinham sido importados`);
  }
  if (resultado.deFora > 0) {
    partes.push(resultado.deFora === 1 ? '1 ficou de fora' : `${resultado.deFora} ficaram de fora`);
  }
  if (partes.length > 0) {
    const frase = juntar(partes);
    frases.push(`${frase.charAt(0).toUpperCase()}${frase.slice(1)}.`);
  }
  return { titulo, texto: frases.join(' ') };
}
