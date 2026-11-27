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

/** Valores e textos prontos para o gráfico diário, que não conhece a projeção. */
export interface DiaDoDegrau {
  readonly data: string;
  readonly saldo: number;
  readonly rotuloDoEixo: string | null;
  readonly leitura: string;
  readonly titulo: string;
  readonly linhas: readonly { readonly texto: string; readonly valor: string }[];
  readonly dica: string | null;
}
