import { expect, test, type Locator, type Page } from '@playwright/test';

import { criarUsuarioConfirmado, ehDesktop, entrarCom, irPeloMenu, semRolagemHorizontal, type UsuarioDaJornada } from './apoio';

// Uma jornada só, em ordem: cada teste continua do anterior. Cada projeto (chromium e
// celular) cria o próprio usuário no beforeAll. Tudo acontece no mês de hoje: a data do
// saldo inicial e a do lançamento são hoje por padrão.
test.describe.configure({ mode: 'serial' });

let usuario: UsuarioDaJornada;

test.beforeAll(async ({ request }) => {
  usuario = await criarUsuarioConfirmado(request, 'lancamentos', 'Lia Lançamentos');
});

async function entrar(page: Page): Promise<void> {
  await entrarCom(page, usuario.email, usuario.senha);
  await expect(page.getByRole('heading', { name: `Olá, ${usuario.nome}`, level: 1 })).toBeVisible();
}

function linha(page: Page, descricao: string): Locator {
  return page.locator('app-linha-do-lancamento').filter({ has: page.locator('.linha__titulo', { hasText: new RegExp(`^${descricao}$`) }) });
}

function linhaDaConta(page: Page, nome: string): Locator {
  return page.locator('li.conta').filter({ has: page.locator('.conta__nome', { hasText: new RegExp(`^${nome}$`) }) });
}

async function novaConta(page: Page, nome: string, tipo: string, saldo: string): Promise<void> {
  await page.getByRole('button', { name: 'Nova conta' }).click();
  const dialogo = page.getByRole('dialog');
  await dialogo.getByLabel('Nome', { exact: true }).fill(nome);
  await dialogo.locator('label.flux-opcao-tipo', { hasText: tipo }).click();
  await dialogo.getByLabel('Saldo inicial', { exact: true }).fill(saldo);
  await dialogo.getByRole('button', { name: 'Criar conta' }).click();
  await expect(dialogo).toBeHidden();
}

/** O diálogo cabe na tela: o painel e as peças largas não rolam para o lado nem passam da borda. */
async function dialogoSemRolagemHorizontal(page: Page): Promise<boolean> {
  return page.evaluate(() => {
    const pecas = Array.from(document.querySelectorAll('.cdk-overlay-pane, .mat-mdc-dialog-surface, .lancamento-dialogo, .flux-segmentado--largo'));
    return pecas.length >= 4 && pecas.every((peca) => {
      const caixa = peca.getBoundingClientRect();
      return peca.scrollWidth <= peca.clientWidth && caixa.left >= 0 && caixa.right <= window.innerWidth;
    });
  });
}

async function escolherTipo(dialogo: Locator, tipo: 'Despesa' | 'Receita' | 'Transferência'): Promise<void> {
  await dialogo.locator('.flux-segmentado label', { hasText: tipo }).click();
}

test('o menu tem Lançamentos, e o mês começa vazio', async ({ page }) => {
  await entrar(page);
  await irPeloMenu(page, 'Contas');
  await novaConta(page, 'Banco Horizonte', 'Conta corrente', '1.000');
  await novaConta(page, 'Poupança', 'Poupança', '0');
  // Sem lançamento no mês, o cartão do mês some, e "Últimos lançamentos" ocupa a linha toda.
  await irPeloMenu(page, 'Início');
  const ultimos = page.locator('.inicio-ultimos');
  await expect(ultimos).toContainText('Nenhum lançamento ainda.');
  await expect(page.locator('.inicio-mes')).toHaveCount(0);
  const larguraDosUltimos = (await ultimos.boundingBox())?.width ?? 0;
  const larguraDaLinha = (await page.locator('.inicio-movimento').boundingBox())?.width ?? -1;
  expect(Math.abs(larguraDosUltimos - larguraDaLinha)).toBeLessThan(1);
  await irPeloMenu(page, 'Lançamentos');
  await expect(page.getByRole('heading', { name: 'Lançamentos', level: 1 })).toBeVisible();
  await expect(page.getByRole('heading', { name: /^Nenhum lançamento em \p{L}+$/u, level: 2 })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Mês anterior' })).toBeVisible();
  expect(await semRolagemHorizontal(page)).toBe(true);
});

test('cria uma despesa e, com "Salvar e criar outro", uma receita', async ({ page }) => {
  await entrar(page);
  await irPeloMenu(page, 'Lançamentos');
  await page.getByRole('button', { name: 'Novo lançamento' }).first().click();
  const dialogo = page.getByRole('dialog');
  await expect(dialogo.getByRole('heading', { name: 'Novo lançamento' })).toBeVisible();
  await dialogo.getByLabel('Valor', { exact: true }).fill('287,43');
  await dialogo.getByLabel('Descrição', { exact: true }).fill('Mercado Bom Preço');
  await dialogo.getByLabel('Conta', { exact: true }).selectOption({ label: 'Banco Horizonte' });
  await dialogo.getByLabel('Categoria (opcional)', { exact: true }).selectOption({ label: '\u2003Mercado' });
  await dialogo.getByRole('button', { name: 'Salvar e criar outro' }).click();
  await expect(page.getByText('Lançamento salvo.')).toBeVisible();
  await expect(dialogo.getByLabel('Valor', { exact: true })).toHaveValue('');

  await escolherTipo(dialogo, 'Receita');
  await dialogo.getByLabel('Valor', { exact: true }).fill('7.200');
  await dialogo.getByLabel('Descrição', { exact: true }).fill('Salário');
  await expect(dialogo.getByLabel('Conta', { exact: true })).toHaveValue(/.+/);
  await dialogo.getByLabel('Categoria (opcional)', { exact: true }).selectOption({ label: 'Salário' });
  await dialogo.getByRole('button', { name: 'Criar lançamento' }).click();
  await expect(dialogo).toBeHidden();

  await expect(page.locator('.linha__titulo')).toHaveText(['Salário', 'Mercado Bom Preço']);
  await expect(linha(page, 'Mercado Bom Preço').locator('.valor__texto')).toHaveText('−R$ 287,43');
  await expect(linha(page, 'Salário').locator('.valor__texto')).toHaveText('+R$ 7.200,00');
  await expect(page.locator('.lancamentos__subtitulo')).toHaveText('Todas as contas · 2 lançamentos');
});

test('a transferência fica fora dos totais, e o filtro de conta vive na URL', async ({ page }) => {
  await entrar(page);
  await irPeloMenu(page, 'Lançamentos');
  await page.getByRole('button', { name: 'Novo lançamento' }).first().click();
  const dialogo = page.getByRole('dialog');
  await escolherTipo(dialogo, 'Transferência');
  await expect(dialogo.getByRole('heading', { name: 'Nova transferência' })).toBeVisible();
  await dialogo.getByLabel('Valor', { exact: true }).fill('1.000');
  await dialogo.getByLabel('De', { exact: true }).selectOption({ label: 'Banco Horizonte' });
  await dialogo.getByLabel('Para', { exact: true }).selectOption({ label: 'Poupança' });
  expect(await semRolagemHorizontal(page)).toBe(true);
  expect(await dialogoSemRolagemHorizontal(page)).toBe(true);
  await dialogo.getByRole('button', { name: 'Criar transferência' }).click();
  await expect(dialogo).toBeHidden();

  const transferencia = linha(page, 'Transferência');
  await expect(transferencia.locator('.valor__texto')).toHaveText('R$ 1.000,00');
  await expect(transferencia.locator('app-valor')).toHaveClass(/flux-valor--neutro/);
  await expect(transferencia.locator('.linha__meta-texto').filter({ visible: true })).toHaveText(
    ehDesktop(page) ? 'Banco Horizonte → Poupança · fora dos totais' : 'Banco Horizonte → Poupança');
  const resultado = ehDesktop(page) ? page.locator('.lancamentos__totais .lancamentos__total').nth(2) : page.locator('.lancamentos__totais-celular .lancamentos__resultado');
  await expect(resultado.locator('.valor__texto')).toHaveText('+R$ 6.912,57');
  expect(await semRolagemHorizontal(page)).toBe(true);

  await page.locator('.lancamentos__filtro select').first().selectOption({ label: 'Poupança' });
  await expect(page).toHaveURL(/[?&]conta=\d+/);
  await expect(page.locator('.linha__titulo')).toHaveText(['Transferência']);
  await expect(transferencia.locator('.linha__meta-texto').filter({ visible: true })).toHaveText(
    ehDesktop(page) ? 'De Banco Horizonte · fora dos totais' : 'De Banco Horizonte');
  await expect(page.locator('.lancamentos__saldo-do-dia .valor__texto')).toHaveText('R$ 1.000,00');
  expect(await semRolagemHorizontal(page)).toBe(true);
  await page.reload();
  await expect(page.locator('.lancamentos__subtitulo')).toContainText('Poupança · 1 transferência');
  await page.getByRole('button', { name: 'Limpar filtros' }).click();
  await expect(page).not.toHaveURL(/conta=/);
  await expect(page.locator('.linha__titulo')).toHaveText(['Transferência', 'Salário', 'Mercado Bom Preço']);
});

test('edita e exclui um lançamento, e o saldo das contas acompanha', async ({ page }) => {
  await entrar(page);
  await irPeloMenu(page, 'Lançamentos');
  await linha(page, 'Mercado Bom Preço').click();
  const dialogo = page.getByRole('dialog');
  await expect(dialogo.getByRole('heading', { name: 'Editar lançamento' })).toBeVisible();
  await dialogo.getByLabel('Valor', { exact: true }).fill('300');
  await dialogo.getByRole('button', { name: 'Salvar' }).click();
  await expect(dialogo).toBeHidden();
  await expect(linha(page, 'Mercado Bom Preço').locator('.valor__texto')).toHaveText('−R$ 300,00');

  await irPeloMenu(page, 'Contas');
  await expect(linhaDaConta(page, 'Banco Horizonte').locator('.conta__valor .valor__texto')).toHaveText('R$ 6.900,00');
  await expect(linhaDaConta(page, 'Poupança').locator('.conta__valor .valor__texto')).toHaveText('R$ 1.000,00');

  await irPeloMenu(page, 'Lançamentos');
  await linha(page, 'Mercado Bom Preço').click();
  await page.getByRole('dialog').getByRole('button', { name: 'Excluir' }).click();
  const confirmacao = page.getByRole('dialog').filter({ hasText: 'Excluir Mercado Bom Preço?' });
  await expect(confirmacao).toContainText('Não dá para desfazer.');
  await confirmacao.getByRole('button', { name: 'Excluir lançamento' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.locator('.linha__titulo')).toHaveText(['Transferência', 'Salário']);
});

test('excluir a conta usada mostra o bloqueio e leva a arquivar; os lançamentos dela ficam só leitura', async ({ page }) => {
  await entrar(page);
  await irPeloMenu(page, 'Contas');
  await linhaDaConta(page, 'Banco Horizonte').getByRole('button', { name: 'Ações de Banco Horizonte' }).click();
  await page.getByRole('menuitem', { name: 'Excluir' }).click();
  const dialogo = page.getByRole('dialog');
  await dialogo.getByRole('button', { name: 'Excluir conta' }).click();
  await expect(dialogo.getByRole('heading', { name: 'Esta conta não pode ser excluída' })).toBeVisible();
  await expect(dialogo).toContainText('Banco Horizonte tem 1 lançamento e 1 transferência. Para tirá-la do saldo total e dos seletores sem perder o histórico, arquive a conta.');
  await dialogo.getByRole('button', { name: 'Arquivar conta' }).click();
  const arquivar = page.getByRole('dialog').filter({ hasText: 'Arquivar Banco Horizonte?' });
  await arquivar.getByRole('button', { name: 'Arquivar conta' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(linhaDaConta(page, 'Banco Horizonte')).toHaveClass(/conta--arquivada/);

  await irPeloMenu(page, 'Lançamentos');
  await linha(page, 'Salário').click();
  const leitura = page.getByRole('dialog');
  await expect(leitura.getByRole('heading', { name: 'Salário' })).toBeVisible();
  await expect(leitura).toContainText('Banco Horizonte está arquivada');
  await expect(leitura.getByRole('button')).toHaveText(['Fechar']);
  await leitura.getByRole('button', { name: 'Fechar' }).click();
  await expect(leitura).toBeHidden();
});

test('o Início mostra o mês e os últimos lançamentos', async ({ page }) => {
  await entrar(page);
  await expect(page.getByRole('heading', { name: 'Últimos lançamentos', level: 2 })).toBeVisible();
  await expect(page.locator('.inicio-ultimos .linha__titulo')).toHaveText(['Transferência', 'Salário']);
  await expect(page.locator('.inicio-mes .inicio-mes__rotulo')).toHaveText(['Entradas · 1', 'Saídas · 0']);
  await expect(page.locator('app-saldo-em-destaque .flux-cartao-destaque__valor .valor__texto')).toHaveText('R$ 1.000,00');
  await page.locator('.inicio-ultimos').getByRole('link', { name: 'Ver todos' }).click();
  await expect(page.getByRole('heading', { name: 'Lançamentos', level: 1 })).toBeVisible();
  if (!ehDesktop(page)) {
    expect(await semRolagemHorizontal(page)).toBe(true);
  }
});
