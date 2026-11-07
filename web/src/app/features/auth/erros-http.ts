import { HttpErrorResponse } from '@angular/common/http';

export const MENSAGEM_MUITAS_TENTATIVAS = 'Muitas tentativas. Tente de novo em alguns minutos.';

/** Status HTTP do erro, ou 0 quando não é uma resposta HTTP. */
export function statusDoErro(erro: unknown): number {
  return erro instanceof HttpErrorResponse ? erro.status : 0;
}

/** Campo `codigo` do ProblemDetail (ex.: "EMAIL_NAO_CONFIRMADO", "LINK_INVALIDO"). */
export function codigoDoErro(erro: unknown): string | null {
  if (!(erro instanceof HttpErrorResponse) || typeof erro.error !== 'object' || erro.error === null) {
    return null;
  }
  const codigo: unknown = (erro.error as Record<string, unknown>)['codigo'];
  return typeof codigo === 'string' ? codigo : null;
}

/** Campo `detail` do ProblemDetail, ou o texto padrão. */
export function detalheDoErro(erro: unknown, padrao: string): string {
  if (!(erro instanceof HttpErrorResponse) || typeof erro.error !== 'object' || erro.error === null) {
    return padrao;
  }
  const detalhe: unknown = (erro.error as Record<string, unknown>)['detail'];
  return typeof detalhe === 'string' && detalhe !== '' ? detalhe : padrao;
}
