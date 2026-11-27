import { ParamMap, Params } from '@angular/router';

import { dataCurta } from '../../shared/datas/data-curta';
import { DiaDoDegrau } from '../../shared/graficos/mes-do-grafico';
import { formatarMoeda } from '../../shared/moeda/moeda';
import { ItemDaProjecao, Projecao } from './projecao.service';

function somarDias(data: string, quantidade: number): string {
  const [ano, mes, dia] = data.split('-').map(Number);
  return new Date(Date.UTC(ano, mes - 1, dia + quantidade)).toISOString().slice(0, 10);
}

function diasEntre(inicio: string, fim: string): number {
  return Math.round((Date.parse(`${fim}T00:00:00Z`) - Date.parse(`${inicio}T00:00:00Z`)) / 86_400_000);
}

function dataPorExtenso(data: string, hoje: string): string {
  const [ano, mes, dia] = data.split('-').map(Number);
  const dias = ['domingo', 'segunda', 'terça', 'quarta', 'quinta', 'sexta', 'sábado'];
  const nome = dias[new Date(Date.UTC(ano, mes - 1, dia)).getUTCDay()];
  const curta = data.slice(0, 4) === hoje.slice(0, 4) ? dataCurta(data).slice(0, 5) : dataCurta(data);
  return `${nome}, ${curta}`;
}

function textoDoItem(item: ItemDaProjecao): string {
  if (item.tipo === 'FATURA') {
    return `Fatura de ${item.cartaoNome}`;
  }
  if (item.tipo === 'TRANSFERENCIA') {
    return `${item.descricao}, ${item.valor < 0 ? 'para' : 'de'} ${item.outraContaNome}`;
  }
  return item.descricao ?? '';
}

function cortar(texto: string): string {
  return texto.length > 26 ? `${texto.slice(0, 25)}…` : texto;
}

function rotuloDoEixo(data: string, indice: number, hoje: string, ate: string): string | null {
  const dias = diasEntre(hoje, ate);
  if (indice === 0) {
    return 'hoje';
  }
  if (data === ate) {
    return dataCurta(data).slice(0, 5);
  }
  if (dias <= 62) {
    return indice % 7 === 0 && dias - indice > 3 ? dataCurta(data).slice(0, 5) : null;
  }
  return data.slice(8) === '01' && dias - indice > 3 ? dataCurta(data).slice(0, 5) : null;
}

/** Dias contínuos para o gráfico, sem calcular dinheiro: a API entrega cada saldo. */
export function diasDoGrafico(projecao: Projecao): readonly DiaDoDegrau[] {
  const porData = new Map(projecao.dias.map((dia) => [dia.data, dia]));
  let saldo = projecao.resumo.inicio;
  return Array.from({ length: diasEntre(projecao.hoje, projecao.ate) + 1 }, (_, indice) => {
    const data = somarDias(projecao.hoje, indice);
    const dia = porData.get(data);
    if (dia !== undefined && indice > 0) {
      saldo = dia.saldo;
    }
    const itens = dia?.itens ?? [];
    const linhas = itens.slice(0, 3).map((item) => ({
      texto: cortar(textoDoItem(item)),
      valor: `${item.valorVaria ? '≈ ' : ''}${item.valor > 0 ? '+' : ''}${formatarMoeda(item.valor)}`,
    }));
    const tituloBase = dataPorExtenso(data, projecao.hoje);
    const titulo = indice === 0
      ? `${tituloBase.replace(/^./, (letra) => letra.toLocaleUpperCase('pt-BR'))} (hoje, com as pendências)`
      : tituloBase.replace(/^./, (letra) => letra.toLocaleUpperCase('pt-BR'));
    const leitura = `${titulo.toLocaleLowerCase('pt-BR')}: saldo ${formatarMoeda(saldo)}`;
    const dica = indice === 0
      ? projecao.resumo.pendencias === 0 ? 'Saldo de hoje' : 'Saldo de hoje menos as pendências'
      : itens.length === 0 ? 'Nenhum movimento neste dia'
        : itens.length > 3 ? `e mais ${itens.length - 3} neste dia` : null;
    return { data, saldo, rotuloDoEixo: rotuloDoEixo(data, indice, projecao.hoje, projecao.ate), leitura, titulo, linhas, dica };
  });
}

export function menorDoGrafico(projecao: Projecao): { readonly data: string; readonly rotulo: string } | null {
  return projecao.resumo.menor < 0
    ? {
      data: projecao.resumo.diaDoMenor,
      rotulo: `${formatarMoeda(projecao.resumo.menor)} · ${dataCurta(projecao.resumo.diaDoMenor).slice(0, 5)}`,
    }
    : null;
}

export function pedidoDaUrl(parametros: ParamMap): { readonly ate: string | null; readonly contas: readonly number[] | null } {
  const ate = parametros.get('ate');
  const contas = parametros.get('contas');
  return {
    ate: ate === null || ate === '' ? null : ate,
    contas: contas === null ? null : contas.split(',').map(Number).filter((id) => Number.isInteger(id) && id > 0),
  };
}

export function parametrosDaUrl(ate: string | null, contas: readonly number[] | null, projecao: Projecao): Params {
  const idsPadrao = projecao.contas.filter((conta) => conta.padrao).map((conta) => conta.id);
  const selecaoPadrao = contas !== null && contas.length === idsPadrao.length && contas.every((id, i) => id === idsPadrao[i]);
  const idsNaOrdem = contas === null ? null : projecao.contas.filter((conta) => contas.includes(conta.id)).map((conta) => conta.id);
  return {
    ate: ate === projecao.periodos.padrao ? null : ate,
    contas: contas === null || selecaoPadrao ? null : idsNaOrdem!.join(','),
  };
}

export function dozeMesesDepois(hoje: string): string {
  const [ano, mes, dia] = hoje.split('-').map(Number);
  const ultimoDia = new Date(Date.UTC(ano + 1, mes, 0)).getUTCDate();
  return `${ano + 1}-${String(mes).padStart(2, '0')}-${String(Math.min(dia, ultimoDia)).padStart(2, '0')}`;
}

export function ateDoMes(mes: string, hoje: string): string | null {
  const ultimo = new Date(Date.UTC(Number(mes.slice(0, 4)), Number(mes.slice(5, 7)), 0)).toISOString().slice(0, 10);
  const ate = ultimo < dozeMesesDepois(hoje) ? ultimo : dozeMesesDepois(hoje);
  const [ano, numeroMes] = hoje.split('-').map(Number);
  const padrao = new Date(Date.UTC(ano, numeroMes + 1, 0)).toISOString().slice(0, 10);
  return ate === padrao ? null : ate;
}
