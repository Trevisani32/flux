import { HttpErrorResponse } from '@angular/common/http';

import { dataCurta, dataIsoNoFuso } from '../../shared/datas/data-curta';
import { dataHoraCurta } from '../../shared/datas/data-hora';
import { codigoDoErro, detalheDoErro, propriedadeDoErro, statusDoErro } from '../auth/erros-http';
import { dataLimite } from '../lancamentos/lancamento-dialogo/lancamento-dialogo';
import { FormatoCsv, Importacao, LinhaDaPrevia, Origem, Parecido, Resultado } from './importacao.service';

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

export type PassoDaImportacao = 'arquivo' | 'perfil' | 'revisao' | 'pronto';

/** Um passo do indicador "1 Arquivo, 2 Perfil do CSV, 3 Revisar, 4 Pronto" do mockup. */
export interface PassoDoIndicador {
  readonly numero: number;
  readonly nome: string;
  readonly estado: 'feito' | 'atual' | 'depois';
  readonly nota: string | null;
}

const NOMES_DOS_PASSOS: Record<PassoDaImportacao, string> = { arquivo: 'Arquivo', perfil: 'Perfil do CSV', revisao: 'Revisar', pronto: 'Pronto' };

/**
 * Os passos, com o atual marcado. No passo do arquivo, o 2 diz "(só CSV)"; depois dele, o OFX pula
 * o 2, e os números não mudam (mockup: "1 Arquivo, 3 Revisar, 4 Pronto").
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

/** O formato é o do modelo pronto (decisão 6)? */
export function ehNubank(formato: FormatoCsv): boolean {
  return (Object.keys(NUBANK_CONTA) as (keyof FormatoCsv)[]).every((campo) => formato[campo] === NUBANK_CONTA[campo]);
}

/** O resumo do perfil salvo (decisão 12): "Nubank (conta)" e "vírgula · DD/MM/AAAA · ponto decimal". */
export function resumoDoPerfil(formato: FormatoCsv): { readonly modelo: string; readonly detalhes: string } {
  const detalhes = [SEPARADORES[formato.separador], FORMATOS_DE_DATA[formato.formatoData],
    formato.separadorDecimal === 'PONTO' ? 'ponto decimal' : 'vírgula decimal'];
  if (formato.colunaSaidas !== null) {
    detalhes.push('entradas e saídas separadas');
  }
  return { modelo: ehNubank(formato) ? 'Nubank (conta)' : 'Personalizado', detalhes: detalhes.join(' · ') };
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

/** O 422 do perfil salvo que não lê nenhuma linha: o passo do perfil abre com o alerta (mockup, linha 623). */
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

/** A pílula da situação; "Data depois de 10/11/2027" usa o limite de hoje + 12 meses. */
export function rotuloDaSituacao(linha: LinhaDaPrevia, dataSaldoInicial: string, hoje: string): string {
  switch (linha.situacao) {
    case 'NOVA':
      return 'Nova';
    case 'PROVAVEL':
      return 'Provável duplicada';
    case 'JA_IMPORTADA':
      return linha.parecido?.excluido ? 'Já importada (excluída)' : 'Já importada';
    case 'ANTES_DO_SALDO_INICIAL':
      return `Antes do saldo inicial (${dataCurta(dataSaldoInicial)})`;
    default:
      return linha.motivo === 'VALOR_ZERO' ? 'Valor zero'
        : linha.motivo === 'LINHA_DE_SALDO' ? 'Linha de saldo'
          : linha.motivo === 'DATA_ALEM_DO_LIMITE' ? `Data depois de ${dataCurta(dataLimite(hoje))}`
            : `Linha ${linha.linha}: ${linha.erro ?? 'ilegível'}.`;
  }
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

/** O começo do grupo "Já importadas" (mockup): de onde as linhas vieram. */
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
 * O lado de um item do histórico (mockup, linha 1072): "7 importados" e "34 já existiam · 6
 * desmarcados · 4 de fora", só as partes que não são zero; com excluídos depois, eles. A desfeita
 * diz quando e quantos saíram.
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
 * O título e o texto do resultado (decisão 27). `porRegras` (Fatia 04b): quantas linhas a revisão mandou com
 * a categoria da regra; nunca mais que as importadas (uma linha pode ter virado "já importada" no meio).
 */
export function frasesDoResultado(resultado: Resultado, porRegras = 0): { readonly titulo: string; readonly texto: string } {
  const n = resultado.importadas;
  const titulo = n === 0 ? 'Nada foi importado'
    : `${n.toLocaleString('pt-BR')} ${n === 1 ? 'lançamento importado' : 'lançamentos importados'} em ${resultado.contaNome}`;
  const frases: string[] = [];
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
