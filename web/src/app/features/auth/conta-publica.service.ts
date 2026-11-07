import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

export interface DadosDoCadastro {
  readonly nome: string;
  readonly email: string;
  readonly senha: string;
  readonly aceiteDosTermos: boolean;
}

export interface ConfirmacaoDeEmail {
  readonly tipo: 'CADASTRO' | 'TROCA_DE_EMAIL';
  readonly email: string;
}

/** Endpoints públicos da conta (sem token de acesso; o interceptor ignora /api/auth/). */
@Injectable({ providedIn: 'root' })
export class ContaPublicaService {
  private readonly http = inject(HttpClient);

  cadastrar(dados: DadosDoCadastro): Observable<void> {
    return this.http.post('/api/auth/cadastrar', dados).pipe(map(() => undefined));
  }

  reenviarConfirmacao(email: string): Observable<void> {
    return this.http.post('/api/auth/reenviar-confirmacao', { email }).pipe(map(() => undefined));
  }

  esqueciASenha(email: string): Observable<void> {
    return this.http.post('/api/auth/esqueci-a-senha', { email }).pipe(map(() => undefined));
  }

  redefinirSenha(token: string, novaSenha: string): Observable<void> {
    return this.http.post('/api/auth/redefinir-senha', { token, novaSenha }).pipe(map(() => undefined));
  }

  confirmarEmail(token: string): Observable<ConfirmacaoDeEmail> {
    return this.http.post<ConfirmacaoDeEmail>('/api/auth/confirmar-email', { token });
  }
}
