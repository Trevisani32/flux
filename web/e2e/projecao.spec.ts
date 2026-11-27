import { expect, test, type APIRequestContext } from '@playwright/test';

import { criarUsuarioConfirmado, ehDesktop, entrarCom, semRolagemHorizontal, type UsuarioDaJornada } from './apoio';

function dataNoFuso(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date());
}

function somarDias(data: string, dias: number): string {
  const [ano, mes, dia] = data.split('-').map(Number);
  return new Date(Date.UTC(ano, mes - 1, dia + dias)).toISOString().slice(0, 10);
}

function primeiroDiaDoMesAnterior(data: string): string {
  const [ano, mes] = data.split('-').map(Number);
  return new Date(Date.UTC(ano, mes - 2, 1)).toISOString().slice(0, 10);
}

function dataCurta(data: string, hoje: string): string {
  const diaEMes = `${data.slice(8, 10)}/${data.slice(5, 7)}`;
  return data.slice(0, 4) === hoje.slice(0, 4) ? diaEMes : `${diaEMes}/${data.slice(0, 4)}`;
}

function dataDaOcorrencia(data: string, hoje: string): string {
  const nomes = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'];
  const [ano, mes, dia] = data.split('-').map(Number);
  return `${nomes[new Date(Date.UTC(ano, mes - 1, dia)).getUTCDay()]}, ${dataCurta(data, hoje)}`;
}

/** A ocorrência mensal seguinte: o mesmo dia no mês seguinte, ou o último dia dele quando o mês é curto. */
function ocorrenciaNoMesSeguinte(data: string): string {
  const [ano, mes, dia] = data.split('-').map(Number);
  const ultimoDia = new Date(Date.UTC(ano, mes + 1, 0)).getUTCDate();
  return new Date(Date.UTC(ano, mes, Math.min(dia, ultimoDia))).toISOString().slice(0, 10);
}

/** O fim do período padrão: o último dia do mês seguinte ao de hoje. */
function fimDoPadrao(hoje: string): string {
  const [ano, mes] = hoje.split('-').map(Number);
  return new Date(Date.UTC(ano, mes + 1, 0)).toISOString().slice(0, 10);
}

/** Como o `naData` da página: "na sexta, 04/12", "no sábado, 05/12". */
function naData(data: string, hoje: string): string {
  const nomes = ['domingo', 'segunda', 'terça', 'quarta', 'quinta', 'sexta', 'sábado'];
  const [ano, mes, dia] = data.split('-').map(Number);
  const semana = new Date(Date.UTC(ano, mes - 1, dia)).getUTCDay();
  return `${semana === 0 || semana === 6 ? 'no' : 'na'} ${nomes[semana]}, ${dataCurta(data, hoje)}`;
}

async function tokenDeAcesso(request: APIRequestContext, usuario: UsuarioDaJornada): Promise<string> {
  const sessao = await request.post('/api/auth/entrar', { data: { email: usuario.email, senha: usuario.senha } });
  expect(sessao.status()).toBe(200);
  return ((await sessao.json()) as { tokenAcesso: string }).tokenAcesso;
}

test('projeção: a conta que fica no negativo, o aviso do Início, o período, as contas e a tabela', async ({ page, request }) => {
  const usuario = await criarUsuarioConfirmado(request, 'e2e-07b', 'Paulo');
  const hoje = dataNoFuso();
  const amanha = somarDias(hoje, 1);
  const tokenAcesso = await tokenDeAcesso(request, usuario);
  const headers = { Authorization: `Bearer ${tokenAcesso}` };
  const conta = await request.post('/api/contas', {
    headers,
    data: {
      nome: 'Banco E2E',
      tipo: 'CORRENTE',
      saldoInicial: 100,
      dataSaldoInicial: primeiroDiaDoMesAnterior(hoje),
      cor: null,
    },
  });
  expect(conta.status()).toBe(201);
  const banco = (await conta.json()) as { id: number };
  const recorrencia = await request.post('/api/recorrencias', {
    headers,
    data: {
      tipo: 'DESPESA',
      descricao: 'Aluguel E2E',
      valor: 1500,
      valorVaria: false,
      categoriaId: null,
      contaId: banco.id,
      cartaoId: null,
      frequencia: 'MENSAL',
      diaDoMes: Number(amanha.slice(8, 10)),
      comecaEm: hoje,
      terminaEm: null,
    },
  });
  expect(recorrencia.status()).toBe(201);

  await entrarCom(page, usuario.email, usuario.senha);
  const dataDoNegativo = dataCurta(amanha, hoje);
  // No último dia do mês, amanhã já é o mês seguinte e a segunda ocorrência cai fora do período padrão.
  const segundaOcorrencia = ocorrenciaNoMesSeguinte(amanha);
  const duasNoPeriodo = segundaOcorrencia <= fimDoPadrao(hoje);
  const valorDoMenorSaldo = duasNoPeriodo ? '−R$\u00a02.900,00' : '−R$\u00a01.400,00';
  const diaDoMenor = duasNoPeriodo ? segundaOcorrencia : amanha;
  const menorSaldoDoInicio = `O menor saldo previsto é ${valorDoMenorSaldo}, em ${dataCurta(diaDoMenor, hoje)}.`;
  const menorSaldoDaProjecao = `O menor saldo é ${valorDoMenorSaldo}, ${naData(diaDoMenor, hoje)}.`;
  const avisoDoInicio = page.locator('app-inicio app-alerta').filter({
    has: page.getByText(`Banco E2E pode ficar no negativo em ${dataDoNegativo}`, { exact: true }),
  });
  await expect(avisoDoInicio.getByText(`Banco E2E pode ficar no negativo em ${dataDoNegativo}`, { exact: true })).toBeVisible();
  await expect(avisoDoInicio.locator('p')).toHaveText(menorSaldoDoInicio);
  const linkDaProjecao = avisoDoInicio.getByRole('link', { name: 'Ver projeção', exact: true });
  await expect(linkDaProjecao).toHaveAttribute('href', '/projecao');
  await linkDaProjecao.click();

  await expect(page).toHaveURL(/\/projecao$/);
  await expect(page.getByRole('heading', { name: 'Projeção', level: 1 })).toBeVisible();
  const alertaDaProjecao = page.locator('app-projecao app-alerta').first();
  await expect(alertaDaProjecao.getByText(`Banco E2E fica no negativo a partir de ${dataDoNegativo}.`, { exact: true }))
    .toBeVisible();
  await expect(alertaDaProjecao.locator('p')).toHaveText(menorSaldoDaProjecao);
  const saldoHoje = page.locator('.app-projecao__numero').filter({ hasText: 'Saldo hoje' });
  await expect(saldoHoje.locator('> span')).toHaveText('Saldo hoje');
  await expect(saldoHoje.locator('.valor__texto')).toHaveText('R$\u00a0100,00');

  const alternarTabela = page.getByRole('button', { name: ehDesktop(page) ? 'Ver tabela' : 'Tabela', exact: true });
  await alternarTabela.click();
  const tabela = page.getByRole('table');
  const linhas = tabela.getByRole('row');
  await expect(linhas.nth(1).getByRole('cell').first()).toHaveText(`Hoje, ${dataCurta(hoje, hoje).slice(0, 5)}`);
  const linhaDaOcorrencia = linhas.filter({ has: page.getByRole('cell', { name: dataDaOcorrencia(amanha, hoje), exact: true }) });
  await expect(linhaDaOcorrencia).toHaveCount(1);
  await expect(linhaDaOcorrencia.getByRole('cell').nth(0)).toHaveText(dataDaOcorrencia(amanha, hoje));
  await expect(linhaDaOcorrencia.locator('.valor__texto').first()).toHaveText('−R$\u00a01.500,00');
  await page.getByRole('button', { name: ehDesktop(page) ? 'Ver gráfico' : 'Gráfico', exact: true }).click();

  await page.getByRole('button', { name: '3 meses', exact: true }).click();
  await expect(page).toHaveURL(/ate=/);
  await page.getByRole('button', { name: 'Banco E2E', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Nenhuma conta escolhida', level: 2 })).toBeVisible();
  await expect(page.getByText('Escolha ao menos uma conta.', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Voltar ao padrão', exact: true }).click();
  await expect(page.getByText(`Banco E2E fica no negativo a partir de ${dataDoNegativo}.`, { exact: true })).toBeVisible();
  expect(await semRolagemHorizontal(page)).toBe(true);
});
