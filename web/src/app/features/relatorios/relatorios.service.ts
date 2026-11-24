import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** A comparação com a média (Fatia 06a): a API calcula; `percentual` é inteiro e sem sinal (ABAIXO 12 = 12% abaixo). */
export type TipoDaComparacao = 'ACIMA' | 'ABAIXO' | 'IGUAL' | 'NOVO';

export interface Comparacao {
  readonly tipo: TipoDaComparacao;
  readonly percentual: number;
}

/**
 * O saldo total em `data` (hoje no mês atual, o último dia num mês passado). `total` e `investimentos` são nulos antes do
 * primeiro saldo inicial; `contas` conta as que entram nele.
 */
export interface SaldoDoRelatorio {
  readonly data: string;
  readonly hoje: boolean;
  readonly total: number | null;
  readonly investimentos: number | null;
  readonly contas: number;
  readonly contasDeInvestimento: number;
}

/** Receitas ou despesas do mês, sem sinal. `media` e `comparacao` são nulas sem meses anteriores com dados. */
export interface TotalDoRelatorio {
  readonly valor: number;
  readonly quantidade: number;
  /** As despesas de fatura de cartão (0 nas receitas). */
  readonly noCartao: number;
  readonly media: number | null;
  readonly comparacao: Comparacao | null;
}

/** Uma linha dos gastos por categoria: `id` nulo é "Sem categoria"; `direto` é "Direto em Alimentação", sem link. */
export interface CategoriaDoRelatorio {
  readonly id: number | null;
  readonly nome: string;
  readonly arquivada: boolean;
  readonly direto: boolean;
  readonly valor: number;
  readonly media: number | null;
  readonly comparacao: Comparacao | null;
  readonly subcategorias: readonly CategoriaDoRelatorio[];
}

/** Um mês dos gráficos: `saldo` é nulo antes do primeiro saldo inicial; `dataDoSaldo` é hoje no mês atual. */
export interface MesDoRelatorio {
  readonly mes: string;
  readonly receitas: number;
  readonly despesas: number;
  readonly resultado: number;
  readonly dataDoSaldo: string;
  readonly saldo: number | null;
}

/** Resposta de GET /api/relatorios/mes. `primeiroMes` nulo: o usuário não tem nenhuma conta. */
export interface RelatorioDoMes {
  readonly mes: string;
  readonly mesAtual: string;
  readonly primeiroMes: string | null;
  readonly hoje: string;
  readonly mesesDaMedia: readonly string[];
  readonly saldo: SaldoDoRelatorio;
  readonly receitas: TotalDoRelatorio;
  readonly despesas: TotalDoRelatorio;
  readonly resultado: number;
  readonly categorias: readonly CategoriaDoRelatorio[];
  readonly meses: readonly MesDoRelatorio[];
}

@Injectable({ providedIn: 'root' })
export class RelatoriosService {
  private readonly http = inject(HttpClient);

  /** O relatório de um mês ("2026-10"); sem mês, o atual. A API devolve o atual quando o mês está fora da faixa. */
  doMes(mes: string | null): Observable<RelatorioDoMes> {
    return this.http.get<RelatorioDoMes>('/api/relatorios/mes', { params: mes === null ? {} : { mes } });
  }
}
