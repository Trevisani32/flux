/** Primeira letra do nome, para o avatar. Nome em branco dá texto vazio. */
export function inicialDoNome(nome: string): string {
  const limpo = nome.trim();
  if (limpo === '') {
    return '';
  }
  return Array.from(limpo)[0].toLocaleUpperCase('pt-BR');
}
