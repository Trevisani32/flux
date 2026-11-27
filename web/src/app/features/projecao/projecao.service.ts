import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { CorConta, TipoConta } from '../contas/contas.service';
import { Ocorrencia } from '../recorrencias/recorrencias.service';

export interface PeriodosDaProjecao {
  readonly padrao: string;
  readonly tresMeses: string;
  readonly seisMeses: string;
  readonly dozeMeses: string;
}

export interface ContaDaProjecao {
  readonly id: number;
  readonly nome: string;
  readonly tipo: TipoConta;
  readonly cor: CorConta | null;
  readonly padrao: boolean;
  readonly escolhida: boolean;
}

export interface TrechoNegativo {
  readonly de: string;
  readonly ate: string;
}

export interface ResumoDaProjecao {
  readonly hoje: number;
  readonly pendencias: number;
  readonly inicio: number;
  readonly menor: number;
  readonly diaDoMenor: string;
  readonly fim: number;
  readonly diasNegativos: number;
  readonly trechosNegativos: readonly TrechoNegativo[];
}

export interface ResumoDaConta extends ResumoDaProjecao {
  readonly contaId: number;
  readonly nome: string;
  readonly cor: CorConta | null;
}

export interface PrevistoDaFatura {
  readonly recorrenciaId: number;
  readonly periodo: string;
  readonly data: string;
  readonly descricao: string;
  readonly valor: number;
  readonly ocorrencia: Ocorrencia | null;
}

export type TipoDoItemDaProjecao = 'PREVISTO' | 'LANCAMENTO' | 'TRANSFERENCIA' | 'FATURA';

export interface ItemDaProjecao {
  readonly tipo: TipoDoItemDaProjecao;
  readonly data: string;
  readonly descricao: string | null;
  readonly valor: number;
  readonly valorVaria: boolean;
  readonly contaId: number;
  readonly contaNome: string;
  readonly lancamentoId: number | null;
  readonly outraContaId: number | null;
  readonly outraContaNome: string | null;
  readonly cartaoId: number | null;
  readonly cartaoNome: string | null;
  readonly cartaoCor: CorConta | null;
  readonly mes: string | null;
  readonly vencimento: string | null;
  readonly pendente: number | null;
  readonly previsto: number | null;
  readonly previstos: readonly PrevistoDaFatura[];
  readonly ocorrencia: Ocorrencia | null;
}

export interface DiaDaProjecao {
  readonly data: string;
  readonly movimento: number;
  readonly saldo: number;
  readonly itens: readonly ItemDaProjecao[];
}

export interface FaturaFora {
  readonly cartaoId: number;
  readonly cartaoNome: string;
  readonly contaId: number;
  readonly contaNome: string;
  readonly meses: readonly string[];
  readonly valor: number;
}

export interface Projecao {
  readonly hoje: string;
  readonly ate: string;
  readonly diasDepoisDeHoje: number;
  readonly periodos: PeriodosDaProjecao;
  readonly contas: readonly ContaDaProjecao[];
  readonly escolhidas: readonly number[];
  readonly resumo: ResumoDaProjecao;
  readonly porConta: readonly ResumoDaConta[];
  readonly pendencias: readonly ItemDaProjecao[];
  readonly dias: readonly DiaDaProjecao[];
  readonly faturasFora: readonly FaturaFora[];
}

export interface ContaNegativa {
  readonly contaId: number;
  readonly contaNome: string;
  readonly hoje: number;
  readonly primeiroNegativo: string;
  readonly menor: number;
  readonly diaDoMenor: string;
}

export interface AvisoDaProjecao {
  readonly contas: readonly ContaNegativa[];
}

@Injectable({ providedIn: 'root' })
export class ProjecaoService {
  private readonly http = inject(HttpClient);

  projetar(ate: string | null, contas: readonly number[] | null): Observable<Projecao> {
    let params = new HttpParams();
    if (ate !== null) {
      params = params.set('ate', ate);
    }
    if (contas !== null) {
      params = params.set('contas', contas.join(','));
    }
    return this.http.get<Projecao>('/api/projecao', { params });
  }

  aviso(): Observable<AvisoDaProjecao> {
    return this.http.get<AvisoDaProjecao>('/api/projecao/aviso');
  }
}
