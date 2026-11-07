import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

export interface TrocaDeEmailPendente {
  readonly email: string;
  readonly expiraEm: string;
}

export interface TermosAceitos {
  readonly versao: string;
  readonly aceitosEm: string;
}

/** Resposta de GET /api/usuarios/me. */
export interface Conta {
  readonly id: number;
  readonly nome: string;
  readonly email: string;
  readonly trocaDeEmailPendente: TrocaDeEmailPendente | null;
  readonly termos: TermosAceitos | null;
}

@Injectable({ providedIn: 'root' })
export class MinhaContaService {
  private readonly http = inject(HttpClient);

  carregar(): Observable<Conta> {
    return this.http.get<Conta>('/api/usuarios/me');
  }

  renomear(nome: string): Observable<Conta> {
    return this.http.put<Conta>('/api/usuarios/me/nome', { nome });
  }

  pedirTrocaDeEmail(novoEmail: string, senhaAtual: string): Observable<Conta> {
    return this.http.post<Conta>('/api/usuarios/me/email', { novoEmail, senhaAtual });
  }

  excluir(senhaAtual: string): Observable<void> {
    return this.http.delete('/api/usuarios/me', { body: { senhaAtual } }).pipe(map(() => undefined));
  }
}
