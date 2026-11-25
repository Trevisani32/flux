import { HttpClient, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, from, map, switchMap, throwError } from 'rxjs';

export type FormatoDaExportacao = 'JSON' | 'CSV';

export interface ArquivoBaixado {
  readonly nome: string;
  readonly conteudo: Blob;
}

/** O nome que a API dá ao arquivo de hoje ("2026-11-24"): o diálogo mostra antes de baixar. */
export function nomeDoArquivo(formato: FormatoDaExportacao, hoje: string): string {
  return formato === 'JSON' ? `flux-dados-${hoje}.json` : `flux-lancamentos-${hoje}.csv`;
}

/** O nome do Content-Disposition (attachment; filename="flux-dados-2026-11-24.json"), ou o padrão. */
export function nomeDoCabecalho(cabecalho: string | null, padrao: string): string {
  const achado = cabecalho === null ? null : /filename="([^"]+)"/.exec(cabecalho);
  return achado === null ? padrao : achado[1];
}

/**
 * O erro de um pedido com responseType 'blob' traz o ProblemDetail como Blob. Lido como JSON, o erro fica igual ao dos
 * outros pedidos, e statusDoErro e detalheDoErro (erros-http.ts) servem. Corpo que não é JSON vira nulo.
 */
export async function lerCorpoDoErro(erro: unknown): Promise<unknown> {
  if (!(erro instanceof HttpErrorResponse) || !(erro.error instanceof Blob)) {
    return erro;
  }
  let corpo: unknown;
  try {
    corpo = JSON.parse(await erro.error.text()) as unknown;
  } catch {
    corpo = null;
  }
  return new HttpErrorResponse({
    error: corpo,
    headers: erro.headers,
    status: erro.status,
    statusText: erro.statusText,
    url: erro.url ?? undefined,
  });
}

/** Salva o arquivo pelo navegador, com um link temporário com download. A URL é revogada depois, não no clique. */
export function salvarArquivo(documento: Document, arquivo: ArquivoBaixado): void {
  const url = URL.createObjectURL(arquivo.conteudo);
  const link = documento.createElement('a');
  link.href = url;
  link.download = arquivo.nome;
  link.hidden = true;
  documento.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 40_000);
}

@Injectable({ providedIn: 'root' })
export class ExportacaoService {
  private readonly http = inject(HttpClient);

  /** O token fica só na memória: o arquivo vem por este pedido, nunca por um <a href> para a API. */
  baixar(formato: FormatoDaExportacao, senhaAtual: string, nomePadrao: string): Observable<ArquivoBaixado> {
    return this.http.post('/api/minha-conta/exportacao', { formato, senhaAtual }, { observe: 'response', responseType: 'blob' }).pipe(
      map((resposta: HttpResponse<Blob>) => ({
        nome: nomeDoCabecalho(resposta.headers.get('Content-Disposition'), nomePadrao),
        conteudo: resposta.body ?? new Blob([]),
      })),
      catchError((erro: unknown) => from(lerCorpoDoErro(erro)).pipe(switchMap((lido) => throwError(() => lido)))),
    );
  }
}
