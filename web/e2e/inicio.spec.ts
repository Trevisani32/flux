import { expect, test } from '@playwright/test';

import { entrarNoInicio } from './apoio';

test('início mostra a saudação, a data, o estado das contas e o card das próximas faturas', async ({ page }) => {
  await entrarNoInicio(page);
  await expect(page.getByText(/^(Domingo|Segunda-feira|Terça-feira|Quarta-feira|Quinta-feira|Sexta-feira|Sábado), \d{1,2} de (janeiro|fevereiro|março|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro) de \d{4}$/)).toBeVisible();
  // O usuário de dev pode ou não ter contas: um dos dois estados aparece (a jornada de
  // financas.spec.ts confere os dois com um usuário novo).
  await expect(page.getByRole('heading', { name: 'Comece pelas suas contas', level: 2 }).or(page.getByText('Saldo total', { exact: true }))).toBeVisible();
  const cartoes = page.locator('app-card.inicio-faturas');
  await expect(cartoes.getByRole('heading', { name: 'Próximas faturas', level: 2 })).toBeVisible();
  await expect(cartoes.getByText('Cadastre um cartão para ver as faturas aqui.', { exact: true }).or(cartoes.getByText('Nenhuma fatura a pagar.', { exact: true })).or(cartoes.locator('li.inicio-fatura')).first())
    .toBeVisible();
  await expect(page.getByText('Em breve', { exact: true })).toHaveCount(0);
});
