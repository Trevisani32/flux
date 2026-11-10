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

/** Campos inválidos de um 400 ("campos": [{ campo, mensagem }]), por nome do campo. */
export function camposDoErro(erro: unknown): Readonly<Record<string, string>> {
  if (!(erro instanceof HttpErrorResponse) || typeof erro.error !== 'object' || erro.error === null) {
    return {};
  }
  const campos: unknown = (erro.error as Record<string, unknown>)['campos'];
  if (!Array.isArray(campos)) {
    return {};
  }
  const porCampo: Record<string, string> = {};
  for (const item of campos as unknown[]) {
    if (typeof item === 'object' && item !== null) {
      const { campo, mensagem } = item as Record<string, unknown>;
      if (typeof campo === 'string' && typeof mensagem === 'string' && !(campo in porCampo)) {
        porCampo[campo] = mensagem;
      }
    }
  }
  return porCampo;
}

/** Uma propriedade de texto do ProblemDetail (ex.: "uso" no 422 de exclusão bloqueada), ou null. */
export function propriedadeDoErro(erro: unknown, nome: string): string | null {
  if (!(erro instanceof HttpErrorResponse) || typeof erro.error !== 'object' || erro.error === null) {
    return null;
  }
  const valor: unknown = (erro.error as Record<string, unknown>)[nome];
  return typeof valor === 'string' && valor !== '' ? valor : null;
}

/** Campo `detail` do ProblemDetail, ou o texto padrão. */
export function detalheDoErro(erro: unknown, padrao: string): string {
  if (!(erro instanceof HttpErrorResponse) || typeof erro.error !== 'object' || erro.error === null) {
    return padrao;
  }
  const detalhe: unknown = (erro.error as Record<string, unknown>)['detail'];
  return typeof detalhe === 'string' && detalhe !== '' ? detalhe : padrao;
}
