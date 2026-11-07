import { expect, test, type Page } from '@playwright/test';

import { botaoDoUsuario, emailUnico, entrarCom, esperarEmails, tokenDoEmail } from './apoio';

// Uma jornada só, em ordem: cada teste continua a conta do anterior. Cada projeto
// (chromium e celular) cria a própria conta no beforeAll.
test.describe.configure({ mode: 'serial' });

interface ContaDaJornada {
  readonly nome: string;
  readonly nomeNovo: string;
  readonly email: string;
  readonly emailNovo: string;
  readonly senha: string;
  readonly senhaNova: string;
}

let conta: ContaDaJornada;

test.beforeAll(() => {
  conta = {
    nome: 'Ana Jornada',
    nomeNovo: 'Ana Renomeada',
    email: emailUnico('jornada'),
    emailNovo: emailUnico('jornada-novo'),
    senha: 'senha-da-jornada-123',
    senhaNova: 'senha-nova-da-jornada-456',
  };
});

async function preencherCadastro(page: Page, nomeDaConta: string, emailDaConta: string, senhaDaConta: string): Promise<void> {
  await page.getByLabel('Nome', { exact: true }).fill(nomeDaConta);
  await page.getByLabel('E-mail', { exact: true }).fill(emailDaConta);
  await page.getByLabel('Senha', { exact: true }).fill(senhaDaConta);
  await page.getByRole('checkbox', { name: /Li e aceito/ }).check();
  await page.getByRole('button', { name: 'Criar conta' }).click();
}

test('cadastro, e-mail não confirmado, reenvio do link e confirmação', async ({ page, request }) => {
  await page.goto('/entrar');
  await page.getByRole('link', { name: 'Criar conta' }).click();
  await expect(page.getByRole('heading', { name: 'Criar conta', level: 1 })).toBeVisible();
  await preencherCadastro(page, conta.nome, conta.email, conta.senha);
  await expect(page.getByRole('heading', { name: 'Confira seu e-mail', level: 1 })).toBeVisible();
  await expect(page.getByText(conta.email, { exact: true })).toBeVisible();
  // Recarregar não traz o e-mail de volta: ele só vem pelo estado da navegação.
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Confira seu e-mail', level: 1 })).toBeVisible();
  await expect(page.getByText('Enviamos um link de confirmação para o e-mail informado.', { exact: false })).toBeVisible();
  await expect(page.getByText(conta.email, { exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Reenviar link' })).toHaveCount(0);

  await page.getByRole('link', { name: 'Voltar para entrar' }).click();
  await entrarCom(page, conta.email, conta.senha);
  const aviso = page.getByRole('alert');
  await expect(aviso).toContainText('Confirme seu e-mail para entrar.');
  await aviso.getByRole('button', { name: 'Reenviar link' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Link reenviado.' })).toBeVisible();

  const [novo, antigo] = await esperarEmails(request, conta.email, 'Confirme seu e-mail no Flux', 2);
  await page.goto(`/confirmar-email#token=${tokenDoEmail(antigo)}`);
  await expect(page.getByRole('heading', { name: 'Este link expirou ou já foi usado' })).toBeVisible();
  await page.goto('about:blank');
  await page.goto(`/confirmar-email#token=${tokenDoEmail(novo)}`);
  await expect(page.getByRole('heading', { name: 'E-mail confirmado' })).toBeVisible();
  await expect(page).toHaveURL(/\/confirmar-email$/);
  await page.getByRole('link', { name: 'Entrar', exact: true }).click();
  await entrarCom(page, conta.email, conta.senha);
  await expect(page.getByRole('heading', { name: `Olá, ${conta.nome}` })).toBeVisible();
});

test('cadastro com e-mail já confirmado responde igual e avisa o dono', async ({ page, request }) => {
  await page.goto('/criar-conta');
  await preencherCadastro(page, 'Outra Pessoa', conta.email, 'outra-senha-qualquer-789');
  await expect(page.getByRole('heading', { name: 'Confira seu e-mail', level: 1 })).toBeVisible();
  await esperarEmails(request, conta.email, 'Tentativa de cadastro com o seu e-mail');
  await entrarCom(page, conta.email, conta.senha);
  await expect(page.getByRole('heading', { name: `Olá, ${conta.nome}` })).toBeVisible();
});

test('esqueci a senha e redefinição pelo link', async ({ page, request }) => {
  await page.goto('/entrar');
  await page.getByRole('link', { name: 'Esqueci a senha', exact: true }).click();
  // Espera a tela nova: o Entrar também tem um campo "E-mail".
  await expect(page.getByRole('heading', { name: 'Esqueci a senha', level: 1 })).toBeVisible();
  await page.getByLabel('E-mail', { exact: true }).fill(conta.email);
  await page.getByRole('button', { name: 'Enviar link' }).click();
  await expect(page.getByRole('heading', { name: 'Confira seu e-mail', level: 1 })).toBeVisible();
  await expect(page.getByText('Ele vale por 30 minutos.', { exact: false })).toBeVisible();

  const [redefinicao] = await esperarEmails(request, conta.email, 'Redefinir sua senha do Flux');
  await page.goto(`/redefinir-senha#token=${tokenDoEmail(redefinicao)}`);
  await page.getByLabel('Nova senha', { exact: true }).fill(conta.senhaNova);
  await page.getByLabel('Confirmar nova senha', { exact: true }).fill(conta.senhaNova);
  await page.getByRole('button', { name: 'Salvar senha nova' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Senha redefinida.' })).toBeVisible();
  // O aviso veio de outra tela e aparece uma vez só: recarregar o Entrar tira o aviso.
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Entrar', level: 1 })).toBeVisible();
  await expect(page.getByText('Senha redefinida.')).toHaveCount(0);
  await entrarCom(page, conta.email, conta.senhaNova);
  await expect(page.getByRole('heading', { name: `Olá, ${conta.nome}` })).toBeVisible();
});

test('minha conta: troca do nome e do e-mail', async ({ page, request }) => {
  await entrarCom(page, conta.email, conta.senhaNova);
  await botaoDoUsuario(page, conta.nome).click();
  await page.getByRole('menuitem', { name: 'Minha conta' }).click();
  await expect(page.getByRole('heading', { name: 'Minha conta', level: 1 })).toBeVisible();

  await page.getByLabel('Nome', { exact: true }).fill(conta.nomeNovo);
  await page.getByRole('button', { name: 'Salvar nome' }).click();
  await expect(page.getByText('Nome salvo.')).toBeVisible();
  await expect(botaoDoUsuario(page, conta.nomeNovo)).toBeVisible();

  await page.getByLabel('Novo e-mail', { exact: true }).fill(conta.emailNovo);
  await page.getByLabel('Senha atual', { exact: true }).fill(conta.senhaNova);
  await page.getByRole('button', { name: 'Enviar link de confirmação' }).click();
  await expect(page.getByText(`Troca pendente para ${conta.emailNovo}`)).toBeVisible();

  const [aviso] = await esperarEmails(request, conta.email, 'Pedido de troca do e-mail da sua conta');
  expect(aviso.texto).toContain(`${conta.emailNovo.charAt(0)}•••@teste.local`);
  const [confirmacao] = await esperarEmails(request, conta.emailNovo, 'Confirme seu novo e-mail no Flux');
  await page.goto(`/confirmar-email#token=${tokenDoEmail(confirmacao)}`);
  await expect(page.getByRole('heading', { name: 'Novo e-mail confirmado' })).toBeVisible();
  await page.getByRole('link', { name: 'Ir para o Flux' }).click();
  await expect(page.getByRole('heading', { name: 'Minha conta', level: 1 })).toBeVisible();
  await expect(page.getByRole('main').getByText(conta.emailNovo, { exact: true })).toBeVisible();
  await expect(page.getByText('Troca pendente para', { exact: false })).toHaveCount(0);
});

test('exclusão da conta com a senha atual', async ({ page, request }) => {
  await entrarCom(page, conta.emailNovo, conta.senhaNova);
  await expect(page.getByRole('heading', { name: `Olá, ${conta.nomeNovo}` })).toBeVisible();
  await page.goto('/minha-conta');
  await page.getByRole('button', { name: 'Excluir minha conta' }).click();
  const dialogo = page.getByRole('dialog', { name: 'Excluir sua conta?' });
  await expect(dialogo).toBeVisible();
  const campo = dialogo.getByLabel('Senha atual', { exact: true });
  await expect(campo).toBeFocused();
  const excluir = dialogo.getByRole('button', { name: 'Excluir definitivamente' });
  await expect(excluir).toBeDisabled();
  await campo.fill('senha-errada-123');
  await excluir.click();
  await expect(dialogo.getByText('Senha atual incorreta.')).toBeVisible();
  await campo.fill(conta.senhaNova);
  await excluir.click();

  await expect(page).toHaveURL(/\/entrar$/);
  await expect(page.getByRole('status').filter({ hasText: 'Sua conta foi excluída.' })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Entrar', level: 1 })).toBeVisible();
  await expect(page.getByText('Sua conta foi excluída.')).toHaveCount(0);
  await esperarEmails(request, conta.emailNovo, 'Sua conta do Flux foi excluída');
  await entrarCom(page, conta.emailNovo, conta.senhaNova);
  await expect(page.getByRole('alert')).toHaveText('E-mail ou senha inválidos');
});
