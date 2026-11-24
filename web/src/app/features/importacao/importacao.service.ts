import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { CondicaoDaRegra } from '../regras/regras.service';

export type SituacaoDaLinha = 'NOVA' | 'PROVAVEL' | 'JA_IMPORTADA' | 'ANTES_DO_SALDO_INICIAL' | 'FORA';
/** Desde a Fatia 05c, na fatura: a data antes da faixa da compra, o pagamento da fatura e as parcelas cobertas. */
export type MotivoDeFora = 'ILEGIVEL' | 'VALOR_ZERO' | 'LINHA_DE_SALDO' | 'DATA_ALEM_DO_LIMITE' | 'DATA_ANTES_DO_LIMITE' | 'PAGAMENTO_DA_FATURA'
  | 'COMPRA_JA_NO_FLUX' | 'PARCELA_DO_ARQUIVO';

/**
 * Fatia 05c: onde a importação acontece, uma conta ou um cartão (o primeiro campo, "Importar em"). `id` é o da conta ou
 * o do cartão.
 */
export interface Destino {
  readonly tipo: 'conta' | 'cartao';
  readonly id: number;
}

/** Fatia 05c (decisão 40): uma fatura oferecida ("2026-12"), com o fechamento, o vencimento e a situação pelas datas. */
export interface FaturaParaImportar {
  readonly mes: string;
  readonly fechamento: string;
  readonly vencimento: string;
  readonly situacao: 'FUTURA' | 'ABERTA' | 'FECHADA';
}

/**
 * Com quem a provável duplicada se parece; na já importada, o lançamento dela. `criadoEm` é quando
 * ele foi lançado à mão ou importado; `excluidoEm`, quando foi excluído (decisão 17). Na fatura (Fatia 05c), `data` é a
 * data da compra.
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
 * `descricao` é sempre a do extrato; `sugestao` só vem na linha para importar que uma regra pega. Na fatura (Fatia 05c),
 * `valor` tem o sinal do Flux (a compra negativa, o crédito positivo); `parcela` e `parcelas` só na linha "Parcela
 * k/N"; `parcelaQueCobre`, na parcela que fica de fora, a parcela que já cria a compra. Os três são opcionais: a API
 * manda sempre, e os objetos de teste da importação da conta não os trazem.
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
  readonly parcela?: number | null;
  readonly parcelas?: number | null;
  readonly parcelaQueCobre?: number | null;
  /** Fatia 05c: na linha "Parcela k/N", a descrição sem "Parcela k/N", que a compra grava. */
  readonly descricaoDaCompra?: string | null;
  /**
   * Fatia 05c ("Corrigir ao importar"): só na parcela já importada que a confirmação corrige, o valor dela no Flux; `valor`
   * é o do banco.
   */
  readonly valorNoFlux?: number | null;
}

/**
 * Resposta de POST /api/importacoes/previa (nada foi gravado). Na fatura (Fatia 05c), `contaId`, `contaNome` e
 * `dataSaldoInicial` vêm nulos, e `cartaoId`, `cartaoNome`, a `fatura` escolhida, as `faturas` oferecidas e a
 * `dataDaSugestao` (nula quando todas as linhas são parcelas) vêm preenchidos. Os campos da fatura são opcionais: a API
 * manda sempre, e os objetos de teste da conta não os trazem.
 */
export interface Previa {
  readonly hash: string;
  readonly formato: 'OFX' | 'CSV';
  readonly nomeArquivo: string;
  readonly contaId: number | null;
  readonly contaNome: string | null;
  readonly dataSaldoInicial: string | null;
  readonly dataInicio: string | null;
  readonly dataFim: string | null;
  readonly linhas: readonly LinhaDaPrevia[];
  /**
   * O mesmo arquivo já importado, numa conta ou (Fatia 05c) num cartão: `contaNome` é o nome do lugar, e `mesmaConta`, se é o
   * mesmo lugar desta revisão.
   */
  readonly arquivoRepetido: {
    readonly importadoEm: string; readonly contaId: number | null; readonly cartaoId?: number | null; readonly contaNome: string;
    readonly mesmaConta: boolean;
  } | null;
  readonly outraConta: { readonly contaId: number; readonly contaNome: string; readonly finalDaConta: string } | null;
  /** O perfil de CSV usado (nulo no OFX), para o resumo da decisão 12. */
  readonly perfil: FormatoCsv | null;
  /** De onde vieram as já importadas, a mais recente primeiro. */
  readonly origens: readonly Origem[];
  readonly cartaoId?: number | null;
  readonly cartaoNome?: string | null;
  readonly fatura?: FaturaParaImportar | null;
  readonly faturas?: readonly FaturaParaImportar[];
  readonly dataDaSugestao?: string | null;
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

/**
 * Um lançamento da importação que virou pagamento de fatura (Fatia 05b): a data dele, o cartão e o mês da fatura
 * ("2026-11"), que volta a ter esse valor pendente quando o desfazer apaga o pagamento.
 */
export interface PagamentoDaImportacao {
  readonly data: string;
  readonly cartaoId: number;
  readonly cartaoNome: string;
  readonly cartaoArquivado: boolean;
  readonly faturaMes: string;
}

/** Fatia 05c: uma compra criada de "Parcela k/N": a descrição, a primeira parcela no Flux (k) e N. */
export interface Parcelada {
  readonly descricao: string;
  readonly parcelaInicial: number;
  readonly parcelas: number;
}

/**
 * Resposta de POST /api/importacoes. Sem nada gravado, `importacaoId` vem nulo. Na fatura (Fatia 05c), `contaId`,
 * `contaNome` e `saldoDeHoje` vêm nulos, e `cartaoId`, `cartaoNome`, a `fatura` ("2026-12"), os `lancamentos` gravados
 * (com as parcelas) e as `parceladas` vêm preenchidos; opcionais pelo mesmo motivo de `pares`.
 */
export interface Resultado {
  readonly importacaoId: number | null;
  readonly contaId: number | null;
  readonly contaNome: string | null;
  readonly importadas: number;
  readonly desmarcadas: number;
  readonly jaImportadas: number;
  readonly deFora: number;
  readonly entraramPorOutra: number;
  readonly saldoDeHoje: number | null;
  readonly mesDaUltimaLinha: string | null;
  /**
   * Fatia 04c: as linhas gravadas com um só par possível. A API manda sempre; é opcional aqui porque os
   * objetos de teste das fatias anteriores não o trazem.
   */
  readonly pares?: readonly ParSugerido[];
  readonly cartaoId?: number | null;
  readonly cartaoNome?: string | null;
  readonly fatura?: string | null;
  readonly lancamentos?: number;
  readonly parceladas?: readonly Parcelada[];
  /** Fatia 05c ("Corrigir ao importar"): as parcelas já importadas que ficaram com o valor do banco. */
  readonly corrigidas?: number;
}

/**
 * Um item do histórico (GET /api/importacoes). Na de fatura (Fatia 05c), `contaId` e `contaNome` vêm nulos, e o cartão e
 * a fatura vêm preenchidos; `editados` conta as compras com texto mudado, e `excluidos`, as excluídas depois.
 */
export interface Importacao {
  readonly id: number;
  readonly nomeArquivo: string;
  readonly contaId: number | null;
  readonly contaNome: string | null;
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
  /** Fatia 05b: os lançamentos dela que viraram pagamento de fatura. A API manda sempre; opcional pelo mesmo motivo. */
  readonly pagamentos?: readonly PagamentoDaImportacao[];
  /** Fatia 05c: o cartão e a fatura da importação de fatura. A API manda sempre; opcionais pelo mesmo motivo. */
  readonly cartaoId?: number | null;
  readonly cartaoNome?: string | null;
  readonly cartaoArquivado?: boolean;
  readonly faturaMes?: string | null;
}

export interface Excluido {
  readonly id: number;
  readonly data: string;
  readonly descricao: string;
  readonly valor: number;
  readonly ignoradoEm: string;
}

/**
 * O perfil de CSV de uma conta ou, desde a Fatia 05c, de um cartão (as colunas contam a partir de 1).
 * `comprasPositivas` só no cartão (decisão 39): na coluna única, as compras vêm positivas; a API manda nulo na conta.
 */
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
  readonly comprasPositivas?: boolean | null;
}

/** Resposta de POST /api/importacoes/amostra. Desde a Fatia 05c, o modelo "Nubank (fatura)". */
export interface Amostra {
  readonly linhas: readonly (readonly string[])[];
  readonly modelo: 'NUBANK_CONTA' | 'NUBANK_FATURA' | null;
  readonly formato: FormatoCsv;
  readonly primeiraLinha: { readonly data: string; readonly descricao: string; readonly valor: number } | null;
  readonly erroDaPrimeiraLinha: string | null;
}

@Injectable({ providedIn: 'root' })
export class ImportacaoService {
  private readonly http = inject(HttpClient);

  /** Último lugar usado numa revisão (a conta ou, desde a Fatia 05c, o cartão), só em memória (decisão 11). */
  ultimoDestino: Destino | null = null;

  /** Na fatura (Fatia 05c), `fatura` é a escolhida ("2026-12"); sem ela, a API usa a sugerida. */
  previa(arquivo: File, destino: Destino, fatura: string | null = null): Observable<Previa> {
    const corpo = new FormData();
    corpo.append('arquivo', arquivo, arquivo.name);
    corpo.append(destino.tipo === 'conta' ? 'contaId' : 'cartaoId', String(destino.id));
    if (fatura !== null) {
      corpo.append('fatura', fatura);
    }
    return this.http.post<Previa>('/api/importacoes/previa', corpo);
  }

  /** O mesmo arquivo da revisão e a parte JSON "pedido" (decisão 13); na fatura, o cartão e a fatura escolhida. */
  confirmar(arquivo: File, destino: Destino, hash: string, linhas: readonly Escolha[], fatura: string | null = null): Observable<Resultado> {
    const corpo = new FormData();
    corpo.append('arquivo', arquivo, arquivo.name);
    const pedido = destino.tipo === 'conta' ? { contaId: destino.id, hash, linhas } : { cartaoId: destino.id, fatura, hash, linhas };
    corpo.append('pedido', new Blob([JSON.stringify(pedido)], { type: 'application/json' }));
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

  /**
   * As primeiras linhas do CSV, lidas com o formato dado (ou com o sugerido, sem ele). `cartao` (Fatia 05c): o arquivo vai
   * para um cartão.
   */
  amostra(arquivo: File, formato: FormatoCsv | null, cartao = false): Observable<Amostra> {
    const corpo = new FormData();
    corpo.append('arquivo', arquivo, arquivo.name);
    if (formato !== null) {
      corpo.append('formato', new Blob([JSON.stringify(formato)], { type: 'application/json' }));
    }
    if (cartao) {
      corpo.append('cartao', 'true');
    }
    return this.http.post<Amostra>('/api/importacoes/amostra', corpo);
  }

  /** O perfil de CSV da conta ou, desde a Fatia 05c, do cartão. */
  perfil(destino: Destino): Observable<FormatoCsv> {
    return this.http.get<FormatoCsv>(urlDoPerfil(destino));
  }

  salvarPerfil(destino: Destino, formato: FormatoCsv): Observable<FormatoCsv> {
    return this.http.put<FormatoCsv>(urlDoPerfil(destino), formato);
  }
}

/** "/api/importacoes/perfis/4" na conta; "/api/importacoes/perfis/cartao/2" no cartão (Fatia 05c). */
function urlDoPerfil(destino: Destino): string {
  return destino.tipo === 'conta' ? `/api/importacoes/perfis/${destino.id}` : `/api/importacoes/perfis/cartao/${destino.id}`;
}
