import { expect, test } from '@playwright/test';

import { entrarNoInicio, nome, semRolagemHorizontal } from './apoio';

test('a gaveta abre pelo botão e fecha com Esc devolvendo o foco', async ({ page }) => {
  await entrarNoInicio(page);
  const abrir = page.getByRole('button', { name: 'Abrir menu' });
  const inicio = page.getByRole('link', { name: 'Início' });
  await expect(abrir).toHaveAttribute('aria-expanded', 'false');
  await expect(inicio).toBeHidden();
  await abrir.click();
  await expect(abrir).toHaveAttribute('aria-expanded', 'true');
  await expect(inicio).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(abrir).toHaveAttribute('aria-expanded', 'false');
  await expect(inicio).toBeHidden();
  await expect(abrir).toBeFocused();
});

test('a gaveta fecha ao tocar fora', async ({ page }) => {
  await entrarNoInicio(page);
  const abrir = page.getByRole('button', { name: 'Abrir menu' });
  await abrir.click();
  await expect(page.getByRole('link', { name: 'Início' })).toBeFocused();
  await page.mouse.click(350, 400);
  await expect(abrir).toHaveAttribute('aria-expanded', 'false');
  await expect(abrir).toBeFocused();
});

test('a gaveta fecha ao navegar', async ({ page }) => {
  await entrarNoInicio(page);
  await page.goto('/minha-conta/senha');
  await expect(page.getByRole('heading', { name: 'Trocar senha' })).toBeVisible();
  const abrir = page.getByRole('button', { name: 'Abrir menu' });
  const inicio = page.getByRole('link', { name: 'Início' });
  await abrir.click();
  await expect(inicio).toBeFocused();
  await inicio.click();
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByRole('heading', { name: `Olá, ${nome}` })).toBeVisible();
  await expect(abrir).toHaveAttribute('aria-expanded', 'false');
  await expect(inicio).toBeHidden();
  await expect(abrir).toBeFocused();
});

test('nenhuma tela tem rolagem horizontal a 375 px', async ({ page }) => {
  await page.goto('/entrar');
  await expect(page.getByRole('heading', { name: 'Entrar' })).toBeVisible();
  expect(await semRolagemHorizontal(page)).toBe(true);
  await entrarNoInicio(page);
  expect(await semRolagemHorizontal(page)).toBe(true);
  const larguraDoConteudo = await page.locator('main').evaluate((elemento) => elemento.getBoundingClientRect().width);
  expect(larguraDoConteudo).toBeGreaterThanOrEqual(343);
  await page.goto('/minha-conta/senha');
  await expect(page.getByRole('heading', { name: 'Trocar senha' })).toBeVisible();
  expect(await semRolagemHorizontal(page)).toBe(true);
});

test('telas públicas e Minha conta sem rolagem horizontal a 375 px', async ({ page }) => {
  for (const [rota, titulo] of [
    ['/criar-conta', 'Criar conta'],
    ['/esqueci-a-senha', 'Esqueci a senha'],
    ['/confira-seu-email', 'Confira seu e-mail'],
    ['/redefinir-senha', 'Este link expirou ou já foi usado'],
    ['/termos-de-uso', 'Termos de uso'],
    ['/privacidade', 'Política de privacidade'],
  ]) {
    await page.goto(rota);
    await expect(page.getByRole('heading', { name: titulo, level: 1 })).toBeVisible();
    expect(await semRolagemHorizontal(page), rota).toBe(true);
  }
  await entrarNoInicio(page);
  await page.goto('/minha-conta');
  await expect(page.getByRole('heading', { name: 'Minha conta', level: 1 })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Excluir minha conta' })).toBeVisible();
  expect(await semRolagemHorizontal(page)).toBe(true);
});
