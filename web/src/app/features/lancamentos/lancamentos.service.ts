import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, Subject, map, tap } from 'rxjs';

import { Filtros, parametrosDaApi } from './filtros';

export type TipoLancamento = 'RECEITA' | 'DESPESA' | 'TRANSFERENCIA';

/**
 * Um lado da transferência (Fatia 04c): a saída primeiro. `descricaoBanco` só no importado; na
 * convertida, os dois lados podem ter datas diferentes.
 */
export interface LadoDaTransferencia {
  readonly contaId: number;
  readonly contaNome: string;
  readonly data: string;
  readonly origem: 'MANUAL' | 'IMPORTADO';
  readonly descricaoBanco: string | null;
  readonly observacao: string | null;
}

/**
 * Uma linha da lista (receita, despesa ou a transferência inteira), como a API devolve.
 * Na transferência, `valor` é o valor movido, positivo; `contaId`/`contaNome` são a origem
 * e `contaDestinoId`/`contaDestinoNome`, o destino.
 */
export interface ItemDaLista {
  readonly tipo: TipoLancamento;
  readonly id: number;
  readonly data: string;
  readonly descricao: string;
  readonly valor: number;
  readonly observacao: string | null;
  readonly contaId: number;
  readonly contaNome: string;
  readonly categoriaId: number | null;
  readonly categoriaNome: string | null;
  readonly categoriaMaeNome: string | null;
  readonly categoriaArquivada: boolean;
  readonly contaDestinoId: number | null;
  readonly contaDestinoNome: string | null;
  /** Só com o filtro de uma das contas da transferência. */
  readonly direcao: 'SAIDA' | 'ENTRADA' | null;
  readonly futuro: boolean;
  readonly somenteLeitura: boolean;
  /** IMPORTADO: tipo, valor, data e conta vêm do extrato e não mudam (Fatia 04a, decisão 29). */
  readonly origem: 'MANUAL' | 'IMPORTADO';
  /** Só no importado: a descrição que veio do extrato. */
  readonly descricaoBanco: string | null;
  /**
   * Fatia 04c: só na transferência, a saída e a entrada. A API manda sempre (nulo na receita e na
   * despesa); é opcional aqui porque os objetos de teste das fatias anteriores não o trazem.
   */
  readonly lados?: readonly LadoDaTransferencia[] | null;
}

/** Um par possível para a conversão (Fatia 04c, decisão 27): `valor` com sinal, oposto ao do importado. */
export interface ParDoLancamento {
  readonly id: number;
  readonly data: string;
  readonly descricao: string;
  readonly valor: number;
  readonly contaId: number;
  readonly contaNome: string;
  readonly origem: 'MANUAL' | 'IMPORTADO';
}

/** Resposta de GET /api/lancamentos/{id}/pares: o importado, a janela de 5 dias e os pares, o mais perto primeiro. */
export interface ParesDoLancamento {
  readonly lancamento: ItemDaLista;
  readonly inicio: string;
  readonly fim: string;
  readonly pares: readonly ParDoLancamento[];
}

/** Corpo de POST /api/lancamentos/{id}/converter: um par ou a conta do lado novo, nunca os dois. */
export interface DadosDaConversao {
  readonly parId: number | null;
  readonly contaId: number | null;
  readonly descricao: string | null;
}

/** Só receitas e despesas, prontas da API (o frontend não faz conta com dinheiro). */
export interface TotaisDoMes {
  readonly entradas: number;
  readonly quantidadeEntradas: number;
  readonly saidas: number;
  readonly quantidadeSaidas: number;
  readonly resultado: number;
}

export interface DiaDaLista {
  readonly data: string;
  /** Só com o filtro de uma conta. */
  readonly saldoNoFimDoDia: number | null;
  readonly itens: readonly ItemDaLista[];
}

/** Resposta de GET /api/lancamentos. */
export interface ListaDoMes {
  readonly mes: string;
  readonly hoje: string;
  readonly totais: TotaisDoMes;
  readonly quantidadeLancamentos: number;
  readonly quantidadeTransferencias: number;
  readonly dias: readonly DiaDaLista[];
}

/** Corpo de POST /api/lancamentos e PUT /api/lancamentos/{id}. O valor vai positivo. */
export interface DadosDoLancamento {
  readonly tipo: 'RECEITA' | 'DESPESA';
  readonly valor: number;
  readonly data: string;
  readonly descricao: string;
  readonly contaId: number;
  readonly categoriaId: number | null;
  readonly observacao: string | null;
}

/** Corpo de POST /api/transferencias e PUT /api/transferencias/{id}. O valor vai positivo. */
export interface DadosDaTransferencia {
  readonly valor: number;
  readonly data: string;
  readonly contaOrigemId: number;
  readonly contaDestinoId: number;
  readonly descricao: string | null;
  readonly observacao: string | null;
}

@Injectable({ providedIn: 'root' })
export class LancamentosService {
  private readonly http = inject(HttpClient);

  private readonly restaurados = new Subject<void>();

  /** Última conta usada num lançamento salvo, só em memória (decisão 16). */
  ultimaConta: number | null = null;
  /** Avisa as telas que um importado voltou para a lista (o "Desfazer" do aviso curto). */
  readonly restaurado$ = this.restaurados.asObservable();

  listar(filtros: Filtros): Observable<ListaDoMes> {
    return this.http.get<ListaDoMes>('/api/lancamentos', { params: parametrosDaApi(filtros) });
  }

  ultimos(): Observable<ItemDaLista[]> {
    return this.http.get<ItemDaLista[]>('/api/lancamentos/ultimos');
  }

  criarLancamento(dados: DadosDoLancamento): Observable<ItemDaLista> {
    return this.http.post<ItemDaLista>('/api/lancamentos', dados);
  }

  atualizarLancamento(id: number, dados: DadosDoLancamento): Observable<ItemDaLista> {
    return this.http.put<ItemDaLista>(`/api/lancamentos/${id}`, dados);
  }

  excluirLancamento(id: number): Observable<void> {
    return this.http.delete(`/api/lancamentos/${id}`).pipe(map(() => undefined));
  }

  /** Volta um importado excluído (ignorado); a categoria só vale se ainda servir (decisão 31). */
  restaurarLancamento(id: number, categoriaId: number | null): Observable<ItemDaLista> {
    return this.http.post<ItemDaLista>(`/api/lancamentos/${id}/restaurar`, { categoriaId }).pipe(tap(() => this.restaurados.next()));
  }

  criarTransferencia(dados: DadosDaTransferencia): Observable<ItemDaLista> {
    return this.http.post<ItemDaLista>('/api/transferencias', dados);
  }

  atualizarTransferencia(id: number, dados: DadosDaTransferencia): Observable<ItemDaLista> {
    return this.http.put<ItemDaLista>(`/api/transferencias/${id}`, dados);
  }

  excluirTransferencia(id: number): Observable<void> {
    return this.http.delete(`/api/transferencias/${id}`).pipe(map(() => undefined));
  }

  /** Fatia 04c: o importado e os pares possíveis para virar transferência. */
  pares(id: number): Observable<ParesDoLancamento> {
    return this.http.get<ParesDoLancamento>(`/api/lancamentos/${id}/pares`);
  }

  /** Fatia 04c: liga o importado a um par ou a um lado novo; responde a transferência. */
  converter(id: number, dados: DadosDaConversao): Observable<ItemDaLista> {
    return this.http.post<ItemDaLista>(`/api/lancamentos/${id}/converter`, dados);
  }

  /** Fatia 04c: desfaz a convertida; responde os dois lançamentos, a saída primeiro. */
  desfazerTransferencia(id: number): Observable<ItemDaLista[]> {
    return this.http.post<ItemDaLista[]>(`/api/transferencias/${id}/desfazer`, null);
  }
}