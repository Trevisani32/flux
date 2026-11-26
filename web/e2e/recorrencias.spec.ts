import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test';

import { criarUsuarioConfirmado, ehDesktop, entrarCom, irPeloMenu, semRolagemHorizontal, type UsuarioDaJornada } from './apoio';

test.describe.configure({ mode: 'serial' });

let usuario: UsuarioDaJornada;

function dataNoFuso(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date());
}

function somarDias(data: string, dias: number): string {
  const [ano, mes, dia] = data.split('-').map(Number);
  return new Date(Date.UTC(ano, mes - 1, dia + dias)).toISOString().slice(0, 10);
}

function inicioDoMesAnterior(data: string): string {
  const [ano, mes] = data.split('-').map(Number);
  return new Date(Date.UTC(ano, mes - 2, 1)).toISOString().slice(0, 10);
}

function mesDaData(data: string): string {
  return data.slice(0, 7);
}

function diaEMes(data: string): string {
  return `${data.slice(8, 10)}/${data.slice(5, 7)}`;
}

function diaCurto(data: string): string {
  const nomes = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'];
  const [ano, mes, dia] = data.split('-').map(Number);
  return `${nomes[new Date(Date.UTC(ano, mes - 1, dia)).getUTCDay()]}, ${diaEMes(data)}`;
}

async function criarContaPelaApi(
  request: APIRequestContext,
  conta: { readonly nome: string; readonly dataSaldoInicial: string },
): Promise<void> {
  const sessao = await request.post('/api/auth/entrar', { data: { email: usuario.email, senha: usuario.senha } });
  expect(sessao.status()).toBe(200);
  const { tokenAcesso } = (await sessao.json()) as { tokenAcesso: string };
  const criada = await request.post('/api/contas', {
    headers: { Authorization: `Bearer ${tokenAcesso}` },
    data: { nome: conta.nome, tipo: 'CORRENTE', saldoInicial: 0, dataSaldoInicial: conta.dataSaldoInicial, cor: null },
  });
  expect(criada.status()).toBe(201);
}

async function entrar(page: Page): Promise<void> {
  await entrarCom(page, usuario.email, usuario.senha);
  await expect(page.getByRole('heading', { name: `Olá, ${usuario.nome}`, level: 1 })).toBeVisible();
}

function ocorrencia(page: Page, descricao: string): Locator {
  return page.locator('app-linha-da-ocorrencia').filter({
    has: page.locator('.flux-ocorrencia__titulo', { hasText: new RegExp(`^${descricao}$`) }),
  });
}

async function abrirNovaRecorrencia(page: Page): Promise<Locator> {
  await page.getByRole('button', { name: 'Nova recorrência', exact: true }).first().click();
  const dialogo = page.getByRole('dialog');
  await expect(dialogo.getByRole('heading', { name: 'Nova recorrência', exact: true })).toBeVisible();
  return dialogo;
}

async function preencherRecorrenciaMensal(dialogo: Locator, amanha: string): Promise<void> {
  await dialogo.getByRole('button', { name: 'Despesa', exact: true }).click();
  await dialogo.getByLabel('Descrição', { exact: true }).fill('Aluguel E2E');
  await dialogo.getByLabel('Valor R$', { exact: true }).fill('1.500,00');
  await dialogo.locator('select').nth(0).selectOption({ label: 'Banco E2E' });
  await dialogo.locator('select').nth(1).selectOption({ label: '\u2003Aluguel' });
  await dialogo.getByRole('button', { name: 'Mensal', exact: true }).click();
  await dialogo.getByLabel('Dia do mês', { exact: true }).fill(String(Number(amanha.slice(8, 10))));
  await dialogo.getByLabel('Começa em', { exact: true }).fill(dataNoFuso());
}

test('recorrências: cadastra, confirma, pula e desfaz, e os previstos em Lançamentos', async ({ page, request }) => {
  usuario = await criarUsuarioConfirmado(request, 'e2e-07a', 'Rita');
  const hoje = dataNoFuso();
  const amanha = somarDias(hoje, 1);
  await criarContaPelaApi(request, { nome: 'Banco E2E', dataSaldoInicial: inicioDoMesAnterior(hoje) });

  await entrar(page);
  await irPeloMenu(page, 'Recorrências');
  await expect(page.getByRole('heading', { name: 'Recorrências', level: 1 })).toBeVisible();
  await expect(page.getByText('Nada se repete por aqui ainda', { exact: true })).toBeVisible();

  const dialogoMensal = await abrirNovaRecorrencia(page);
  await preencherRecorrenciaMensal(dialogoMensal, amanha);
  const ocorrenciasDaPrevia = dialogoMensal.locator('.recorrencia-dialogo__previa p:not(.recorrencia-dialogo__dica)');
  await expect(ocorrenciasDaPrevia).toHaveCount(5);
  await expect(ocorrenciasDaPrevia.first()).toHaveText(diaCurto(amanha));
  await dialogoMensal.getByRole('button', { name: 'Salvar recorrência', exact: true }).click();
  await expect(dialogoMensal).toBeHidden();
  await expect(page.getByText('Recorrência criada.', { exact: true })).toBeVisible();

  const aluguel = ocorrencia(page, 'Aluguel E2E');
  await expect(aluguel).toContainText(`amanhã, ${diaEMes(amanha)}`);
  expect(await semRolagemHorizontal(page)).toBe(true);
  if (ehDesktop(page)) {
    await aluguel.getByRole('button', { name: `Confirmar Aluguel E2E de ${diaEMes(amanha)}` }).click();
  } else {
    await aluguel.click();
    await page.getByRole('dialog').getByRole('button', { name: 'Confirmar', exact: true }).click();
  }
  const confirmar = page.getByRole('dialog');
  await expect(confirmar.getByRole('heading', { name: 'Confirmar Aluguel E2E', exact: true })).toBeVisible();
  await confirmar.getByRole('button', { name: 'Confirmar', exact: true }).click();
  await expect(confirmar).toBeHidden();
  await expect(page.getByText(`Aluguel E2E confirmada: lançamento de ${diaEMes(amanha)} criado.`, { exact: true })).toBeVisible();

  const dialogoSemanal = await abrirNovaRecorrencia(page);
  await dialogoSemanal.getByLabel('Descrição', { exact: true }).fill('Diarista E2E');
  await dialogoSemanal.getByLabel('Valor R$', { exact: true }).fill('200,00');
  await dialogoSemanal.locator('select').nth(0).selectOption({ label: 'Banco E2E' });
  await dialogoSemanal.getByRole('button', { name: 'Semanal', exact: true }).click();
  await dialogoSemanal.getByLabel('Começa em', { exact: true }).fill(amanha);
  await dialogoSemanal.getByRole('button', { name: 'Salvar recorrência', exact: true }).click();
  await expect(dialogoSemanal).toBeHidden();

  const diarista = ocorrencia(page, 'Diarista E2E');
  if (ehDesktop(page)) {
    await diarista.getByRole('button', { name: `Pular Diarista E2E de ${diaEMes(amanha)}` }).click();
  } else {
    await diarista.click();
    await page.getByRole('dialog').getByRole('button', { name: 'Pular esta', exact: true }).click();
  }
  await expect(page.getByText(`Ocorrência de ${diaEMes(amanha)} pulada.`, { exact: true })).toBeVisible();
  await expect(ocorrencia(page, 'Diarista E2E')).toHaveCount(0);
  await page.getByRole('button', { name: 'Desfazer', exact: true }).click();
  await expect(ocorrencia(page, 'Diarista E2E')).toBeVisible();

  await irPeloMenu(page, 'Lançamentos');
  if (mesDaData(amanha) !== mesDaData(hoje)) {
    await page.getByRole('button', { name: 'Próximo mês', exact: true }).click();
  }
  await expect(page.locator('.flux-faixa-previstos')).toContainText('Previstos');
  const previsto = ocorrencia(page, 'Diarista E2E');
  await expect(previsto).toContainText('Previsto');
  await expect(previsto).toContainText('Diarista E2E');
  await page.getByRole('button', { name: 'Previstos', exact: true }).click();
  await expect(page).toHaveURL(/previstos=nao/);
  await expect(page.locator('.flux-faixa-previstos')).toHaveCount(0);
});
