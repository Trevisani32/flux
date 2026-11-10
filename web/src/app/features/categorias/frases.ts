import { TrechoDeTexto } from '../../shared/confirmacao/confirmacao-dialogo';

const POR_EXTENSO = ['', 'uma', 'duas', 'três', 'quatro', 'cinco', 'seis', 'sete', 'oito', 'nove', 'dez'];

/** "Aluguel", "Aluguel e Condomínio", "Aluguel, Condomínio e Contas da casa", com os nomes em negrito. */
export function nomesEmNegrito(nomes: readonly string[]): TrechoDeTexto[] {
  const trechos: TrechoDeTexto[] = [];
  nomes.forEach((nome, indice) => {
    if (indice > 0) {
      trechos.push(indice === nomes.length - 1 ? ' e ' : ', ');
    }
    trechos.push({ forte: nome });
  });
  return trechos;
}

/** Parágrafos do diálogo "Arquivar {principal}?" quando ela tem subcategorias ativas. */
export function paragrafosDeArquivar(principal: string, subcategoriasAtivas: readonly string[]): TrechoDeTexto[][] {
  const quantidade = subcategoriasAtivas.length;
  const total = quantidade + 1;
  const primeiro: TrechoDeTexto[] = quantidade === 1
    ? ['A subcategoria ativa também será arquivada: ', ...nomesEmNegrito(subcategoriasAtivas), '.']
    : [`As ${quantidade} subcategorias ativas também serão arquivadas: `, ...nomesEmNegrito(subcategoriasAtivas), '.'];
  const todas = total < POR_EXTENSO.length ? POR_EXTENSO[total] : String(total);
  return [
    primeiro,
    [`Elas somem dos seletores, e os lançamentos continuam nos relatórios. Reativar ${principal} traz as ${todas} de volta.`],
  ];
}

/** Parágrafos do diálogo "Excluir {categoria}?". {@code subcategorias} = todas, ativas e arquivadas. */
export function paragrafosDeExcluir(subcategorias: readonly string[]): TrechoDeTexto[][] {
  if (subcategorias.length === 0) {
    return [['A categoria sai do Flux de vez. ', { forte: 'Não dá para desfazer.' }]];
  }
  const primeiro: TrechoDeTexto[] = subcategorias.length === 1
    ? ['A subcategoria também será excluída: ', ...nomesEmNegrito(subcategorias), '.']
    : [`As ${subcategorias.length} subcategorias também serão excluídas: `, ...nomesEmNegrito(subcategorias), '.'];
  return [primeiro, [{ forte: 'Não dá para desfazer.' }]];
}

/** "1 subcategoria", "3 subcategorias". */
export function quantidadeDeSubcategorias(quantidade: number): string {
  return quantidade === 1 ? '1 subcategoria' : `${quantidade} subcategorias`;
}

/**
 * O parágrafo de "Esta categoria não pode ser excluída" (decisão 33): "Alimentação e as
 * subcategorias têm 42 lançamentos." ou "Mercado tem 9 lançamentos.", com o que fazer.
 */
export function paragrafoDoBloqueio(nome: string, temSubcategorias: boolean, uso: string, arquivada: boolean): TrechoDeTexto[] {
  const quem: TrechoDeTexto[] = temSubcategorias ? [{ forte: nome }, ` e as subcategorias têm ${uso}.`] : [{ forte: nome }, ` tem ${uso}.`];
  return [...quem, arquivada ? ' Ela continua arquivada, com os lançamentos nos relatórios.'
    : ' Para tirá-la dos seletores sem perder o histórico, arquive a categoria.'];
}
