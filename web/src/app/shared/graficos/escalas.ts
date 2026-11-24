import { SINAL_MENOS } from '../moeda/moeda';

/**
 * As escalas dos gráficos (Fatia 06a, ADR 0020): funções puras, sem Angular. Desenhar não é fazer conta com dinheiro: os
 * valores chegam prontos da API e aqui só viram posição na tela. As marcas do eixo são "redondas" (1, 2, 2,5 ou 5 vezes
 * uma potência de 10, nunca menos de R$ 1 entre duas), sempre com o zero; o eixo só desce abaixo de zero quando há valor
 * negativo.
 */

const MULTIPLOS = [1, 2, 2.5, 5, 10] as const;
const INTERVALOS = 4;
const EIXO = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2 });

/** As marcas do eixo, da menor para a maior, de min(0, menor valor) a max(0, maior valor), em cerca de 4 intervalos. */
export function marcasDoEixo(valores: readonly number[]): number[] {
  const menor = Math.min(0, ...valores);
  const maior = Math.max(0, ...valores);
  if (menor === 0 && maior === 0) {
    return [0, 1];
  }
  const bruto = (maior - menor) / INTERVALOS;
  const potencia = 10 ** Math.floor(Math.log10(bruto));
  const passo = Math.max(1, MULTIPLOS.map((multiplo) => multiplo * potencia).find((candidato) => candidato >= bruto) ?? 10 * potencia);
  const primeira = Math.floor(menor / passo + 1e-9);
  const ultima = Math.ceil(maior / passo - 1e-9);
  const marcas: number[] = [];
  for (let indice = primeira; indice <= ultima; indice++) {
    marcas.push(Math.round(indice * passo * 100) / 100);
  }
  return marcas;
}

/** O texto de uma marca: "0", "500", "2,5 mil", "40 mil", "−5 mil". */
export function textoDoEixo(valor: number): string {
  if (valor === 0) {
    return '0';
  }
  const absoluto = Math.abs(valor);
  const texto = absoluto >= 1000 ? `${EIXO.format(absoluto / 1000)} mil` : EIXO.format(absoluto);
  return valor < 0 ? `${SINAL_MENOS}${texto}` : texto;
}

/** A posição vertical de um valor: a maior marca fica em `topo`, a menor em `topo + altura`. */
export function escalaVertical(marcas: readonly number[], topo: number, altura: number): (valor: number) => number {
  const menor = marcas[0];
  const maior = marcas[marcas.length - 1];
  return (valor) => topo + (altura * (maior - valor)) / (maior - menor);
}

/** A faixa de cada mês: a largura (no máximo `maximo`) e o começo da primeira, com as faixas centradas na área. */
export function faixas(quantidade: number, esquerda: number, largura: number, maximo = Number.POSITIVE_INFINITY): { inicio: number; largura: number } {
  const daFaixa = Math.min(largura / Math.max(quantidade, 1), maximo);
  return { inicio: esquerda + (largura - daFaixa * quantidade) / 2, largura: daFaixa };
}

/** A largura de cada uma das duas colunas de uma faixa: até 14 px, com 10 px de folga e 2 px entre elas. */
export function larguraDaColuna(faixa: number): number {
  return Math.max(2, Math.min(14, (faixa - 10) / 2));
}

/** A barra horizontal das categorias: o valor em % de `maximo` (de 0 a 100); sem máximo, 0. */
export function naEscala(valor: number, maximo: number): number {
  return maximo > 0 ? Math.min(100, Math.max(0, (valor / maximo) * 100)) : 0;
}
