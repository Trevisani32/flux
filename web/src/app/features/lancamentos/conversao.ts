import { TrechoDeTexto } from '../../shared/confirmacao/confirmacao-dialogo';
import { dataCurta } from '../../shared/datas/data-curta';
import { partesDaMoeda } from '../../shared/moeda/moeda';
import { ItemDaLista, LadoDaTransferencia } from './lancamentos.service';

/** A transferência convertida (Fatia 04c): algum lado veio de extrato. A lançada à mão tem os dois manuais. */
export function convertida(item: ItemDaLista): boolean {
  return item.tipo === 'TRANSFERENCIA' && (item.lados ?? []).some((lado) => lado.origem === 'IMPORTADO');
}

/** "2026-12-03" vira "03/12". */
export function diaEMes(data: string): string {
  return dataCurta(data).slice(0, 5);
}

/** Dias de `de` até `ate`, as duas no formato da API, sem Date local (não há fuso para errar). */
function diasEntre(de: string, ate: string): number {
  const [anoDe, mesDe, diaDe] = de.split('-').map(Number);
  const [anoAte, mesAte, diaAte] = ate.split('-').map(Number);
  return Math.round((Date.UTC(anoAte, mesAte - 1, diaAte) - Date.UTC(anoDe, mesDe - 1, diaDe)) / 86_400_000);
}

/** A distância do par até o lançamento (mockup: "03/12, 1 dia depois"): "no mesmo dia", "1 dia depois", "2 dias antes". */
export function distancia(de: string, ate: string): string {
  const dias = diasEntre(de, ate);
  if (dias === 0) {
    return 'no mesmo dia';
  }
  const quantos = Math.abs(dias);
  return `${quantos} ${quantos === 1 ? 'dia' : 'dias'} ${dias > 0 ? 'depois' : 'antes'}`;
}

/** O valor sem sinal, como o Intl escreve ("R$ 1.000,00"), para as frases. */
export function valorSemSinal(valor: number): string {
  return partesDaMoeda(valor)?.absoluto ?? '';
}

/**
 * A observação que o diálogo da convertida mostra (decisão 31): as dos dois lados, sem repetir e uma
 * por linha. Antes de uma edição, cada lado pode ter a sua (decisão 29); salvar grava o texto nos dois,
 * e nada some sem o usuário ver.
 */
export function observacaoDosLados(lados: readonly LadoDaTransferencia[]): string {
  return [...new Set(lados.map((lado) => lado.observacao?.trim() ?? '').filter((observacao) => observacao !== ''))].join('\n');
}

/** "02/12/2026 · importado" ou "03/12/2026 · lançado à mão", o rodapé de cada lado da convertida. */
export function metaDoLado(lado: LadoDaTransferencia): string {
  return `${dataCurta(lado.data)} · ${lado.origem === 'IMPORTADO' ? 'importado' : 'lançado à mão'}`;
}

/**
 * O que o "Desfazer transferência" diz (decisão 32): o tipo e a conta que cada lado volta a ter e o que
 * acontece com a descrição de cada um.
 */
export function paragrafosDoDesfazer(item: ItemDaLista): readonly (readonly TrechoDeTexto[])[] {
  const lados = item.lados ?? [];
  const soImportados = lados.every((lado) => lado.origem === 'IMPORTADO');
  return [
    ['Os dois lançamentos voltam a ser uma ', { forte: `despesa em ${item.contaNome}` }, ' e uma ', { forte: `receita em ${item.contaDestinoNome}` },
      soImportados ? ', sem categoria e com a descrição do extrato.'
        : ', sem categoria. O importado volta com a descrição do extrato, e o lançado à mão fica com a descrição que tem.'],
    ['Nenhum deles é apagado. Se quiser, aplique as regras depois.'],
  ];
}