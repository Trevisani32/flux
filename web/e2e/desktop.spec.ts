import { expect, test } from '@playwright/test';

import { entrarNoInicio, nomeDoBotaoDoUsuario } from './apoio';

test('o painel de marca ocupa toda a altura da tela de entrada', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto('/entrar');
  const painel = page.locator('app-painel-marca');
  await expect(painel).toBeVisible();

  const alturas = await page.locator('main.publico').evaluate((entrar) => ({
    entrar: entrar.getBoundingClientRect().height,
    painel: entrar.querySelector('app-painel-marca')?.getBoundingClientRect().height,
  }));

  expect(alturas.painel).toBe(alturas.entrar);
  expect(alturas.entrar).toBe(page.viewportSize()?.height);
});

test('a lateral fica fixa com Início ativo e sem botão de menu', async ({ page }) => {
  await entrarNoInicio(page);
  const navegacao = page.getByRole('navigation', { name: 'Menu principal' });
  await expect(navegacao).toBeVisible();
  await expect(navegacao.getByRole('link', { name: 'Início' })).toHaveAttribute('aria-current', 'page');
  await expect(navegacao.getByRole('list', { name: 'Visão geral' }).getByRole('link')).toHaveText(['Início', 'Lançamentos', 'Importação', 'Contas', 'Cartões']);
  await expect(navegacao.getByRole('list', { name: 'Organização' }).getByRole('link')).toHaveText(['Categorias', 'Regras']);
  await navegacao.getByRole('link', { name: 'Contas' }).click();
  await expect(page.getByRole('heading', { name: 'Contas', level: 1 })).toBeVisible();
  await expect(navegacao.getByRole('link', { name: 'Contas' })).toHaveAttribute('aria-current', 'page');
  await expect(navegacao.getByRole('link', { name: 'Início' })).not.toHaveAttribute('aria-current', 'page');
  await navegacao.getByRole('link', { name: 'Início' }).click();
  await expect(page.getByRole('button', { name: 'Abrir menu' })).toHaveCount(0);
  const largura = await page.locator('#menu-principal').evaluate((elemento) => elemento.getBoundingClientRect().width);
  expect(largura).toBe(252);
  await expect(page.locator('#menu-principal').getByRole('button', { name: nomeDoBotaoDoUsuario })).toBeVisible();
});

test('a página usa a fonte Manrope sem violar a CSP', async ({ page }) => {
  const violacoes: string[] = [];
  page.on('console', (mensagem) => {
    if (mensagem.type() === 'error' && /Content Security Policy/i.test(mensagem.text())) {
      violacoes.push(mensagem.text());
    }
  });
  await entrarNoInicio(page);
  const fonte = await page.evaluate(async () => {
    await document.fonts.ready;
    return {
      familia: getComputedStyle(document.body).fontFamily,
      carregada: Array.from(document.fonts).some((face) => face.family.replace(/["']/g, '') === 'Manrope' && face.status === 'loaded'),
    };
  });
  expect(fonte.familia).toMatch(/^"?Manrope"?,/);
  expect(fonte.carregada).toBe(true);
  expect(violacoes).toEqual([]);
});
