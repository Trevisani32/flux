import { CondicaoDaRegra, GrupoDaAplicacao, Regra } from './regras.service';

const ROTULOS: Readonly<Record<CondicaoDaRegra, string>> = {
  CONTEM: 'contém',
  COMECA_COM: 'começa com',
  IGUAL_A: 'igual a',
};

/** As condições do select, na ordem do mockup. */
export const CONDICOES: readonly { readonly valor: CondicaoDaRegra; readonly rotulo: string }[] = [
  { valor: 'CONTEM', rotulo: ROTULOS.CONTEM },
  { valor: 'COMECA_COM', rotulo: ROTULOS.COMECA_COM },
  { valor: 'IGUAL_A', rotulo: ROTULOS.IGUAL_A },
];

/** "contém", "começa com" ou "igual a". */
export function rotuloDaCondicao(condicao: CondicaoDaRegra): string {
  return ROTULOS[condicao];
}

/** "Contém" com maiúscula, para começar a linha da lista. */
export function condicaoNoInicio(condicao: CondicaoDaRegra): string {
  const rotulo = ROTULOS[condicao];
  return rotulo.charAt(0).toUpperCase() + rotulo.slice(1);
}

/** "contém "uber"" (aspas retas, como nas mensagens da API). */
export function fraseDaCondicao(condicao: CondicaoDaRegra, texto: string): string {
  return `${ROTULOS[condicao]} "${texto}"`;
}

/** "Transporte › App de transporte" ou "Lazer". */
export function nomeDaCategoria(nome: string, mae: string | null): string {
  return mae === null ? nome : `${mae} › ${nome}`;
}

/** A segunda linha da regra na lista: "Despesas · todas as contas · descrição vira "Uber"". */
export function metaDaRegra(regra: Regra): string {
  const partes = [regra.natureza === 'RECEITA' ? 'Receitas' : 'Despesas', regra.contaNome === null ? 'todas as contas' : `só ${regra.contaNome}`];
  if (regra.descricao !== null) {
    partes.push(`descrição vira "${regra.descricao}"`);
  }
  return partes.join(' · ');
}

/** "Pausada: Lazer está arquivada" (decisões 23 e 24); a categoria vem antes da conta. */
export function motivoDaPausa(regra: Regra): string | null {
  if (!regra.pausada) {
    return null;
  }
  return regra.categoriaArquivada ? `Pausada: ${regra.categoriaNome} está arquivada` : `Pausada: ${regra.contaNome ?? 'a conta'} está arquivada`;
}

/** "1 lançamento" ou "37 lançamentos". */
export function quantidadeDeLancamentos(quantidade: number): string {
  return quantidade === 1 ? '1 lançamento' : `${quantidade.toLocaleString('pt-BR')} lançamentos`;
}

/** O botão de "Aplicar regras": conta os lançamentos dos grupos marcados, sem somar dinheiro. */
export function textoDoAplicar(quantidade: number): string {
  return quantidade === 0 ? 'Escolha ao menos uma regra.' : `Aplicar a ${quantidadeDeLancamentos(quantidade)}`;
}

/** A nota de um grupo: "e a descrição vira "Uber"" e "1 lançado à mão: só a categoria muda". */
export function notaDoGrupo(grupo: GrupoDaAplicacao): string | null {
  const partes: string[] = [];
  if (grupo.descricao !== null && grupo.renomeados > 0) {
    partes.push(`e a descrição vira "${grupo.descricao}"`);
  }
  if (grupo.manuais > 0) {
    partes.push(grupo.manuais === 1 ? '1 lançado à mão: só a categoria muda' : `${grupo.manuais} lançados à mão: só a categoria muda`);
  }
  return partes.length === 0 ? null : partes.join(' · ');
}

/**
 * Onde a regra nova entra (decisão 10), com a 1ª atual, se houver: "Entra em 1º lugar, antes de "contém
 * uber eats". Dá para mudar a ordem na lista."
 */
export function posicaoDaNova(primeira: Regra | null): string {
  return primeira === null ? 'Entra em 1º lugar. Dá para mudar a ordem na lista.'
    : `Entra em 1º lugar, antes de "${ROTULOS[primeira.condicao]} ${primeira.texto}". Dá para mudar a ordem na lista.`;
}

/** O aviso curto depois de criar a regra quando a contagem falhou: a regra existe, a conta não veio. */
export const AVISO_SEM_CONTAGEM = 'Regra criada. Não foi possível contar os lançamentos sem categoria que casam com ela.';

/** O aviso curto depois de criar a regra (decisão 19). */
export function avisoDaRegraCriada(semCategoria: number): string {
  if (semCategoria === 0) {
    return 'Regra criada.';
  }
  return semCategoria === 1 ? 'Regra criada. 1 lançamento sem categoria casa com ela.'
    : `Regra criada. ${quantidadeDeLancamentos(semCategoria)} sem categoria casam com ela.`;
}

/** O aviso curto depois de aplicar. */
export function avisoDosAplicados(categorizados: number): string {
  if (categorizados === 0) {
    return 'Nenhum lançamento mudou.';
  }
  return categorizados === 1 ? '1 lançamento categorizado.' : `${quantidadeDeLancamentos(categorizados)} categorizados.`;
}

/** A prévia do diálogo: "Casa com 6 lançamentos seus, 4 sem categoria". */
export function tituloDaPrevia(casam: number, semCategoria: number): string {
  if (casam === 0) {
    return 'Não casa com nenhum lançamento seu ainda';
  }
  const quantos = casam === 1 ? '1 lançamento seu' : `${quantidadeDeLancamentos(casam)} seus`;
  return `Casa com ${quantos}, ${semCategoria} sem categoria`;
}
