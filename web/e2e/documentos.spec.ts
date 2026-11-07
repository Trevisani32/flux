import { expect, test } from '@playwright/test';

import { ehDesktop } from './apoio';

interface ConfiguracaoPublica {
  readonly cadastroPublico: boolean;
  readonly controlador: { readonly nome: string; readonly email: string };
  readonly versaoDosTermos: string;
}

test('termos de uso: versão igual à da API, contato da instância e índice', async ({ page, request }) => {
  const configuracao = (await (await request.get('/api/publico/configuracao')).json()) as ConfiguracaoPublica;
  await page.goto('/termos-de-uso');
  await expect(page.getByRole('heading', { name: 'Termos de uso', level: 1 })).toBeVisible();
  await expect(page.getByText(`Versão ${configuracao.versaoDosTermos}`, { exact: true })).toBeVisible();
  await expect(page.getByText('Versão de 6 de novembro de 2026', { exact: false })).toBeVisible();
  await expect(page.getByRole('heading', { level: 2 })).toHaveCount(10);
  await expect(page.getByText(`Esta instância é mantida por ${configuracao.controlador.nome}`, { exact: false })).toBeVisible();
  await expect(page.getByRole('link', { name: configuracao.controlador.email, exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Entrar', exact: true })).toBeVisible();
  if (ehDesktop(page)) {
    await page.getByRole('navigation', { name: 'Seções dos termos' }).getByRole('link', { name: '10. Contato' }).click();
    await expect(page).toHaveURL(/#t10$/);
    await expect(page.getByRole('heading', { name: '10. Contato', level: 2 })).toBeInViewport();
  }
});

test('política de privacidade: cookie, contato e link para os termos', async ({ page, request }) => {
  const configuracao = (await (await request.get('/api/publico/configuracao')).json()) as ConfiguracaoPublica;
  await page.goto('/privacidade');
  await expect(page.getByRole('heading', { name: 'Política de privacidade', level: 1 })).toBeVisible();
  await expect(page.getByText('flux_renovacao', { exact: true })).toBeVisible();
  await expect(page.getByText(`O controlador dos dados desta instância do Flux é ${configuracao.controlador.nome}`, { exact: false })).toBeVisible();
  await page.getByRole('link', { name: '← Termos de uso' }).click();
  await expect(page.getByRole('heading', { name: 'Termos de uso', level: 1 })).toBeVisible();
});
