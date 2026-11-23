import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { CorConta } from '../contas/contas.service';

/** A paleta é a das contas (decisão 1 da 05a). */
export type CorCartao = CorConta;

/** Fatia 05b (decisão 34): PAGA é a fechada com pagamento que cobre o total, ou marcada como paga fora do Flux. */
export type SituacaoDaFatura = 'FUTURA' | 'ABERTA' | 'FECHADA' | 'PAGA';

/** Uma fatura resumida no cartão: `mes` é "2026-12"; `total` positivo é a pagar, negativo é crédito. */
export interface FaturaDoCartao {
  readonly mes: string;
  readonly inicio: string;
  readonly fechamento: string;
  readonly vencimento: string;
  readonly total: number;
  readonly lancamentos: number;
}

/** Um cartão como a API devolve (decisões 1 a 6, 26 e 29 da 05a). Valores prontos, com sinal. */
export interface Cartao {
  readonly id: number;
  readonly nome: string;
  readonly limite: number;
  readonly diaFechamento: number;
  readonly diaVencimento: number;
  readonly contaPagamentoId: number;
  readonly contaPagamentoNome: string;
  readonly contaPagamentoArquivada: boolean;
  readonly cor: CorCartao | null;
  readonly arquivadoEm: string | null;
  readonly faturaAberta: FaturaDoCartao;
  readonly proximaFatura: FaturaDoCartao;
  /** A soma das faturas aberta e futuras, para a confirmação de arquivar (decisão 5). */
  readonly emAberto: number;
  readonly lancamentosEmAberto: number;
  /**
   * Fatia 05b (decisão 38): o que falta pagar em todas as faturas; o disponível = limite − pendente (negativo: acima do
   * limite); de 0 a 100, a parte do limite tomada pelo pendente, para a barra. A API manda sempre; opcionais porque os
   * objetos de teste da 05a não os trazem.
   */
  readonly pendente?: number;
  readonly disponivel?: number;
  readonly usoDoLimite?: number;
}

/** Resposta de GET /api/cartoes. */
export interface ListaDeCartoes {
  readonly ativos: readonly Cartao[];
  readonly arquivados: readonly Cartao[];
}

/** Corpo de POST /api/cartoes e PUT /api/cartoes/{id}. */
export interface DadosDoCartao {
  readonly nome: string;
  readonly limite: number;
  readonly diaFechamento: number;
  readonly diaVencimento: number;
  readonly contaPagamentoId: number;
  readonly cor: CorCartao | null;
}

/** Decisão 7: a aberta, que continua, e a primeira fatura com os dias novos. */
/**
 * A prévia dos dias novos (decisão 7): `troca` com o cartão que tem lançamento ou ajuste de fechamento; `valemJa`
 * quando ele não tem nenhum dos dois e os dias novos valem já, inclusive na aberta; com os dias iguais, nenhum
 * dos dois.
 */
export interface PreviaDosDias {
  readonly troca: TrocaDeDias | null;
  readonly valemJa: boolean;
}

export interface TrocaDeDias {
  readonly abertaMes: string;
  readonly abertaFechamento: string;
  readonly primeiraMes: string;
  readonly primeiraInicio: string;
  readonly primeiraFechamento: string;
  readonly primeiraVencimento: string;
}

/** Uma parcela ou um estorno na fatura. `valor` com sinal; `valorTotal`, o da compra, positivo. */
export interface ItemDaFatura {
  readonly lancamentoId: number;
  readonly compraId: number;
  readonly tipo: 'COMPRA' | 'ESTORNO';
  readonly dataCompra: string;
  readonly descricao: string;
  readonly valor: number;
  readonly parcela: number;
  readonly parcelas: number;
  readonly parcelaInicial: number;
  readonly valorTotal: number;
  readonly categoriaId: number | null;
  readonly categoriaNome: string | null;
  readonly categoriaMaeNome: string | null;
}

/** Um pagamento na fatura (Fatia 05b, decisão 33): `valor` negativo, como na conta. */
export interface PagamentoNaFatura {
  readonly id: number;
  readonly data: string;
  readonly contaId: number;
  readonly contaNome: string;
  readonly valor: number;
  readonly origem: 'MANUAL' | 'IMPORTADO';
}

/** Resposta de GET /api/cartoes/{id}/faturas/{mes} (decisões 8 a 13). */
export interface Fatura {
  readonly cartaoId: number;
  readonly cartaoNome: string;
  readonly cartaoCor: CorCartao | null;
  readonly cartaoArquivado: boolean;
  readonly mes: string;
  readonly inicio: string;
  readonly fechamento: string;
  readonly vencimento: string;
  /** Se a fatura existe; sem ela, as datas são as dos dias do cartão (decisão 9). */
  readonly gravada: boolean;
  readonly fechamentoAjustado: boolean;
  /** O fechamento pelos dias que valiam para a fatura (decisão 7), para "Voltar ao fechamento normal". */
  readonly fechamentoNormal: string;
  readonly ajusteDe: string;
  readonly ajusteAte: string;
  readonly ajusteAteOVencimento: boolean;
  readonly situacao: SituacaoDaFatura;
  readonly compras: number;
  readonly quantidadeCompras: number;
  readonly estornos: number;
  readonly quantidadeEstornos: number;
  readonly total: number;
  readonly depois: number;
  readonly depoisAte: string | null;
  readonly primeiroMes: string;
  readonly ultimoMes: string;
  readonly itens: readonly ItemDaFatura[];
  /**
   * Fatia 05b (decisões 31 a 38): o pago e quantos pagamentos; o pendente (total − pago, nunca negativo; zero na paga fora
   * do Flux); o pago a mais; a marca de paga fora; o limite e o disponível do cartão, com a parte do limite tomada (de 0 a
   * 100, para a barra); a conta que vem no pagamento; e os pagamentos, o mais antigo primeiro. A API manda sempre;
   * opcionais porque os objetos de teste da 05a não os trazem (a tela lê a falta como zero e sem pagamento).
   */
  readonly pago?: number;
  readonly quantidadePagamentos?: number;
  readonly pendente?: number;
  readonly pagoAMais?: number;
  readonly pagaFora?: boolean;
  readonly limite?: number;
  readonly disponivel?: number;
  readonly usoDoLimite?: number;
  readonly contaPagamentoId?: number;
  readonly pagamentos?: readonly PagamentoNaFatura[];
}

/** Resposta do ajuste e da volta ao normal (decisão 12): as compras e os estornos que mudaram de fatura, à parte. */
export interface ResultadoDoAjuste {
  readonly comprasMovidas: number;
  readonly estornosMovidos: number;
  readonly fatura: Fatura;
}

@Injectable({ providedIn: 'root' })
export class CartoesService {
  private readonly http = inject(HttpClient);

  listar(): Observable<ListaDeCartoes> {
    return this.http.get<ListaDeCartoes>('/api/cartoes');
  }

  criar(dados: DadosDoCartao): Observable<Cartao> {
    return this.http.post<Cartao>('/api/cartoes', dados);
  }

  atualizar(id: number, dados: DadosDoCartao): Observable<Cartao> {
    return this.http.put<Cartao>(`/api/cartoes/${id}`, dados);
  }

  /** Decisão 7: o aviso do diálogo de edição; `null` quando os dias novos valem já. */
  previaDosDias(id: number, diaFechamento: number, diaVencimento: number): Observable<PreviaDosDias> {
    return this.http.post<PreviaDosDias>(`/api/cartoes/${id}/previa-dos-dias`, { diaFechamento, diaVencimento });
  }

  arquivar(id: number): Observable<Cartao> {
    return this.http.post<Cartao>(`/api/cartoes/${id}/arquivar`, null);
  }

  reativar(id: number): Observable<Cartao> {
    return this.http.post<Cartao>(`/api/cartoes/${id}/reativar`, null);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete(`/api/cartoes/${id}`).pipe(map(() => undefined));
  }

  fatura(id: number, mes: string): Observable<Fatura> {
    return this.http.get<Fatura>(`/api/cartoes/${id}/faturas/${mes}`);
  }

  ajustarFechamento(id: number, mes: string, data: string): Observable<ResultadoDoAjuste> {
    return this.http.put<ResultadoDoAjuste>(`/api/cartoes/${id}/faturas/${mes}/fechamento`, { data });
  }

  voltarAoFechamentoNormal(id: number, mes: string): Observable<ResultadoDoAjuste> {
    return this.http.delete<ResultadoDoAjuste>(`/api/cartoes/${id}/faturas/${mes}/fechamento`);
  }

  /** Fatia 05b (decisão 36): "Marcar como paga fora do Flux"; responde a fatura. */
  marcarPagaFora(id: number, mes: string): Observable<Fatura> {
    return this.http.put<Fatura>(`/api/cartoes/${id}/faturas/${mes}/paga-fora`, null);
  }

  /** Fatia 05b (decisão 36): "Desmarcar"; responde a fatura. */
  desmarcarPagaFora(id: number, mes: string): Observable<Fatura> {
    return this.http.delete<Fatura>(`/api/cartoes/${id}/faturas/${mes}/paga-fora`);
  }
}
