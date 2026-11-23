import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { ItemDaLista } from '../lancamentos/lancamentos.service';
import { CorCartao, SituacaoDaFatura } from './cartoes.service';

/** Um pagamento de fatura como a API devolve (Fatia 05b, decisões 30 a 33 e 37). `valor` é positivo. */
export interface Pagamento {
  readonly id: number;
  readonly cartaoId: number;
  readonly cartaoNome: string;
  readonly cartaoArquivado: boolean;
  /** "2026-11". */
  readonly faturaMes: string;
  readonly faturaVencimento: string;
  readonly contaId: number;
  readonly contaNome: string;
  readonly contaArquivada: boolean;
  readonly data: string;
  readonly valor: number;
  readonly observacao: string | null;
  /** IMPORTADO: a despesa do extrato convertida; conta, data e valor não mudam, e ela se desfaz. */
  readonly origem: 'MANUAL' | 'IMPORTADO';
  readonly descricaoBanco: string | null;
  /** A conta ou o cartão está arquivado (decisão 33). */
  readonly somenteLeitura: boolean;
}

/** Corpo de POST /api/cartoes/{id}/faturas/{mes}/pagamentos e PUT /api/pagamentos/{id}. O valor vai positivo. */
export interface DadosDoPagamento {
  readonly contaId: number;
  readonly data: string;
  readonly valor: number;
  readonly observacao: string | null;
}

/** Uma fatura que a despesa importada pode pagar: `sugerida` é a fechada com o pendente igual ao valor (decisão 37). */
export interface FaturaParaPagamento {
  readonly mes: string;
  readonly situacao: SituacaoDaFatura;
  readonly vencimento: string;
  readonly total: number;
  readonly pendente: number;
  readonly sugerida: boolean;
}

/** Resposta de GET /api/lancamentos/{id}/faturas-para-pagamento: a despesa e, por cartão ativo, as faturas, as sugeridas primeiro. */
export interface FaturasParaPagamento {
  readonly lancamento: ItemDaLista;
  readonly cartoes: readonly {
    readonly id: number;
    readonly nome: string;
    readonly cor: CorCartao | null;
    readonly faturas: readonly FaturaParaPagamento[];
  }[];
}

/** O pagamento da fatura e a conversão da despesa importada em pagamento (Fatia 05b). */
@Injectable({ providedIn: 'root' })
export class PagamentosService {
  private readonly http = inject(HttpClient);

  pagar(cartaoId: number, mes: string, dados: DadosDoPagamento): Observable<Pagamento> {
    return this.http.post<Pagamento>(`/api/cartoes/${cartaoId}/faturas/${mes}/pagamentos`, dados);
  }

  buscar(id: number): Observable<Pagamento> {
    return this.http.get<Pagamento>(`/api/pagamentos/${id}`);
  }

  atualizar(id: number, dados: DadosDoPagamento): Observable<Pagamento> {
    return this.http.put<Pagamento>(`/api/pagamentos/${id}`, dados);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete(`/api/pagamentos/${id}`).pipe(map(() => undefined));
  }

  /** O pagamento convertido volta a ser a despesa importada; responde a despesa. */
  desfazer(id: number): Observable<ItemDaLista> {
    return this.http.post<ItemDaLista>(`/api/pagamentos/${id}/desfazer`, null);
  }

  faturasParaPagamento(lancamentoId: number): Observable<FaturasParaPagamento> {
    return this.http.get<FaturasParaPagamento>(`/api/lancamentos/${lancamentoId}/faturas-para-pagamento`);
  }

  converter(lancamentoId: number, cartaoId: number, mes: string): Observable<Pagamento> {
    return this.http.post<Pagamento>(`/api/lancamentos/${lancamentoId}/converter-em-pagamento`, { cartaoId, mes });
  }
}
