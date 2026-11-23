import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { SituacaoDaFatura } from './cartoes.service';

export type TipoDaCompra = 'COMPRA' | 'ESTORNO';

/** Uma parcela: o número, o mês da fatura ("2026-12"), o vencimento e o valor positivo. */
export interface ParcelaDaCompra {
  readonly numero: number;
  readonly mes: string;
  readonly vencimento: string;
  readonly valor: number;
  /** Falso nas parcelas de antes da primeira no Flux (decisão 17). */
  readonly noFlux: boolean;
}

/**
 * Resposta de POST /api/compras/previa (decisão 18). Fatia 05b (decisão 38): `disponivelDepois`, o limite disponível
 * com as parcelas desta compra; e `avisoDaFaturaPaga` (decisão 35), a frase que avisa que o lançamento vai ficar travado
 * numa fatura paga (a que já estava paga e continua, ou a que passa a ser paga com ele). Opcionais porque os objetos de
 * teste da 05a não os trazem.
 */
export interface PreviaDaCompra {
  readonly parcelas: readonly ParcelaDaCompra[];
  readonly primeiraFatura: { readonly mes: string; readonly fechamento: string; readonly vencimento: string; readonly situacao: SituacaoDaFatura };
  readonly disponivelDepois?: number;
  readonly avisoDaFaturaPaga?: string | null;
}

/** Uma compra ou um estorno, como a API devolve. `valorTotal` é positivo. */
export interface Compra {
  readonly id: number;
  readonly tipo: TipoDaCompra;
  readonly cartaoId: number;
  readonly cartaoNome: string;
  /** O cartão está arquivado (decisão 5). */
  readonly somenteLeitura: boolean;
  readonly data: string;
  readonly descricao: string;
  readonly valorTotal: number;
  readonly parcelas: number;
  readonly parcelaInicial: number;
  readonly categoriaId: number | null;
  readonly categoriaNome: string | null;
  readonly categoriaMaeNome: string | null;
  readonly categoriaArquivada: boolean;
  readonly observacao: string | null;
  readonly parcelasNoFlux: readonly ParcelaDaCompra[];
  /**
   * Fatia 05b (decisão 35): com uma parcela numa fatura paga, a frase da trava ("A parcela 1/3 está na fatura de
   * novembro, que já foi paga: …"); só a descrição, a categoria e a observação mudam. Opcional pelos objetos de teste da 05a.
   */
  readonly travaDaFaturaPaga?: string | null;
}

/** Corpo de POST/PUT /api/compras. O valor total vai positivo. */
export interface DadosDaCompra {
  readonly cartaoId: number;
  readonly data: string;
  readonly descricao: string;
  readonly valorTotal: number;
  readonly parcelas: number;
  readonly parcelaInicial: number;
  readonly categoriaId: number | null;
  readonly observacao: string | null;
}

/** Corpo de POST/PUT /api/estornos. O valor vai positivo. */
export interface DadosDoEstorno {
  readonly cartaoId: number;
  readonly data: string;
  readonly descricao: string;
  readonly valor: number;
  readonly categoriaId: number | null;
  readonly observacao: string | null;
}

/** Compras e estornos no cartão (decisões 14 a 20 da 05a). */
@Injectable({ providedIn: 'root' })
export class ComprasService {
  private readonly http = inject(HttpClient);

  /** `tipo` só vai no estorno (sem ele, a API entende compra); `compraId`, só na edição (a API desconta as parcelas atuais). */
  previa(dados: { cartaoId: number; data: string; valorTotal: number; parcelas: number; parcelaInicial: number; tipo?: 'ESTORNO';
    compraId?: number }): Observable<PreviaDaCompra> {
    return this.http.post<PreviaDaCompra>('/api/compras/previa', dados);
  }

  buscar(tipo: TipoDaCompra, id: number): Observable<Compra> {
    return this.http.get<Compra>(`${caminho(tipo)}/${id}`);
  }

  criarCompra(dados: DadosDaCompra): Observable<Compra> {
    return this.http.post<Compra>('/api/compras', dados);
  }

  atualizarCompra(id: number, dados: DadosDaCompra): Observable<Compra> {
    return this.http.put<Compra>(`/api/compras/${id}`, dados);
  }

  criarEstorno(dados: DadosDoEstorno): Observable<Compra> {
    return this.http.post<Compra>('/api/estornos', dados);
  }

  atualizarEstorno(id: number, dados: DadosDoEstorno): Observable<Compra> {
    return this.http.put<Compra>(`/api/estornos/${id}`, dados);
  }

  excluir(tipo: TipoDaCompra, id: number): Observable<void> {
    return this.http.delete(`${caminho(tipo)}/${id}`).pipe(map(() => undefined));
  }
}

function caminho(tipo: TipoDaCompra): string {
  return tipo === 'ESTORNO' ? '/api/estornos' : '/api/compras';
}
