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

/** Resposta de POST /api/compras/previa (decisão 18). */
export interface PreviaDaCompra {
  readonly parcelas: readonly ParcelaDaCompra[];
  readonly primeiraFatura: { readonly mes: string; readonly fechamento: string; readonly vencimento: string; readonly situacao: SituacaoDaFatura };
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

  previa(dados: { cartaoId: number; data: string; valorTotal: number; parcelas: number; parcelaInicial: number }): Observable<PreviaDaCompra> {
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
