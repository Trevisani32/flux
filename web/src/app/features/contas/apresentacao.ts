import { NomeIcone } from '../../shared/icone/icones';
import { CorConta, TipoConta } from './contas.service';

export interface ApresentacaoDoTipo {
  readonly valor: TipoConta;
  readonly nome: string;
  readonly icone: NomeIcone;
}

/** Os tipos na ordem do diálogo, com o nome da tela e o ícone. */
export const TIPOS_DE_CONTA: readonly ApresentacaoDoTipo[] = [
  { valor: 'CORRENTE', nome: 'Conta corrente', icone: 'contas' },
  { valor: 'POUPANCA', nome: 'Poupança', icone: 'cofre' },
  { valor: 'CARTEIRA', nome: 'Carteira', icone: 'carteira' },
  { valor: 'INVESTIMENTO', nome: 'Investimento', icone: 'alta' },
];

export interface ApresentacaoDaCor {
  readonly valor: CorConta;
  readonly nome: string;
}

/** A paleta, na ordem do diálogo. Cada cor é um token --flux-cor-conta-<nome em minúsculas>. */
export const CORES_DE_CONTA: readonly ApresentacaoDaCor[] = [
  { valor: 'OURO', nome: 'Ouro' },
  { valor: 'LARANJA', nome: 'Laranja' },
  { valor: 'ROSA', nome: 'Rosa' },
  { valor: 'ROXO', nome: 'Roxo' },
  { valor: 'INDIGO', nome: 'Índigo' },
  { valor: 'AZUL', nome: 'Azul' },
  { valor: 'CIANO', nome: 'Ciano' },
  { valor: 'CINZA', nome: 'Cinza' },
];

export function tipoDaConta(tipo: TipoConta): ApresentacaoDoTipo {
  return TIPOS_DE_CONTA.find((item) => item.valor === tipo) ?? TIPOS_DE_CONTA[0];
}

/** Valor do CSS para a cor da conta; sem cor, null (o selo fica dourado). */
export function corDaConta(cor: CorConta | null): string | null {
  return cor === null ? null : `var(--flux-cor-conta-${cor.toLowerCase()})`;
}

/** "1 conta ativa", "5 contas ativas". */
export function quantidadeDeContasAtivas(quantidade: number): string {
  return quantidade === 1 ? '1 conta ativa' : `${quantidade} contas ativas`;
}
