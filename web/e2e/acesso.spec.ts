import { expect, test } from '@playwright/test';

import { botaoDoUsuario, ehDesktop, email, emailExibido, entrar, entrarNoInicio, nome, senha } from './apoio';

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
  await botaoDoUsuario(page).click();
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

test('mostrar senha alterna a visibilidade sem mudar o rótulo', async ({ page }) => {
  await page.goto('/entrar');
  const campo = page.getByLabel('Senha', { exact: true });
  const alternar = page.getByRole('button', { name: 'Mostrar senha' });
  await campo.fill('senha-de-teste-123');
  await expect(campo).toHaveAttribute('type', 'password');
  await expect(alternar).toHaveAttribute('aria-pressed', 'false');
  await alternar.click();
  await expect(campo).toHaveAttribute('type', 'text');
  await expect(alternar).toHaveAttribute('aria-pressed', 'true');
  await expect(campo).toHaveValue('senha-de-teste-123');
  await alternar.click();
  await expect(campo).toHaveAttribute('type', 'password');
});

test('o nome acessível do botão do usuário é o texto visível', async ({ page }) => {
  await entrarNoInicio(page);
  const botao = botaoDoUsuario(page);
  await expect(botao).toBeVisible();
  await expect(botao).toHaveAccessibleName(ehDesktop(page) ? `${nome} ${emailExibido}` : nome);
  await expect(botao).not.toHaveAttribute('aria-label');
  await expect(botao).toHaveAttribute('aria-haspopup', 'menu');
});
