import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

export type Natureza = 'RECEITA' | 'DESPESA';

/**
 * Uma categoria como a API devolve. Numa principal, `subcategorias` traz as que estão no
 * mesmo estado dela (ativas numa ativa, arquivadas numa arquivada).
 */
export interface Categoria {
  readonly id: number;
  readonly nome: string;
  readonly natureza: Natureza;
  readonly categoriaMaeId: number | null;
  readonly nomeDaMae: string | null;
  readonly arquivadaEm: string | null;
  readonly subcategorias: readonly Categoria[];
}

export interface GrupoDeCategorias {
  readonly ativas: readonly Categoria[];
  readonly arquivadas: readonly Categoria[];
}

/** Resposta de GET /api/categorias. */
export interface ListaDeCategorias {
  readonly despesas: GrupoDeCategorias;
  readonly receitas: GrupoDeCategorias;
}

export interface NovaCategoria {
  readonly nome: string;
  readonly natureza: Natureza;
  readonly categoriaMaeId: number | null;
}

export interface EdicaoDeCategoria {
  readonly nome: string;
  readonly categoriaMaeId: number | null;
}

@Injectable({ providedIn: 'root' })
export class CategoriasService {
  private readonly http = inject(HttpClient);

  listar(): Observable<ListaDeCategorias> {
    return this.http.get<ListaDeCategorias>('/api/categorias');
  }

  criar(dados: NovaCategoria): Observable<Categoria> {
    return this.http.post<Categoria>('/api/categorias', dados);
  }

  atualizar(id: number, dados: EdicaoDeCategoria): Observable<Categoria> {
    return this.http.put<Categoria>(`/api/categorias/${id}`, dados);
  }

  arquivar(id: number): Observable<Categoria> {
    return this.http.post<Categoria>(`/api/categorias/${id}/arquivar`, null);
  }

  reativar(id: number): Observable<Categoria> {
    return this.http.post<Categoria>(`/api/categorias/${id}/reativar`, null);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete(`/api/categorias/${id}`).pipe(map(() => undefined));
  }
}
