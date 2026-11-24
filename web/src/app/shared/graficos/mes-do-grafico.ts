/** Textos e valores já prontos para os gráficos SVG; o componente não conhece funcionalidades de negócio. */
export interface MesDoGrafico {
  readonly mes: string;
  readonly rotulo: string;
  readonly ano: string | null;
  readonly titulo: string;
  readonly leitura: string;
}

export interface ColunaDoGrafico extends MesDoGrafico {
  readonly receitas: number;
  readonly despesas: number;
  readonly resultado: number;
}

export interface PontoDoGrafico extends MesDoGrafico {
  readonly saldo: number;
}
