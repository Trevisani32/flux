import { diaEMes, nomeDaFatura } from '../cartoes/apresentacao';
import { Cartao } from '../cartoes/cartoes.service';

export interface ProximaFatura {
  readonly cartaoId: number;
  readonly cartaoNome: string;
  readonly cor: Cartao['cor'];
  readonly mes: string;
  readonly aberta: boolean;
  readonly fechamento: string;
  readonly vencimento: string;
  readonly valor: number;
  readonly disponivel: number | null;
}

export function proximasFaturas(ativos: readonly Cartao[]): ProximaFatura[] {
  return ativos
    .flatMap((cartao) => [
      ...(cartao.faturasAPagar ?? []).map((fatura) => ({
        cartaoId: cartao.id,
        cartaoNome: cartao.nome,
        cor: cartao.cor,
        mes: fatura.mes,
        aberta: false,
        fechamento: fatura.fechamento,
        vencimento: fatura.vencimento,
        valor: fatura.pendente,
        disponivel: null,
      })),
      ...(cartao.faturaAberta.lancamentos > 0
        ? [
            {
              cartaoId: cartao.id,
              cartaoNome: cartao.nome,
              cor: cartao.cor,
              mes: cartao.faturaAberta.mes,
              aberta: true,
              fechamento: cartao.faturaAberta.fechamento,
              vencimento: cartao.faturaAberta.vencimento,
              valor: cartao.faturaAberta.total,
              disponivel: cartao.disponivel ?? null,
            },
          ]
        : []),
    ])
    .sort((a, b) => {
      return (
        a.vencimento.localeCompare(b.vencimento) ||
        a.cartaoNome.localeCompare(b.cartaoNome, 'pt-BR', { sensitivity: 'base' }) ||
        a.cartaoId - b.cartaoId
      );
    });
}

export function tituloDaProximaFatura(fatura: ProximaFatura, hoje: string): string {
  const cartao = fatura.cartaoNome.startsWith('Cartão ')
    ? fatura.cartaoNome
    : `Cartão ${fatura.cartaoNome}`;
  return `${cartao} · ${nomeDaFatura(fatura.mes, hoje)}`;
}

export function metaDaProximaFatura(
  fatura: ProximaFatura,
  hoje: string,
): { venceu: string | null; resto: string } {
  if (fatura.aberta) {
    return {
      venceu: null,
      resto: `aberta · fecha ${diaEMes(fatura.fechamento)} · vence ${diaEMes(fatura.vencimento)}`,
    };
  }
  if (fatura.vencimento < hoje) {
    return { venceu: `venceu em ${diaEMes(fatura.vencimento)}`, resto: ' · pendente' };
  }
  return {
    venceu: null,
    resto: `fechada · vence ${diaEMes(fatura.vencimento)} · pendente`,
  };
}
