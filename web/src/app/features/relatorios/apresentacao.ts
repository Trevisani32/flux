import { Params } from '@angular/router';

import { dataCurta } from '../../shared/datas/data-curta';
import { ColunaDoGrafico, PontoDoGrafico } from '../../shared/graficos/mes-do-grafico';
import { formatarMoeda } from '../../shared/moeda/moeda';
import { faixaDeMeses, mesCurto } from '../cartoes/apresentacao';
import { nomeDoMes, nomeDoMesSemAno } from '../lancamentos/apresentacao';
import {
  CategoriaDoRelatorio,
  Comparacao,
  MesDoRelatorio,
  SaldoDoRelatorio,
  TotalDoRelatorio,
} from './relatorios.service';

const MES = /^\d{4}-(0[1-9]|1[0-2])$/;
const INTEIRO = new Intl.NumberFormat('pt-BR');

export function mesDaUrl(valor: string | null): string | null {
  return valor !== null && MES.test(valor) ? valor : null;
}

export function parametrosDoMes(mes: string, mesAtual: string): Params {
  return { mes: mes === mesAtual ? null : mes };
}

export function textoDaComparacao(comparacao: Comparacao | null): string | null {
  if (comparacao === null) {
    return null;
  }

  if (comparacao.tipo === 'IGUAL') {
    return 'igual à média';
  }

  if (comparacao.tipo === 'NOVO') {
    return 'novo';
  }

  const direcao = comparacao.tipo === 'ACIMA' ? '▲' : '▼';
  const posicao = comparacao.tipo === 'ACIMA' ? 'acima' : 'abaixo';
  return `${direcao} ${INTEIRO.format(comparacao.percentual)}% ${posicao}`;
}

export function leituraDaComparacao(
  comparacao: Comparacao | null,
  natureza: 'receita' | 'despesa',
): string | null {
  if (comparacao === null) {
    return null;
  }

  if (comparacao.tipo === 'NOVO') {
    return natureza === 'despesa'
      ? ' (sem gasto nos meses anteriores)'
      : ' (sem receita nos meses anteriores)';
  }

  if (comparacao.tipo === 'IGUAL') {
    return ' (igual à média)';
  }

  const posicao = comparacao.tipo === 'ACIMA' ? 'acima' : 'abaixo';
  return ` (${INTEIRO.format(comparacao.percentual)}% ${posicao} da média)`;
}

export function corDaComparacao(
  comparacao: Comparacao | null,
  natureza: 'receita' | 'despesa',
): 'ruim' | 'bom' | 'igual' | 'novo' {
  if (comparacao === null || comparacao.tipo === 'IGUAL') {
    return 'igual';
  }

  if (comparacao.tipo === 'NOVO') {
    return 'novo';
  }

  return (natureza === 'despesa') === (comparacao.tipo === 'ACIMA') ? 'ruim' : 'bom';
}

export function notaDaMedia(mes: string, meses: readonly string[], curta: boolean): string {
  if (meses.length === 0) {
    return 'Ainda não há meses anteriores para comparar.';
  }

  if (meses.length === 1) {
    const anoDiferente = meses[0].slice(0, 4) !== mes.slice(0, 4);
    const faixa = faixaDeMeses(meses[0], meses[0], anoDiferente);
    return `Comparado com ${faixa}, o único mês anterior com dados`;
  }

  const primeiroMes = meses[0];
  const ultimoMes = meses[meses.length - 1];
  const anosDiferentes = meses.some((item) => item.slice(0, 4) !== mes.slice(0, 4));
  const faixa = faixaDeMeses(primeiroMes, ultimoMes, anosDiferentes);

  return curta
    ? `Comparado com a média de ${faixa}`
    : `${nomeDoMesSemAno(mes)} comparado com a média de ${faixa}`;
}

export function legendaDaMedia(quantos: number, curta: boolean): string | null {
  if (quantos === 0) {
    return null;
  }

  if (quantos === 1) {
    return 'Mês anterior';
  }

  return curta ? `Média dos ${quantos} meses` : `Média dos ${quantos} meses anteriores`;
}

export function textoDasReceitas(total: TotalDoRelatorio): string {
  const quantidade =
    total.quantidade === 0
      ? 'Nenhuma receita'
      : `${total.quantidade} ${total.quantidade === 1 ? 'receita' : 'receitas'}`;

  return total.media === null
    ? quantidade
    : `${quantidade} · média ${formatarMoeda(total.media)}`;
}

export function textoDasDespesas(total: TotalDoRelatorio): string {
  const quantidade = quantidadeDeDespesas(total.quantidade);
  const noCartao = total.noCartao === 0 ? '' : `, ${total.noCartao} no cartão`;

  return total.media === null
    ? `${quantidade}${noCartao}`
    : `${quantidade}${noCartao} · média ${formatarMoeda(total.media)}`;
}

export function textoDaMediaDaLinha(
  categoria: CategoriaDoRelatorio,
  quantosNaMedia: number,
): string | null {
  if (quantosNaMedia === 0 || categoria.media === null) {
    return null;
  }

  return categoria.media > 0 ? `média ${formatarMoeda(categoria.media)}` : 'sem gasto antes';
}

export function rotuloDoSaldo(saldo: SaldoDoRelatorio): string {
  return saldo.hoje ? 'Saldo total · hoje' : `Saldo total em ${dataCurta(saldo.data)}`;
}

export function quantidadeDeContas(quantidade: number): string {
  if (quantidade === 0) {
    return 'Nenhuma conta nesta data';
  }

  return quantidade === 1 ? '1 conta' : `${quantidade} contas`;
}

export function quantidadeDeDespesas(quantidade: number): string {
  if (quantidade === 0) {
    return 'Nenhuma despesa';
  }

  return `${quantidade} ${quantidade === 1 ? 'despesa' : 'despesas'}`;
}

export function nadaGasto(mes: string): string {
  return `Nada gasto em ${nomeDoMes(mes)}.`;
}

export function faixaDosMeses(meses: readonly { mes: string }[], curta: boolean): string {
  if (meses.length === 0) {
    return '';
  }

  const primeiroMes = meses[0].mes;
  const ultimoMes = meses[meses.length - 1].mes;

  return curta
    ? `${mesCurto(primeiroMes)} a ${mesCurto(ultimoMes)}`
    : faixaDeMeses(primeiroMes, ultimoMes, true);
}

function anoDoItem(mes: string, indice: number): string | null {
  return indice === 0 || mes.slice(5) === '-01' ? mes.slice(0, 4) : null;
}

function resultadoFormatado(valor: number): string {
  return valor > 0 ? `+${formatarMoeda(valor)}` : formatarMoeda(valor);
}

export function colunasDoGrafico(meses: readonly MesDoRelatorio[]): ColunaDoGrafico[] {
  return meses.map((mes, indice) => {
    const titulo = nomeDoMes(mes.mes).replace(/^./, (letra) => letra.toLocaleUpperCase('pt-BR'));
    const leitura = [
      `${titulo}: receitas ${formatarMoeda(mes.receitas)}`,
      `despesas ${formatarMoeda(mes.despesas)}`,
      `resultado ${resultadoFormatado(mes.resultado)}`,
    ].join(', ');

    return {
      ...mes,
      rotulo: mesCurto(mes.mes).slice(0, 3),
      ano: anoDoItem(mes.mes, indice),
      titulo,
      leitura,
    };
  });
}

export function pontosDoSaldo(meses: readonly MesDoRelatorio[], hoje: string): PontoDoGrafico[] {
  return meses
    .filter((mes) => mes.saldo !== null)
    .map((mes, indice) => {
      const titulo =
        mes.dataDoSaldo === hoje ? `Hoje, ${dataCurta(hoje)}` : `Fim de ${nomeDoMes(mes.mes)}`;

      return {
        mes: mes.mes,
        saldo: mes.saldo!,
        rotulo: mesCurto(mes.mes).slice(0, 3),
        ano: anoDoItem(mes.mes, indice),
        titulo,
        leitura: `${titulo}: saldo total ${formatarMoeda(mes.saldo)}`,
      };
    });
}

export function linkDaCategoria(mes: string, categoria: CategoriaDoRelatorio): Params | null {
  if (categoria.direto) {
    return null;
  }

  return categoria.id === null
    ? { mes, categoria: 'sem', tipo: 'despesa' }
    : { mes, categoria: categoria.id };
}
