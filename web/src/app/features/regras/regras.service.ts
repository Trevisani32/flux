import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { Natureza } from '../categorias/categorias.service';

export type CondicaoDaRegra = 'CONTEM' | 'COMECA_COM' | 'IGUAL_A';

/**
 * Uma regra como a API devolve (GET /api/regras). `contaId` nulo = todas as contas. `pausada`: a
 * categoria ou a conta está arquivada, e a regra não age (decisões 23 e 24).
 */
export interface Regra {
  readonly id: number;
  readonly posicao: number;
  readonly condicao: CondicaoDaRegra;
  readonly texto: string;
  readonly contaId: number | null;
  readonly contaNome: string | null;
  readonly contaArquivada: boolean;
  readonly natureza: Natureza;
  readonly categoriaId: number;
  readonly categoriaNome: string;
  readonly categoriaMaeNome: string | null;
  readonly categoriaArquivada: boolean;
  readonly descricao: string | null;
  readonly pausada: boolean;
}

/** Corpo de POST e PUT /api/regras: a natureza vem da categoria (decisão 6). */
export interface DadosDaRegra {
  readonly condicao: CondicaoDaRegra;
  readonly texto: string;
  readonly contaId: number | null;
  readonly categoriaId: number;
  readonly descricao: string | null;
}

/** POST /api/regras/previa: o que a condição pega, sem olhar as outras regras (decisão 12). */
export interface PreviaDaRegra {
  readonly casam: number;
  readonly semCategoria: number;
  readonly exemplos: readonly { readonly data: string; readonly descricao: string; readonly valor: number }[];
}

export interface PedidoDePrevia {
  readonly condicao: CondicaoDaRegra;
  readonly texto: string;
  readonly contaId: number | null;
  readonly categoriaId: number | null;
}

/** Um grupo de "Aplicar regras" (decisão 17): a regra e os lançamentos sem categoria que ela pega. */
export interface GrupoDaAplicacao {
  readonly regraId: number;
  readonly posicao: number;
  readonly condicao: CondicaoDaRegra;
  readonly texto: string;
  readonly categoriaId: number;
  readonly categoriaNome: string;
  readonly categoriaMaeNome: string | null;
  readonly descricao: string | null;
  readonly quantidade: number;
  readonly manuais: number;
  readonly renomeados: number;
  readonly exemplos: readonly { readonly data: string; readonly descricao: string; readonly contaNome: string; readonly valor: number }[];
}

/** GET /api/regras/aplicacao: nada foi gravado. */
export interface Aplicacao {
  readonly total: number;
  readonly grupos: readonly GrupoDaAplicacao[];
}

/** Um importado que ganhou categoria à mão (decisão 20). `descricao`: a que o usuário vê, se mudou. */
export interface EscolhaAMao {
  readonly contaId: number;
  readonly descricaoBanco: string;
  readonly descricao: string | null;
  readonly categoriaId: number;
}

/** Uma regra pronta para "Nova regra" (decisão 21); `deDescricao` é a do extrato de onde ela saiu. */
export interface SugestaoDeRegra {
  readonly condicao: CondicaoDaRegra;
  readonly texto: string;
  readonly categoriaId: number;
  readonly categoriaNome: string;
  readonly categoriaMaeNome: string | null;
  readonly natureza: Natureza;
  readonly descricao: string | null;
  readonly deDescricao: string;
}

@Injectable({ providedIn: 'root' })
export class RegrasService {
  private readonly http = inject(HttpClient);

  listar(): Observable<Regra[]> {
    return this.http.get<Regra[]>('/api/regras');
  }

  criar(dados: DadosDaRegra): Observable<Regra> {
    return this.http.post<Regra>('/api/regras', dados);
  }

  atualizar(id: number, dados: DadosDaRegra): Observable<Regra> {
    return this.http.put<Regra>(`/api/regras/${id}`, dados);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete(`/api/regras/${id}`).pipe(map(() => undefined));
  }

  /** Todos os ids, na ordem nova (decisão 9). */
  reordenar(ids: readonly number[]): Observable<Regra[]> {
    return this.http.put<Regra[]>('/api/regras/ordem', { ids });
  }

  previa(pedido: PedidoDePrevia): Observable<PreviaDaRegra> {
    return this.http.post<PreviaDaRegra>('/api/regras/previa', pedido);
  }

  /** A prévia de "Aplicar regras"; com `regraId`, só o grupo dela. */
  aplicacao(regraId: number | null): Observable<Aplicacao> {
    const params = regraId === null ? undefined : new HttpParams().set('regra', regraId);
    return this.http.get<Aplicacao>('/api/regras/aplicacao', { params });
  }

  aplicar(regras: readonly number[]): Observable<number> {
    return this.http.post<{ categorizados: number }>('/api/regras/aplicacao', { regras }).pipe(map((resposta) => resposta.categorizados));
  }

  sugestoes(itens: readonly EscolhaAMao[]): Observable<SugestaoDeRegra[]> {
    return this.http.post<SugestaoDeRegra[]>('/api/regras/sugestoes', { itens });
  }
}
