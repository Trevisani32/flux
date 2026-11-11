import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

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

/** Uma linha da revisão. Na ilegível, data, descrição e valor vêm nulos e `erro` diz o que faltou. */
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

/** Uma linha marcada, com a categoria escolhida (nula = sem categoria). */
export interface Escolha {
  readonly linha: number;
  readonly categoriaId: number | null;
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
