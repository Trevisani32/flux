import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { CorConta } from '../contas/contas.service';
import { Frequencia } from './frases';

export type TipoDaRecorrencia = 'RECEITA' | 'DESPESA';

export interface Recorrencia {
  readonly id: number;
  readonly descricao: string;
  readonly tipo: TipoDaRecorrencia;
  readonly valor: number;
  readonly valorComSinal: number;
  readonly valorVaria: boolean;
  readonly categoriaId: number | null;
  readonly categoriaNome: string | null;
  readonly categoriaMaeNome: string | null;
  readonly categoriaArquivada: boolean;
  readonly contaId: number | null;
  readonly contaNome: string | null;
  readonly cartaoId: number | null;
  readonly cartaoNome: string | null;
  readonly cartaoCor: CorConta | null;
  readonly frequencia: Frequencia;
  readonly diaDoMes: number | null;
  readonly comecaEm: string;
  readonly terminaEm: string | null;
  readonly ocorrenciasDesde: string;
  readonly situacao: 'ATIVA' | 'PAUSADA' | 'ENCERRADA';
  readonly motivoDaPausa: string | null;
  readonly proxima: string | null;
  readonly atrasadas: number;
  readonly confirmadas: number;
}

export interface Ocorrencia {
  readonly recorrenciaId: number;
  readonly periodo: string;
  readonly data: string;
  readonly dataNaLista: string | null;
  readonly descricao: string;
  readonly tipo: TipoDaRecorrencia;
  readonly valor: number;
  readonly valorPrevisto: number;
  readonly valorVaria: boolean;
  readonly contaId: number | null;
  readonly contaNome: string | null;
  readonly cartaoId: number | null;
  readonly cartaoNome: string | null;
  readonly cartaoCor: CorConta | null;
  readonly categoriaId: number | null;
  readonly categoriaNome: string | null;
  readonly categoriaMaeNome: string | null;
  readonly atrasada: boolean;
  readonly visivelAte: string | null;
}

export interface AConfirmar {
  readonly ate: string;
  readonly recorrencias: number;
  readonly ocorrencias: readonly Ocorrencia[];
}

export interface ListaDeRecorrencias {
  readonly ativas: readonly Recorrencia[];
  readonly pausadas: readonly Recorrencia[];
  readonly encerradas: readonly Recorrencia[];
  readonly aConfirmar: AConfirmar;
}

export interface DadosDaRecorrencia {
  readonly tipo: TipoDaRecorrencia;
  readonly descricao: string;
  readonly valor: number;
  readonly valorVaria: boolean;
  readonly categoriaId: number | null;
  readonly contaId: number | null;
  readonly cartaoId: number | null;
  readonly frequencia: Frequencia;
  readonly diaDoMes: number | null;
  readonly comecaEm: string;
  readonly terminaEm: string | null;
}

export interface PedidoDaPrevia {
  readonly frequencia: Frequencia;
  readonly diaDoMes: number | null;
  readonly comecaEm: string;
  readonly terminaEm: string | null;
  readonly cartaoId: number | null;
  readonly recorrenciaId: number | null;
}

export interface PreviaDaRecorrencia {
  readonly desde: string;
  readonly comecaAntes: boolean;
  readonly ocorrencias: readonly {
    readonly data: string;
    readonly ajustada: boolean;
    readonly faturaMes: string | null;
    readonly faturaVencimento: string | null;
  }[];
}

export interface Candidato {
  readonly id: number;
  readonly alvo: 'LANCAMENTO' | 'COMPRA';
  readonly data: string;
  readonly descricao: string;
  readonly valor: number;
  readonly origem: 'MANUAL' | 'IMPORTADO';
  readonly categoriaNome: string | null;
  readonly categoriaMaeNome: string | null;
}

export interface Candidatos {
  readonly de: string;
  readonly ate: string;
  readonly sugerido: Candidato | null;
  readonly candidatos: readonly Candidato[];
}

export interface ItemDoHistorico {
  readonly periodo: string;
  readonly dataPrevista: string;
  readonly situacao: 'PREVISTA' | 'ATRASADA' | 'EFETIVADA' | 'PULADA';
  readonly valor: number | null;
  readonly data: string | null;
  readonly lancamentoId: number | null;
  readonly compraId: number | null;
  readonly mesNaLista: string | null;
  readonly contaId: number | null;
  readonly cartaoId: number | null;
}

export interface Historico {
  readonly recorrenciaId: number;
  readonly descricao: string;
  readonly frequencia: Frequencia;
  readonly desde: string;
  readonly itens: readonly ItemDoHistorico[];
}

export interface Confirmacao {
  readonly recorrenciaId: number;
  readonly periodo: string;
  readonly lancamentoId: number | null;
  readonly compraId: number | null;
  readonly data: string;
  readonly faturaMes: string | null;
  readonly cartaoNome: string | null;
}

export interface DadosDaConfirmacao {
  readonly data: string;
  readonly valor: number;
  readonly observacao: string | null;
}

export interface DadosDaLigacao {
  readonly lancamentoId: number | null;
  readonly compraId: number | null;
}

@Injectable({ providedIn: 'root' })
export class RecorrenciasService {
  private readonly http = inject(HttpClient);

  listar(): Observable<ListaDeRecorrencias> {
    return this.http.get<ListaDeRecorrencias>('/api/recorrencias');
  }

  criar(dados: DadosDaRecorrencia): Observable<Recorrencia> {
    return this.http.post<Recorrencia>('/api/recorrencias', dados);
  }

  buscar(id: number): Observable<Recorrencia> {
    return this.http.get<Recorrencia>(`/api/recorrencias/${id}`);
  }

  editar(id: number, dados: DadosDaRecorrencia): Observable<Recorrencia> {
    return this.http.put<Recorrencia>(`/api/recorrencias/${id}`, dados);
  }

  encerrar(id: number): Observable<Recorrencia> {
    return this.http.post<Recorrencia>(`/api/recorrencias/${id}/encerrar`, {});
  }

  retomar(id: number): Observable<Recorrencia> {
    return this.http.post<Recorrencia>(`/api/recorrencias/${id}/retomar`, {});
  }

  excluir(id: number): Observable<void> {
    return this.http.delete(`/api/recorrencias/${id}`).pipe(map(() => undefined));
  }

  aConfirmar(): Observable<AConfirmar> {
    return this.http.get<AConfirmar>('/api/recorrencias/a-confirmar');
  }

  previa(pedido: PedidoDaPrevia): Observable<PreviaDaRecorrencia> {
    return this.http.post<PreviaDaRecorrencia>('/api/recorrencias/previa', pedido);
  }

  historico(id: number): Observable<Historico> {
    return this.http.get<Historico>(`/api/recorrencias/${id}/historico`);
  }

  candidatos(id: number, periodo: string): Observable<Candidatos> {
    return this.http.get<Candidatos>(`/api/recorrencias/${id}/ocorrencias/${periodo}/candidatos`);
  }

  confirmar(id: number, periodo: string, corpo: DadosDaConfirmacao): Observable<Confirmacao> {
    return this.http.post<Confirmacao>(`/api/recorrencias/${id}/ocorrencias/${periodo}/confirmar`, corpo);
  }

  ligar(id: number, periodo: string, corpo: DadosDaLigacao): Observable<Confirmacao> {
    return this.http.post<Confirmacao>(`/api/recorrencias/${id}/ocorrencias/${periodo}/ligar`, corpo);
  }

  pular(id: number, periodo: string): Observable<Ocorrencia> {
    return this.http.post<Ocorrencia>(`/api/recorrencias/${id}/ocorrencias/${periodo}/pular`, {});
  }

  voltarAPrever(id: number, periodo: string): Observable<void> {
    return this.http.post(`/api/recorrencias/${id}/ocorrencias/${periodo}/voltar-a-prever`, {}).pipe(map(() => undefined));
  }
}
