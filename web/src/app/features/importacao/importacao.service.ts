import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { CondicaoDaRegra } from '../regras/regras.service';

export type SituacaoDaLinha = 'NOVA' | 'PROVAVEL' | 'JA_IMPORTADA' | 'ANTES_DO_SALDO_INICIAL' | 'FORA';
export type MotivoDeFora = 'ILEGIVEL' | 'VALOR_ZERO' | 'LINHA_DE_SALDO' | 'DATA_ALEM_DO_LIMITE';

/**
 * Com quem a provável duplicada se parece; na já importada, o lançamento dela. `criadoEm` é quando
 * ele foi lançado à mão ou importado; `excluidoEm`, quando foi excluído (decisão 17).
 */
export interface Parecido {
  readonly descricao: string;
  readonly data: string;
  readonly manual: boolean;
  readonly excluido: boolean;
  readonly criadoEm: string;
  readonly excluidoEm: string | null;
}

/** Uma importação de onde vieram linhas já importadas. */
export interface Origem {
  readonly nomeArquivo: string;
  readonly importadaEm: string;
}

/**
 * A regra que categorizou a linha na revisão (Fatia 04b, decisão 14): a posição e a condição, para a
 * tela dizer "pela 2ª regra", a categoria e a descrição legível (nula = fica a do extrato).
 */
export interface SugestaoDaLinha {
  readonly regraId: number;
  readonly posicao: number;
  readonly condicao: CondicaoDaRegra;
  readonly texto: string;
  readonly categoriaId: number;
  readonly descricao: string | null;
}

/**
 * Uma linha da revisão. Na ilegível, data, descrição e valor vêm nulos e `erro` diz o que faltou.
 * `descricao` é sempre a do extrato; `sugestao` só vem na linha para importar que uma regra pega.
 */
export interface LinhaDaPrevia {
  readonly linha: number;
  readonly data: string | null;
  readonly descricao: string | null;
  readonly valor: number | null;
  readonly situacao: SituacaoDaLinha;
  readonly motivo: MotivoDeFora | null;
  readonly marcada: boolean;
  readonly futuro: boolean;
  readonly erro: string | null;
  readonly parecido: Parecido | null;
  readonly sugestao: SugestaoDaLinha | null;
}

/** Resposta de POST /api/importacoes/previa (nada foi gravado). */
export interface Previa {
  readonly hash: string;
  readonly formato: 'OFX' | 'CSV';
  readonly nomeArquivo: string;
  readonly contaId: number;
  readonly contaNome: string;
  readonly dataSaldoInicial: string;
  readonly dataInicio: string | null;
  readonly dataFim: string | null;
  readonly linhas: readonly LinhaDaPrevia[];
  readonly arquivoRepetido: { readonly importadoEm: string; readonly contaId: number; readonly contaNome: string; readonly mesmaConta: boolean } | null;
  readonly outraConta: { readonly contaId: number; readonly contaNome: string; readonly finalDaConta: string } | null;
  /** O perfil de CSV usado (nulo no OFX), para o resumo da decisão 12. */
  readonly perfil: FormatoCsv | null;
  /** De onde vieram as já importadas, a mais recente primeiro. */
  readonly origens: readonly Origem[];
}

/**
 * Uma linha marcada, com a categoria escolhida (nula = sem categoria) e a descrição que a revisão
 * mostrou (a legível da regra, Fatia 04b; nula = a do extrato).
 */
export interface Escolha {
  readonly linha: number;
  readonly categoriaId: number | null;
  readonly descricao: string | null;
}

/** Um lado de um par sugerido (Fatia 04c): `valor` com sinal. */
export interface LadoSugerido {
  readonly id: number;
  readonly data: string;
  readonly descricao: string;
  readonly valor: number;
  readonly contaId: number;
  readonly contaNome: string;
}

/** Uma linha importada e o único par possível dela (Fatia 04c, decisão 35): nada se converte sozinho. */
export interface ParSugerido {
  readonly lancamento: LadoSugerido;
  readonly par: LadoSugerido;
}

/**
 * Um lançamento da importação numa transferência convertida (Fatia 04c, decisão 34): a data dele, a
 * conta do outro lado e o tipo que o outro lado volta a ter quando o desfazer desfaz a conversão.
 */
export interface TransferenciaDaImportacao {
  readonly data: string;
  readonly contaId: number;
  readonly contaNome: string;
  readonly contaArquivada: boolean;
  readonly tipo: 'RECEITA' | 'DESPESA';
}

/** Resposta de POST /api/importacoes. Sem nada gravado, `importacaoId` vem nulo. */
export interface Resultado {
  readonly importacaoId: number | null;
  readonly contaId: number;
  readonly contaNome: string;
  readonly importadas: number;
  readonly desmarcadas: number;
  readonly jaImportadas: number;
  readonly deFora: number;
  readonly entraramPorOutra: number;
  readonly saldoDeHoje: number;
  readonly mesDaUltimaLinha: string | null;
  /**
   * Fatia 04c: as linhas gravadas com um só par possível. A API manda sempre; é opcional aqui porque os
   * objetos de teste das fatias anteriores não o trazem.
   */
  readonly pares?: readonly ParSugerido[];
}

/** Um item do histórico (GET /api/importacoes). */
export interface Importacao {
  readonly id: number;
  readonly nomeArquivo: string;
  readonly contaId: number;
  readonly contaNome: string;
  readonly contaArquivada: boolean;
  readonly criadaEm: string;
  readonly dataInicio: string;
  readonly dataFim: string;
  readonly importadas: number;
  readonly jaImportadas: number;
  readonly desmarcadas: number;
  readonly deFora: number;
  readonly editados: number;
  readonly excluidos: number;
  readonly desfeitaEm: string | null;
  /** Fatia 04c: os lançamentos dela numa transferência convertida. A API manda sempre; opcional pelo mesmo motivo de `pares`. */
  readonly transferencias?: readonly TransferenciaDaImportacao[];
}

export interface Excluido {
  readonly id: number;
  readonly data: string;
  readonly descricao: string;
  readonly valor: number;
  readonly ignoradoEm: string;
}

/** O perfil de CSV de uma conta (as colunas contam a partir de 1). */
export interface FormatoCsv {
  readonly separador: 'VIRGULA' | 'PONTO_E_VIRGULA' | 'TAB';
  readonly linhasDeCabecalho: number;
  readonly colunaData: number;
  readonly formatoData: 'DD_MM_AAAA' | 'DD_MM_AA' | 'AAAA_MM_DD';
  readonly colunaDescricao: number;
  readonly colunaValor: number;
  readonly colunaSaidas: number | null;
  readonly colunaIdentificador: number | null;
  readonly separadorDecimal: 'VIRGULA' | 'PONTO';
}

/** Resposta de POST /api/importacoes/amostra. */
export interface Amostra {
  readonly linhas: readonly (readonly string[])[];
  readonly modelo: 'NUBANK_CONTA' | null;
  readonly formato: FormatoCsv;
  readonly primeiraLinha: { readonly data: string; readonly descricao: string; readonly valor: number } | null;
  readonly erroDaPrimeiraLinha: string | null;
}

@Injectable({ providedIn: 'root' })
export class ImportacaoService {
  private readonly http = inject(HttpClient);

  /** Última conta usada numa revisão, só em memória (decisão 11). */
  ultimaConta: number | null = null;

  previa(arquivo: File, contaId: number): Observable<Previa> {
    const corpo = new FormData();
    corpo.append('arquivo', arquivo, arquivo.name);
    corpo.append('contaId', String(contaId));
    return this.http.post<Previa>('/api/importacoes/previa', corpo);
  }

  /** O mesmo arquivo da revisão e a parte JSON "pedido" (decisão 13). */
  confirmar(arquivo: File, contaId: number, hash: string, linhas: readonly Escolha[]): Observable<Resultado> {
    const corpo = new FormData();
    corpo.append('arquivo', arquivo, arquivo.name);
    corpo.append('pedido', new Blob([JSON.stringify({ contaId, hash, linhas })], { type: 'application/json' }));
    return this.http.post<Resultado>('/api/importacoes', corpo);
  }

  listar(): Observable<Importacao[]> {
    return this.http.get<Importacao[]>('/api/importacoes');
  }

  desfazer(id: number): Observable<Importacao> {
    return this.http.post<Importacao>(`/api/importacoes/${id}/desfazer`, null);
  }

  excluidos(id: number): Observable<Excluido[]> {
    return this.http.get<Excluido[]>(`/api/importacoes/${id}/excluidos`);
  }

  /** As primeiras linhas do CSV, lidas com o formato dado (ou com o sugerido, sem ele). */
  amostra(arquivo: File, formato: FormatoCsv | null): Observable<Amostra> {
    const corpo = new FormData();
    corpo.append('arquivo', arquivo, arquivo.name);
    if (formato !== null) {
      corpo.append('formato', new Blob([JSON.stringify(formato)], { type: 'application/json' }));
    }
    return this.http.post<Amostra>('/api/importacoes/amostra', corpo);
  }

  perfil(contaId: number): Observable<FormatoCsv> {
    return this.http.get<FormatoCsv>(`/api/importacoes/perfis/${contaId}`);
  }

  salvarPerfil(contaId: number, formato: FormatoCsv): Observable<FormatoCsv> {
    return this.http.put<FormatoCsv>(`/api/importacoes/perfis/${contaId}`, formato);
  }
}