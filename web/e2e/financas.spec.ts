import { expect, test, type Locator, type Page } from '@playwright/test';

import { criarUsuarioConfirmado, ehDesktop, entrarCom, irPeloMenu, semRolagemHorizontal, type UsuarioDaJornada } from './apoio';

// Uma jornada só, em ordem: cada teste continua do anterior. Cada projeto (chromium e
// celular) cria o próprio usuário no beforeAll, sem contas e com as categorias padrão.
test.describe.configure({ mode: 'serial' });

let usuario: UsuarioDaJornada;

test.beforeAll(async ({ request }) => {
  usuario = await criarUsuarioConfirmado(request, 'financas', 'Fulana Finanças');
});

async function entrar(page: Page): Promise<void> {
  await entrarCom(page, usuario.email, usuario.senha);
  await expect(page.getByRole('heading', { name: `Olá, ${usuario.nome}`, level: 1 })).toBeVisible();
}

function linhaDaConta(page: Page, nome: string): Locator {
  return page.locator('li.conta').filter({ has: page.locator('.conta__nome', { hasText: new RegExp(`^${nome}$`) }) });
}

function linhaDaCategoria(page: Page, nome: string): Locator {
  return page.locator('.arvore__linha, .arquivada__linha').filter({ has: page.getByText(nome, { exact: true }) }).first();
}

async function acaoDoMenu(linha: Locator, page: Page, acao: string): Promise<void> {
  await linha.getByRole('button', { name: /^(Ações|Mais ações) de / }).click();
  await page.getByRole('menuitem', { name: acao }).click();
}

async function novaConta(page: Page, nome: string, tipo: string, saldo: string): Promise<void> {
  await page.getByRole('button', { name: 'Nova conta' }).click();
  const dialogo = page.getByRole('dialog');
  await expect(dialogo.getByRole('heading', { name: 'Nova conta' })).toBeVisible();
  await dialogo.getByLabel('Nome', { exact: true }).fill(nome);
  await dialogo.locator('label.flux-opcao-tipo', { hasText: tipo }).click();
  await dialogo.getByLabel('Saldo inicial', { exact: true }).fill(saldo);
  await dialogo.getByRole('button', { name: 'Criar conta' }).click();
  await expect(dialogo).toBeHidden();
}

async function saldoTotal(page: Page): Promise<Locator> {
  return page.locator('app-saldo-em-destaque .flux-cartao-destaque__valor .valor__texto');
}

/** A Manrope 800 carregou: a largura do texto depende dela. */
async function manropeCarregada(page: Page): Promise<boolean> {
  return page.evaluate(async () => {
    await document.fonts.ready;
    let carregada = false;
    document.fonts.forEach((fonte) => {
      if (fonte.family.replace(/["']/g, '') === 'Manrope' && fonte.weight === '800' && fonte.status === 'loaded') {
        carregada = true;
      }
    });
    return carregada;
  });
}

/** Nenhum dos elementos transborda a própria largura: o texto quebra em vez de vazar. */
async function cabem(elementos: Locator): Promise<boolean> {
  return elementos.evaluateAll((lista) => lista.length > 0 && lista.every((elemento) => elemento.scrollWidth <= elemento.clientWidth));
}

test('início sem contas e as categorias padrão do usuário novo', async ({ page }) => {
  await entrar(page);
  await expect(page.getByRole('heading', { name: 'Comece pelas suas contas', level: 2 })).toBeVisible();
  await irPeloMenu(page, 'Categorias');
  await expect(page.getByRole('heading', { name: 'Categorias', level: 1 })).toBeVisible();
  const abas = page.getByRole('navigation', { name: 'Natureza' });
  await expect(abas.getByRole('link', { name: /^Despesas/ })).toHaveText(/Despesas\s*19/);
  await expect(abas.getByRole('link', { name: /^Receitas/ })).toHaveText(/Receitas\s*5/);
  for (const nome of ['Alimentação', 'Assinaturas', 'Compras', 'Educação', 'Impostos e taxas', 'Lazer', 'Moradia', 'Outras despesas', 'Saúde', 'Transporte']) {
    await expect(page.locator('.arvore__nome', { hasText: new RegExp(`^${nome}$`) })).toBeVisible();
  }
  await expect(page.getByRole('list', { name: 'Subcategorias de Alimentação' }).getByRole('listitem')).toHaveText(['Delivery', 'Mercado', 'Restaurante']);
  await abas.getByRole('link', { name: /^Receitas/ }).click();
  await expect(page).toHaveURL(/\/categorias\?natureza=receita$/);
  await expect(page.locator('.arvore__nome')).toHaveText(['Freelance', 'Outras receitas', 'Reembolsos', 'Rendimentos', 'Salário']);
  expect(await semRolagemHorizontal(page)).toBe(true);
});

test('cria contas pelo Início e por Contas, com o saldo total e os erros', async ({ page }) => {
  await entrar(page);
  await page.getByRole('link', { name: 'Cadastrar conta' }).click();
  const dialogo = page.getByRole('dialog');
  await expect(dialogo.getByRole('heading', { name: 'Nova conta' })).toBeVisible();
  await expect(page).toHaveURL(/\/contas$/);
  await dialogo.getByLabel('Nome', { exact: true }).fill('Banco Horizonte');
  await dialogo.getByLabel('Saldo inicial', { exact: true }).fill('4218,4');
  await dialogo.getByLabel('Saldo inicial', { exact: true }).blur();
  await expect(dialogo.getByLabel('Saldo inicial', { exact: true })).toHaveValue('4.218,40');
  await dialogo.getByRole('button', { name: 'Criar conta' }).click();
  await expect(dialogo).toBeHidden();
  await expect(linhaDaConta(page, 'Banco Horizonte')).toContainText('R$ 4.218,40');

  await novaConta(page, 'Conta conjunta', 'Conta corrente', '-120');
  await expect(linhaDaConta(page, 'Conta conjunta').locator('app-valor .valor__texto')).toHaveText('−R$ 120,00');
  await expect(linhaDaConta(page, 'Conta conjunta').locator('app-valor')).toHaveClass(/flux-valor--negativo/);
  await novaConta(page, 'Reserva de emergência', 'Investimento', '15.000');
  await expect(page.locator('li.conta .conta__nome')).toHaveText(['Banco Horizonte', 'Conta conjunta', 'Reserva de emergência']);
  await expect(await saldoTotal(page)).toHaveText('R$ 19.098,40');
  await expect(page.locator('.contas-resumo__valor')).toHaveText(['R$ 4.098,40', 'R$ 15.000,00']);
  await expect(page.getByText('3 contas ativas')).toBeVisible();

  await page.getByRole('button', { name: 'Nova conta' }).click();
  await dialogo.getByLabel('Nome', { exact: true }).fill('banco horizonte');
  await dialogo.getByLabel('Saldo inicial', { exact: true }).fill('10,005');
  await dialogo.getByLabel('Saldo inicial', { exact: true }).blur();
  await expect(dialogo.getByText('Use no máximo 2 casas decimais.')).toBeVisible();
  await dialogo.getByLabel('Saldo inicial', { exact: true }).fill('0');
  await dialogo.getByRole('button', { name: 'Criar conta' }).click();
  await expect(dialogo.getByText('Já existe uma conta ativa com este nome.')).toBeVisible();
  await dialogo.getByRole('button', { name: 'Cancelar' }).click();
  await expect(dialogo).toBeHidden();
  await expect(page.locator('li.conta')).toHaveCount(3);
  expect(await semRolagemHorizontal(page)).toBe(true);
});

test('edita, arquiva com aviso de saldo, reativa, desfaz e exclui', async ({ page }) => {
  await entrar(page);
  await irPeloMenu(page, 'Contas');
  await expect(page.getByRole('heading', { name: 'Contas', level: 1 })).toBeVisible();

  await acaoDoMenu(linhaDaConta(page, 'Conta conjunta'), page, 'Editar');
  const dialogo = page.getByRole('dialog');
  await expect(dialogo.getByRole('heading', { name: 'Editar conta' })).toBeVisible();
  await expect(dialogo.getByLabel('Saldo inicial', { exact: true })).toHaveValue('−120,00');
  await dialogo.getByLabel('Nome', { exact: true }).fill('Conta da casa');
  await dialogo.getByRole('button', { name: 'Salvar' }).click();
  await expect(dialogo).toBeHidden();
  await expect(linhaDaConta(page, 'Conta da casa')).toBeVisible();

  await acaoDoMenu(linhaDaConta(page, 'Banco Horizonte'), page, 'Arquivar');
  await expect(dialogo.getByRole('heading', { name: 'Arquivar Banco Horizonte?' })).toBeVisible();
  await expect(dialogo.getByRole('status')).toContainText('O saldo desta conta é R$ 4.218,40');
  await dialogo.getByRole('button', { name: 'Arquivar conta' }).click();
  await expect(dialogo).toBeHidden();
  await expect(await saldoTotal(page)).toHaveText('R$ 14.880,00');
  const arquivadas = page.getByRole('button', { name: /^Arquivadas/ });
  await expect(arquivadas).toHaveAttribute('aria-expanded', 'false');
  await arquivadas.click();
  await expect(linhaDaConta(page, 'Banco Horizonte')).toContainText('arquivada em');
  await linhaDaConta(page, 'Banco Horizonte').getByRole('button', { name: 'Reativar' }).click();
  await expect(await saldoTotal(page)).toHaveText('R$ 19.098,40');
  await expect(page.getByRole('button', { name: /^Arquivadas/ })).toHaveCount(0);

  await novaConta(page, 'Zerada', 'Carteira', '0');
  await acaoDoMenu(linhaDaConta(page, 'Zerada'), page, 'Arquivar');
  await expect(dialogo).toHaveCount(0);
  await expect(page.getByText('Zerada arquivada.')).toBeVisible();
  await page.getByRole('button', { name: 'Desfazer' }).click();
  await expect(linhaDaConta(page, 'Zerada').getByRole('button', { name: 'Reativar' })).toHaveCount(0);
  await expect(page.locator('li.conta:not(.conta--arquivada)')).toHaveCount(4);

  await acaoDoMenu(linhaDaConta(page, 'Zerada'), page, 'Excluir');
  await expect(dialogo.getByRole('heading', { name: 'Excluir Zerada?' })).toBeVisible();
  await dialogo.getByRole('button', { name: 'Excluir conta' }).click();
  await expect(dialogo).toBeHidden();
  await expect(linhaDaConta(page, 'Zerada')).toHaveCount(0);
  await expect(page.locator('li.conta')).toHaveCount(3);
});

test('categorias: cria, edita com nome em uso, arquiva e exclui a principal', async ({ page }) => {
  await entrar(page);
  await irPeloMenu(page, 'Categorias');
  const dialogo = page.getByRole('dialog');

  await page.getByRole('button', { name: 'Nova categoria' }).click();
  await expect(dialogo.getByRole('heading', { name: 'Nova categoria' })).toBeVisible();
  await dialogo.getByLabel('Nome', { exact: true }).fill('Pets');
  await dialogo.getByRole('button', { name: 'Criar categoria' }).click();
  await expect(dialogo).toBeHidden();
  await expect(page.locator('.arvore__nome', { hasText: /^Pets$/ })).toBeVisible();

  await acaoDoMenu(linhaDaCategoria(page, 'Alimentação'), page, 'Nova subcategoria');
  await expect(dialogo.getByLabel('Dentro de', { exact: true })).toHaveValue(/.+/);
  await expect(dialogo.getByLabel('Dentro de', { exact: true }).locator('option:checked')).toHaveText('Alimentação');
  await dialogo.getByLabel('Nome', { exact: true }).fill('Padaria');
  await dialogo.getByRole('button', { name: 'Criar categoria' }).click();
  await expect(dialogo).toBeHidden();
  await expect(page.getByRole('list', { name: 'Subcategorias de Alimentação' }).getByRole('listitem'))
    .toHaveText(['Delivery', 'Mercado', 'Padaria', 'Restaurante']);

  await acaoDoMenu(linhaDaCategoria(page, 'Restaurante'), page, 'Editar');
  await expect(dialogo.getByRole('heading', { name: 'Editar categoria' })).toBeVisible();
  await dialogo.getByLabel('Nome', { exact: true }).fill('mercado');
  await dialogo.getByRole('button', { name: 'Salvar' }).click();
  await expect(dialogo.getByText('Já existe uma categoria ativa com este nome em Alimentação.')).toBeVisible();
  await dialogo.getByRole('button', { name: 'Cancelar' }).click();
  await expect(dialogo).toBeHidden();

  await acaoDoMenu(linhaDaCategoria(page, 'Transporte'), page, 'Arquivar');
  await expect(dialogo.getByRole('heading', { name: 'Arquivar Transporte?' })).toBeVisible();
  await expect(dialogo).toContainText('As 3 subcategorias ativas também serão arquivadas: App de transporte, Combustível e Transporte público.');
  await expect(dialogo).toContainText('Reativar Transporte traz as quatro de volta.');
  await dialogo.getByRole('button', { name: 'Arquivar', exact: true }).click();
  await expect(dialogo).toBeHidden();
  await expect(page.locator('.arvore__nome', { hasText: /^Transporte$/ })).toHaveCount(0);
  await page.getByRole('button', { name: /^Arquivadas/ }).click();
  await expect(page.getByRole('list', { name: 'Subcategorias arquivadas de Transporte' }).getByRole('listitem'))
    .toHaveText(['App de transporte', 'Combustível', 'Transporte público']);
  await linhaDaCategoria(page, 'Transporte').getByRole('button', { name: 'Reativar' }).click();
  await expect(page.getByRole('list', { name: 'Subcategorias de Transporte' }).getByRole('listitem'))
    .toHaveText(['App de transporte', 'Combustível', 'Transporte público']);

  await acaoDoMenu(linhaDaCategoria(page, 'Moradia'), page, 'Excluir');
  await expect(dialogo.getByRole('heading', { name: 'Excluir Moradia?' })).toBeVisible();
  await expect(dialogo).toContainText('As 3 subcategorias também serão excluídas: Aluguel, Condomínio e Contas da casa.');
  await dialogo.getByRole('button', { name: 'Excluir categoria' }).click();
  await expect(dialogo).toBeHidden();
  await expect(page.locator('.arvore__nome', { hasText: /^Moradia$/ })).toHaveCount(0);
  await expect(page.getByRole('navigation', { name: 'Natureza' }).getByRole('link', { name: /^Despesas/ })).toHaveText(/Despesas\s*17/);
  expect(await semRolagemHorizontal(page)).toBe(true);
});

test('início com contas mostra o saldo total e a lista', async ({ page }) => {
  await entrar(page);
  await expect(await saldoTotal(page)).toHaveText('R$ 19.098,40');
  await expect(page.getByText('3 contas ativas')).toBeVisible();
  await expect(page.getByText('Investimentos: R$ 15.000,00')).toBeVisible();
  await expect(page.locator('.inicio-conta__nome')).toHaveText(['Banco Horizonte', 'Conta da casa', 'Reserva de emergência']);
  await page.getByRole('link', { name: 'Ver todas' }).click();
  await expect(page.getByRole('heading', { name: 'Contas', level: 1 })).toBeVisible();
  if (!ehDesktop(page)) {
    expect(await semRolagemHorizontal(page)).toBe(true);
  }
});

test('nome de 60 caracteres sem espaço cabe na tela, e o saldo total negativo fica vermelho', async ({ page }) => {
  const nomeLongo = 'Nomebemcompridosemnenhumespaco'.repeat(2);
  await entrar(page);
  await irPeloMenu(page, 'Contas');
  await expect(page.getByRole('heading', { name: 'Contas', level: 1 })).toBeVisible();
  await novaConta(page, nomeLongo, 'Conta corrente', '-20.000');
  const total = page.locator('app-saldo-em-destaque .flux-cartao-destaque__valor app-valor');
  await expect(total.locator('.valor__texto')).toHaveText('−R$ 901,60');
  await expect(total).toHaveClass(/flux-valor--negativo/);
  await expect(total).toHaveCSS('color', 'rgb(69, 10, 10)');
  expect(await semRolagemHorizontal(page)).toBe(true);
  await acaoDoMenu(linhaDaConta(page, nomeLongo), page, 'Excluir');
  const dialogo = page.getByRole('dialog');
  await expect(dialogo.getByRole('heading', { name: `Excluir ${nomeLongo}?` })).toBeVisible();
  expect(await cabem(dialogo.locator('.confirmacao__cabecalho, .confirmacao__texto'))).toBe(true);
  await dialogo.getByRole('button', { name: 'Excluir conta' }).click();
  await expect(dialogo).toBeHidden();

  await irPeloMenu(page, 'Categorias');
  await page.getByRole('button', { name: 'Nova categoria' }).click();
  await dialogo.getByLabel('Nome', { exact: true }).fill(nomeLongo);
  await dialogo.getByRole('button', { name: 'Criar categoria' }).click();
  await expect(dialogo).toBeHidden();
  await acaoDoMenu(linhaDaCategoria(page, nomeLongo), page, 'Nova subcategoria');
  await expect(dialogo.getByLabel('Dentro de', { exact: true }).locator('option:checked')).toHaveText(nomeLongo);
  expect(await cabem(dialogo.locator('form'))).toBe(true);
  await dialogo.getByLabel('Nome', { exact: true }).fill(nomeLongo);
  await dialogo.getByRole('button', { name: 'Criar categoria' }).click();
  await expect(dialogo).toBeHidden();
  await expect(page.getByRole('list', { name: `Subcategorias de ${nomeLongo}` }).getByRole('listitem')).toHaveText([nomeLongo]);
  expect(await semRolagemHorizontal(page)).toBe(true);
  await acaoDoMenu(linhaDaCategoria(page, nomeLongo), page, 'Arquivar');
  await expect(dialogo.getByRole('heading', { name: `Arquivar ${nomeLongo}?` })).toBeVisible();
  expect(await cabem(dialogo.locator('.confirmacao__cabecalho, .confirmacao__texto'))).toBe(true);
  await dialogo.getByRole('button', { name: 'Arquivar', exact: true }).click();
  await expect(dialogo).toBeHidden();
  await page.getByRole('button', { name: /^Arquivadas/ }).click();
  await expect(page.getByRole('list', { name: `Subcategorias arquivadas de ${nomeLongo}` }).getByRole('listitem')).toHaveText([nomeLongo]);
  expect(await semRolagemHorizontal(page)).toBe(true);
});

test('o saldo total negativo de 7 dígitos cabe no cartão dourado em cada largura', async ({ page }) => {
  await entrar(page);
  await irPeloMenu(page, 'Contas');
  await expect(page.getByRole('heading', { name: 'Contas', level: 1 })).toBeVisible();
  await novaConta(page, 'Financiamento', 'Conta corrente', '-1.250.000');
  const valor = page.locator('app-saldo-em-destaque .flux-cartao-destaque__valor');
  await expect(valor.locator('.valor__texto')).toHaveText('−R$ 1.230.901,60');
  expect(await manropeCarregada(page)).toBe(true);
  // Desktop: de 960 px à largura do quadro do mockup (1.280 px); celular: 360 e 375 px.
  const larguras = ehDesktop(page) ? [960, 1024, 1150, 1280] : [360, 375];
  for (const largura of larguras) {
    await page.setViewportSize({ width: largura, height: 800 });
    expect(await cabem(valor), `Contas a ${largura} px`).toBe(true);
  }
  if (ehDesktop(page)) {
    // A 1.280 px fica a grade do mockup: o cartão e "Disponível" na mesma linha.
    const cartao = await page.locator('app-saldo-em-destaque').boundingBox();
    const disponivel = await page.locator('.contas-resumo__parte').first().boundingBox();
    expect(Math.abs((cartao?.y ?? 0) - (disponivel?.y ?? -1))).toBeLessThan(1);
  }
  await irPeloMenu(page, 'Início');
  await expect(valor.locator('.valor__texto')).toHaveText('−R$ 1.230.901,60');
  for (const largura of larguras) {
    await page.setViewportSize({ width: largura, height: 800 });
    expect(await cabem(valor), `Início a ${largura} px`).toBe(true);
  }
});
