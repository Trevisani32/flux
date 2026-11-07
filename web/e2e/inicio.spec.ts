import { expect, test } from '@playwright/test';

import { entrarNoInicio } from './apoio';

test('início mostra a saudação, a data, o estado vazio e os cards Em breve', async ({ page }) => {
  await entrarNoInicio(page);
  await expect(page.getByText(/^(Domingo|Segunda-feira|Terça-feira|Quarta-feira|Quinta-feira|Sexta-feira|Sábado), \d{1,2} de (janeiro|fevereiro|março|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro) de \d{4}$/)).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Tudo pronto para começar', level: 2 })).toBeVisible();
  for (const titulo of ['Contas e lançamentos', 'Importação de extratos', 'Cartões de crédito']) {
    await expect(page.getByRole('heading', { name: titulo, level: 3 })).toBeVisible();
  }
  await expect(page.getByText('Em breve', { exact: true })).toHaveCount(3);
});
