import { expect, test } from '@playwright/test';

const email = process.env['E2E_EMAIL'] ?? 'dev@flux.local';
const senha = process.env['E2E_SENHA'] ?? 'senha-de-dev-123';
const nome = process.env['E2E_NOME'] ?? 'Dev';

async function entrar(page: import('@playwright/test').Page): Promise<void> {
  await page.goto('/entrar');
  await page.getByLabel('E-mail', { exact: true }).fill(email);
  await page.getByLabel('Senha', { exact: true }).fill(senha);
  await page.getByRole('button', { name: 'Entrar' }).click();
}

test('login certo mostra o início', async ({ page }) => {
  await entrar(page);
  await expect(page.getByRole('heading', { name: `Olá, ${nome}` })).toBeVisible();
});

test('senha errada mostra a mensagem de credenciais inválidas', async ({ page }) => {
  await page.goto('/entrar');
  await page.getByLabel('E-mail', { exact: true }).fill(email);
  await page.getByLabel('Senha', { exact: true }).fill('senha-errada');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('alert')).toHaveText('E-mail ou senha inválidos');
});

test('recarregar mantém a sessão', async ({ page }) => {
  await entrar(page);
  await expect.poll(async () => (await page.context().cookies()).some((cookie) => cookie.name === 'flux_renovacao'))
    .toBe(true);
  await page.reload();
  await expect(page.getByRole('heading', { name: `Olá, ${nome}` })).toBeVisible();
});

test('tokens não ficam no armazenamento do navegador', async ({ page }) => {
  await entrar(page);
  await expect.poll(() => page.evaluate(() => ({ local: localStorage.length, session: sessionStorage.length })))
    .toEqual({ local: 0, session: 0 });
});

test('sair encerra a sessão mesmo depois de recarregar', async ({ page }) => {
  await entrar(page);
  await page.getByRole('button', { name: 'Menu do usuário' }).click();
  await page.getByRole('menuitem', { name: 'Sair' }).click();
  await expect(page).toHaveURL(/\/entrar$/);
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Entrar' })).toBeVisible();
});

test('rota protegida preserva o retorno após entrar', async ({ page }) => {
  await page.goto('/minha-conta/senha');
  await expect(page).toHaveURL(/\/entrar\?retorno=/);
  await page.getByLabel('E-mail', { exact: true }).fill(email);
  await page.getByLabel('Senha', { exact: true }).fill(senha);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Trocar senha' })).toBeVisible();
});
