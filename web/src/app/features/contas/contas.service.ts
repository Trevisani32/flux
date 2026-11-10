import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

export type TipoConta = 'CORRENTE' | 'POUPANCA' | 'CARTEIRA' | 'INVESTIMENTO';

export type CorConta = 'OURO' | 'LARANJA' | 'ROSA' | 'ROXO' | 'INDIGO' | 'AZUL' | 'CIANO' | 'CINZA';

/** Uma conta como a API devolve. Valores em reais com sinal; datas ISO. */
export interface Conta {
  readonly id: number;
  readonly nome: string;
  readonly tipo: TipoConta;
  readonly saldoInicial: number;
  readonly dataSaldoInicial: string;
  readonly cor: CorConta | null;
  readonly saldoAtual: number;
  readonly arquivadaEm: string | null;
}

/** Somas das ativas, prontas da API (o frontend não faz conta com dinheiro). */
export interface TotaisDasContas {
  readonly geral: number;
  readonly disponivel: number;
  readonly investimentos: number;
}

/** Resposta de GET /api/contas. */
export interface ListaDeContas {
  readonly ativas: readonly Conta[];
  readonly arquivadas: readonly Conta[];
  readonly totais: TotaisDasContas;
}

/** Corpo de POST /api/contas e PUT /api/contas/{id}. */
export interface DadosDaConta {
  readonly nome: string;
  readonly tipo: TipoConta;
  readonly saldoInicial: number;
  readonly dataSaldoInicial: string;
  readonly cor: CorConta | null;
}

@Injectable({ providedIn: 'root' })
export class ContasService {
  private readonly http = inject(HttpClient);

  listar(): Observable<ListaDeContas> {
    return this.http.get<ListaDeContas>('/api/contas');
  }

  criar(dados: DadosDaConta): Observable<Conta> {
    return this.http.post<Conta>('/api/contas', dados);
  }

  atualizar(id: number, dados: DadosDaConta): Observable<Conta> {
    return this.http.put<Conta>(`/api/contas/${id}`, dados);
  }

  arquivar(id: number): Observable<Conta> {
    return this.http.post<Conta>(`/api/contas/${id}/arquivar`, null);
  }

  reativar(id: number): Observable<Conta> {
    return this.http.post<Conta>(`/api/contas/${id}/reativar`, null);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete(`/api/contas/${id}`).pipe(map(() => undefined));
  }
}
