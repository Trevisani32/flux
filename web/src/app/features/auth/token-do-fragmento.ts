/** Lê o token de um fragmento como "token=abc". Devolve null se não houver. */
export function tokenDoFragmento(fragmento: string | null): string | null {
  const token = new URLSearchParams(fragmento ?? '').get('token');
  return token !== null && token.trim() !== '' ? token.trim() : null;
}
