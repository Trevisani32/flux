import { HttpClient } from '@angular/common/http';
import { Injectable, Signal, computed, inject, signal } from '@angular/core';
import { Observable, defer, firstValueFrom, from, map, of, shareReplay, finalize, tap, catchError } from 'rxjs';

import { SessaoResponse, Usuario } from './sessao';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly usuarioInterno = signal<Usuario | null>(null);
  private readonly tokenInterno = signal<string | null>(null);
  private renovacaoEmAndamento: Observable<void> | null = null;
  private restauracaoEmAndamento: Promise<void> | null = null;

  readonly usuario: Signal<Usuario | null> = this.usuarioInterno.asReadonly();
  readonly autenticado: Signal<boolean> = computed(() => this.tokenInterno() !== null);
  private readonly http = inject(HttpClient);

  tokenAcesso(): string | null {
    return this.tokenInterno();
  }

  entrar(email: string, senha: string): Observable<void> {
    return this.http.post<SessaoResponse>('/api/auth/entrar', { email, senha }).pipe(
      tap((sessao) => this.guardarSessao(sessao)),
      map(() => undefined),
    );
  }

  renovar(): Observable<void> {
    if (this.renovacaoEmAndamento !== null) {
      return this.renovacaoEmAndamento;
    }

    const renovar = () => this.http.post<SessaoResponse>('/api/auth/renovar', null).pipe(
      tap((sessao) => this.guardarSessao(sessao)),
      map(() => undefined),
    );
    const executar = (): Observable<void> => {
      if (navigator.locks) {
        return from(navigator.locks.request('flux-renovacao', async () => {
          await firstValueFrom(renovar());
        })).pipe(map(() => undefined));
      }
      return renovar();
    };

    const compartilhada: Observable<void> = defer(() => executar()).pipe(
      finalize(() => this.renovacaoEmAndamento = null),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    this.renovacaoEmAndamento = compartilhada;
    return compartilhada;
  }

  restaurarSessao(): Promise<void> {
    if (this.restauracaoEmAndamento !== null) {
      return this.restauracaoEmAndamento;
    }
    this.restauracaoEmAndamento = firstValueFrom(this.renovar().pipe(catchError(() => {
      this.limparSessao();
      return of(undefined);
    }))).then(() => undefined).finally(() => {
      this.restauracaoEmAndamento = null;
    });
    return this.restauracaoEmAndamento;
  }

  sair(): Observable<void> {
    return this.http.post('/api/auth/sair', null).pipe(
      catchError(() => of(null)),
      finalize(() => this.limparSessao()),
      map(() => undefined),
    );
  }

  /** Atualiza nome e e-mail mostrados no shell (Minha conta). Não faz nada sem sessão. */
  atualizarUsuario(usuario: Usuario): void {
    if (this.usuarioInterno() !== null) {
      this.usuarioInterno.set(usuario);
    }
  }

  limparSessao(): void {
    this.tokenInterno.set(null);
    this.usuarioInterno.set(null);
  }

  private guardarSessao(sessao: SessaoResponse): void {
    this.tokenInterno.set(sessao.tokenAcesso);
    this.usuarioInterno.set(sessao.usuario);
  }
}
