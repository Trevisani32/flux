import { Ocorrencia } from '../recorrencias/recorrencias.service';
import { DiaDaProjecao, ItemDaProjecao, Projecao } from './projecao.service';

const ocorrenciaDaAgua: Ocorrencia = {
  recorrenciaId: 31,
  periodo: '2026-11-01',
  data: '2026-11-20',
  dataNaLista: null,
  descricao: 'Conta de água Fictícia',
  tipo: 'DESPESA',
  valor: -96.4,
  valorPrevisto: 96.4,
  valorVaria: true,
  contaId: 1,
  contaNome: 'Banco Horizonte',
  cartaoId: null,
  cartaoNome: null,
  cartaoCor: null,
  categoriaId: 11,
  categoriaNome: 'Contas da casa',
  categoriaMaeNome: null,
  atrasada: true,
  visivelAte: '2027-01-19',
};

const ocorrenciaSpotify: Ocorrencia = {
  ...ocorrenciaDaAgua,
  recorrenciaId: 32,
  periodo: '2026-12-01',
  data: '2026-12-02',
  descricao: 'Spotify Fictício',
  valor: -21.9,
  valorPrevisto: 21.9,
  contaId: null,
  contaNome: null,
  cartaoId: 8,
  cartaoNome: 'Cartão Horizonte',
  cartaoCor: 'AZUL',
  atrasada: false,
  visivelAte: null,
};

function previsto(data: string, descricao: string, valor: number, parcial: Partial<ItemDaProjecao> = {}): ItemDaProjecao {
  return {
    tipo: 'PREVISTO',
    data,
    descricao,
    valor,
    valorVaria: false,
    contaId: 1,
    contaNome: 'Banco Horizonte',
    lancamentoId: null,
    outraContaId: null,
    outraContaNome: null,
    cartaoId: null,
    cartaoNome: null,
    cartaoCor: null,
    mes: null,
    vencimento: null,
    pendente: null,
    previsto: null,
    previstos: [],
    ocorrencia: null,
    ...parcial,
  };
}

function fatura(
  data: string,
  cartaoNome: string,
  mes: string,
  valor: number,
  pendente: number,
  previstos: ItemDaProjecao['previstos'] = [],
): ItemDaProjecao {
  return {
    tipo: 'FATURA',
    data,
    descricao: null,
    valor,
    valorVaria: false,
    contaId: 1,
    contaNome: 'Banco Horizonte',
    lancamentoId: null,
    outraContaId: null,
    outraContaNome: null,
    cartaoId: cartaoNome === 'Cartão Lua' ? 7 : 8,
    cartaoNome,
    cartaoCor: cartaoNome === 'Cartão Lua' ? 'ROXO' : 'AZUL',
    mes,
    vencimento: data,
    pendente,
    previsto: previstos.reduce((total, item) => total + item.valor, 0),
    previstos,
    ocorrencia: null,
  };
}

const diasDeBancoHorizonte: readonly DiaDaProjecao[] = ([
  ['2026-11-26', -119.9, 611.75, [previsto('2026-11-26', 'Internet Fictícia', -119.9)]],
  ['2026-11-27', -180, 431.75, [previsto('2026-11-27', 'Diarista Fictícia', -180)]],
  ['2026-11-28', -99.9, 331.85, [previsto('2026-11-28', 'Academia Fictícia', -99.9)]],
  ['2026-11-30', -279.8, 52.05, [previsto('2026-11-30', 'Conta de luz Fictícia', -189.9, { valorVaria: true }), {
    ...previsto('2026-11-30', 'Seguro Fictício', -89.9), tipo: 'LANCAMENTO', valorVaria: false, lancamentoId: 44,
  }]],
  ['2026-12-01', -2270, -2217.95, [previsto('2026-12-01', 'Aluguel Fictício', -1850), previsto('2026-12-01', 'Condomínio Fictício', -420)]],
  ['2026-12-04', -180, -2397.95, [previsto('2026-12-04', 'Diarista Fictícia', -180)]],
  ['2026-12-05', 6915.1, 4517.15, [
    previsto('2026-12-05', 'Salário Fictício', 8200),
    fatura('2026-12-05', 'Cartão Lua', '2026-12', -1284.9, 1284.9),
  ]],
  ['2026-12-06', -500, 4017.15, [{
    ...previsto('2026-12-06', 'Reserva do mês', -500),
    tipo: 'TRANSFERENCIA',
    outraContaId: 4,
    outraContaNome: 'Poupança Horizonte',
    lancamentoId: 45,
  }]],
  ['2026-12-10', -1135.59, 2881.56, [fatura(
    '2026-12-10',
    'Cartão Horizonte',
    '2026-12',
    -1135.59,
    1113.69,
    [{
      recorrenciaId: 32,
      periodo: '2026-12-01',
      data: '2026-12-02',
      descricao: 'Spotify Fictício',
      valor: 21.9,
      ocorrencia: null,
    }],
  )]],
  ['2026-12-11', -180, 2701.56, [previsto('2026-12-11', 'Diarista Fictícia', -180)]],
  ['2026-12-15', -350, 2351.56, [previsto('2026-12-15', 'Curso de inglês Fictício', -350)]],
  ['2026-12-18', -180, 2171.56, [previsto('2026-12-18', 'Diarista Fictícia', -180)]],
  ['2026-12-20', -96.4, 2075.16, [previsto('2026-12-20', 'Conta de água Fictícia', -96.4, { valorVaria: true })]],
  ['2026-12-25', -180, 1895.16, [previsto('2026-12-25', 'Diarista Fictícia', -180)]],
  ['2026-12-26', -119.9, 1775.26, [previsto('2026-12-26', 'Internet Fictícia', -119.9)]],
  ['2026-12-28', -99.9, 1675.36, [previsto('2026-12-28', 'Academia Fictícia', -99.9)]],
  ['2026-12-30', -189.9, 1485.46, [previsto('2026-12-30', 'Conta de luz Fictícia', -189.9)]],
] as readonly (readonly [string, number, number, readonly ItemDaProjecao[]])[])
  .map(([data, movimento, saldo, itens]) => ({ data, movimento, saldo, itens }));

export const projecaoDeBancoHorizonte: Projecao = {
  hoje: '2026-11-25',
  ate: '2026-12-31',
  diasDepoisDeHoje: 36,
  periodos: {
    padrao: '2026-12-31',
    tresMeses: '2027-02-25',
    seisMeses: '2027-05-25',
    dozeMeses: '2027-11-25',
  },
  contas: [
    { id: 2, nome: 'Banco Aurora', tipo: 'CORRENTE', cor: 'LARANJA', padrao: true, escolhida: false },
    { id: 1, nome: 'Banco Horizonte', tipo: 'CORRENTE', cor: 'OURO', padrao: true, escolhida: true },
    { id: 5, nome: 'Carteira', tipo: 'CARTEIRA', cor: 'CINZA', padrao: true, escolhida: false },
    { id: 6, nome: 'Conta conjunta', tipo: 'CORRENTE', cor: 'ROSA', padrao: true, escolhida: false },
    { id: 3, nome: 'Investimentos Lua', tipo: 'INVESTIMENTO', cor: 'ROXO', padrao: false, escolhida: false },
    { id: 4, nome: 'Poupança Horizonte', tipo: 'POUPANCA', cor: 'AZUL', padrao: true, escolhida: false },
  ],
  escolhidas: [1],
  resumo: {
    hoje: 1240.35,
    pendencias: -508.7,
    inicio: 731.65,
    menor: -2397.95,
    diaDoMenor: '2026-12-04',
    fim: 1485.46,
    diasNegativos: 4,
    trechosNegativos: [{ de: '2026-12-01', ate: '2026-12-04' }],
  },
  porConta: [{
    contaId: 1,
    nome: 'Banco Horizonte',
    cor: 'OURO',
    hoje: 1240.35,
    pendencias: -508.7,
    inicio: 731.65,
    menor: -2397.95,
    diaDoMenor: '2026-12-04',
    fim: 1485.46,
    diasNegativos: 4,
    trechosNegativos: [{ de: '2026-12-01', ate: '2026-12-04' }],
  }],
  pendencias: [
    fatura('2026-11-05', 'Cartão Lua', '2026-11', -412.3, 412.3),
    previsto('2026-11-20', 'Conta de água Fictícia', -96.4, { valorVaria: true, ocorrencia: ocorrenciaDaAgua }),
  ],
  dias: diasDeBancoHorizonte,
  faturasFora: [],
};

export const projecaoDoPadrao: Projecao = {
  ...projecaoDeBancoHorizonte,
  escolhidas: [2, 1, 5, 6, 4],
  resumo: {
    hoje: 22102.25,
    pendencias: -508.7,
    inicio: 21593.55,
    menor: 18463.95,
    diaDoMenor: '2026-12-04',
    fim: 22207.36,
    diasNegativos: 0,
    trechosNegativos: [],
  },
  porConta: [
    {
      ...projecaoDeBancoHorizonte.porConta[0], contaId: 2, nome: 'Banco Aurora', cor: 'LARANJA',
      hoje: 4310.2, pendencias: 0, inicio: 4310.2, menor: 3670.2, diaDoMenor: '2026-12-10',
      fim: 3670.2, diasNegativos: 0, trechosNegativos: [],
    },
    projecaoDeBancoHorizonte.porConta[0],
    {
      ...projecaoDeBancoHorizonte.porConta[0], contaId: 5, nome: 'Carteira', cor: 'CINZA',
      hoje: 182.3, pendencias: 0, inicio: 182.3, menor: 182.3, diaDoMenor: '2026-11-25',
      fim: 182.3, diasNegativos: 0, trechosNegativos: [],
    },
    {
      ...projecaoDeBancoHorizonte.porConta[0], contaId: 6, nome: 'Conta conjunta', cor: 'ROSA',
      hoje: 1449, pendencias: 0, inicio: 1449, menor: 1449, diaDoMenor: '2026-11-25',
      fim: 1449, diasNegativos: 0, trechosNegativos: [],
    },
    {
      ...projecaoDeBancoHorizonte.porConta[0], contaId: 4, nome: 'Poupança Horizonte', cor: 'AZUL',
      hoje: 14920.4, pendencias: 0, inicio: 14920.4, menor: 14920.4, diaDoMenor: '2026-11-25',
      fim: 15420.4, diasNegativos: 0, trechosNegativos: [],
    },
  ],
  dias: diasDeBancoHorizonte.filter((dia) => dia.data !== '2026-12-06'),
};

export function projecaoComDias(quantidade: number): Projecao {
  const inicio = new Date('2026-11-26T00:00:00Z');
  const dias = Array.from({ length: quantidade }, (_, indice) => {
    const data = new Date(inicio);
    data.setUTCDate(data.getUTCDate() + indice);
    return {
      data: data.toISOString().slice(0, 10),
      movimento: -1,
      saldo: 730 - indice,
      itens: [previsto(data.toISOString().slice(0, 10), `Previsto ${indice + 1}`, -1)],
    };
  });
  return { ...projecaoDeBancoHorizonte, ate: '2027-11-25', diasDepoisDeHoje: 365, dias };
}

export const revistaAtrasada = fatura('2026-11-10', 'Cartão Horizonte', '2026-11', -15, 0, [{
  recorrenciaId: 41,
  periodo: '2026-11-01',
  data: '2026-11-01',
  descricao: 'Revista Fictícia',
  valor: 15,
  ocorrencia: {
    ...ocorrenciaSpotify,
    recorrenciaId: 41,
    periodo: '2026-11-01',
    data: '2026-11-01',
    descricao: 'Revista Fictícia',
    atrasada: true,
  },
}]);
