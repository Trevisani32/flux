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

// Fatia 04a: a importação, com o mesmo usuário da jornada (o e2e já faz 8 dos 10 cadastros por
// hora). O OFX é fictício e nasce na memória: o .gitignore não deixa arquivo .ofx fora dos
// fixtures do backend (decisão 42). As linhas são de hoje, a data do saldo inicial da conta nova.
function extratoDeHoje(): Buffer {
  const hoje = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date()).replaceAll('-', '');
  const transacao = (fitid: string, valor: string, memo: string) =>
    `<STMTTRN><TRNTYPE>OTHER<DTPOSTED>${hoje}000000[-3:BRT]<TRNAMT>${valor}<FITID>${fitid}<MEMO>${memo}</STMTTRN>\n`;
  return Buffer.from('OFXHEADER:100\nDATA:OFXSGML\nVERSION:102\nCHARSET:1252\n\n<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><CURDEF>BRL\n'
    + '<BANKACCTFROM><BANKID>0341<ACCTID>00000-0<ACCTTYPE>CHECKING</BANKACCTFROM>\n<BANKTRANLIST>\n'
    + transacao('e2e-1', '-6.00', 'CAFE DA ESQUINA') + transacao('e2e-2', '-6.00', 'CAFE DA ESQUINA')
    + transacao('e2e-3', '250.00', 'PIX RECEBIDO MARIA') + '</BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>\n', 'utf8');
}

async function escolherOExtrato(page: Page): Promise<void> {
  await irPeloMenu(page, 'Importação');
  await expect(page.getByRole('heading', { name: 'Importação', level: 1 })).toBeVisible();
  await page.getByLabel('Conta', { exact: true }).selectOption({ label: 'Conta Itaú' });
  await page.locator('#importacao-arquivo').setInputFiles({ name: 'extrato.ofx', mimeType: 'application/x-ofx', buffer: extratoDeHoje() });
}

test('importa um OFX: revisão, categoria nas linhas iguais, resultado e a lista', async ({ page }) => {
  await entrar(page);
  await irPeloMenu(page, 'Contas');
  await novaConta(page, 'Conta Itaú', 'Conta corrente', '0');
  await escolherOExtrato(page);

  await expect(page.getByRole('heading', { name: 'Revisar importação', level: 1 })).toBeVisible();
  await expect(page.locator('.revisao__nome-do-grupo')).toHaveText(['Novas · 3']);
  await page.getByRole('button', { name: 'Categoria de CAFE DA ESQUINA: Sem categoria' }).first().click();
  const seletor = page.getByRole('dialog');
  await seletor.getByLabel('Restaurante', { exact: true }).check();
  await expect(seletor.getByLabel('Usar também na outra linha com a mesma descrição')).toBeChecked();
  await seletor.getByRole('button', { name: 'Aplicar' }).click();
  await expect(seletor).toBeHidden();
  await expect(page.locator('.revisao__categoria')).toHaveText(['Sem categoria', 'Alimentação › Restaurante', 'Alimentação › Restaurante']);
  if (!ehDesktop(page)) {
    expect(await semRolagemHorizontal(page)).toBe(true);
  }
  await page.getByRole('button', { name: 'Importar 3 lançamentos' }).click();

  await expect(page.getByRole('heading', { name: '3 lançamentos importados em Conta Itaú', level: 2 })).toBeVisible();
  await expect(page.locator('.importacao__texto-pronto').last()).toHaveText('Saldo de hoje de Conta Itaú: R$ 238,00');
  await page.getByRole('button', { name: 'Ver lançamentos' }).click();
  await expect(page.getByRole('heading', { name: 'Lançamentos', level: 1 })).toBeVisible();
  await expect(linha(page, 'CAFE DA ESQUINA')).toHaveCount(2);
});

test('reimportar avisa e não duplica; excluir o importado o ignora, e desfazer a importação apaga tudo', async ({ page }) => {
  await entrar(page);
  await escolherOExtrato(page);
  await expect(page.getByText('O que já entrou aparece como "Já importada".')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Nada novo neste arquivo' })).toBeVisible();
  await page.getByRole('button', { name: 'Escolher outro arquivo' }).click();

  await irPeloMenu(page, 'Lançamentos');
  await linha(page, 'PIX RECEBIDO MARIA').click();
  const dialogo = page.getByRole('dialog');
  await expect(dialogo).toContainText('Tipo, valor, data e conta vêm do extrato e não mudam.');
  await expect(dialogo.getByLabel('Valor', { exact: true })).toBeDisabled();
  await dialogo.getByRole('button', { name: 'Excluir' }).click();
  const confirmacao = page.getByRole('dialog').filter({ hasText: 'Excluir PIX RECEBIDO MARIA?' });
  await expect(confirmacao).toContainText('não volta se você importar o extrato de novo');
  await confirmacao.getByRole('button', { name: 'Excluir lançamento' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(linha(page, 'PIX RECEBIDO MARIA')).toHaveCount(0);
  await page.getByRole('button', { name: 'Desfazer', exact: true }).click();
  await expect(linha(page, 'PIX RECEBIDO MARIA')).toHaveCount(1);

  await irPeloMenu(page, 'Importação');
  await page.getByRole('button', { name: 'Ações da importação extrato.ofx' }).click();
  await page.getByRole('menuitem', { name: 'Desfazer importação' }).click();
  const desfazer = page.getByRole('dialog').filter({ hasText: 'Desfazer a importação de extrato.ofx?' });
  await expect(desfazer).toContainText('Os 3 lançamentos que ela trouxe saem de Conta Itaú. O saldo muda na hora.');
  await desfazer.getByRole('button', { name: 'Desfazer importação' }).click();
  await expect(page.locator('.importacao__item--desfeita')).toContainText('extrato.ofx');
  if (!ehDesktop(page)) {
    expect(await semRolagemHorizontal(page)).toBe(true);
  }
});

// Fatia 04b: as regras de categorização, com o mesmo usuário (decisão 42). O primeiro extrato foi
// desfeito no teste anterior, então as linhas dele entram de novo; o segundo é outro arquivo do dia.
function segundoExtratoDeHoje(): Buffer {
  const hoje = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date()).replaceAll('-', '');
  const transacao = (fitid: string, valor: string, memo: string) =>
    `<STMTTRN><TRNTYPE>OTHER<DTPOSTED>${hoje}000000[-3:BRT]<TRNAMT>${valor}<FITID>${fitid}<MEMO>${memo}</STMTTRN>\n`;
  return Buffer.from('OFXHEADER:100\nDATA:OFXSGML\nVERSION:102\nCHARSET:1252\n\n<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><CURDEF>BRL\n'
    + '<BANKACCTFROM><BANKID>0341<ACCTID>00000-0<ACCTTYPE>CHECKING</BANKACCTFROM>\n<BANKTRANLIST>\n'
    + transacao('e2e-4', '-7.50', 'CAFE DA ESQUINA') + transacao('e2e-5', '80.00', 'PIX RECEBIDO JOAO')
    + '</BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>\n', 'utf8');
}

/** O diálogo (a raiz dele: `.regra-dialogo`, `.aplicar` e, na 04c, `.conversao` e `.convertida`) não rola para o lado no celular. */
async function dialogoDasRegrasSemRolagemHorizontal(page: Page, raiz: string): Promise<boolean> {
  return page.evaluate((seletorDaRaiz) => {
    const pecas = Array.from(document.querySelectorAll(`.cdk-overlay-pane, .mat-mdc-dialog-surface, ${seletorDaRaiz}`));
    return pecas.length >= 3 && pecas.every((peca) => {
      const caixa = peca.getBoundingClientRect();
      return peca.scrollWidth <= peca.clientWidth && caixa.left >= 0 && caixa.right <= window.innerWidth;
    });
  }, raiz);
}

test('o resultado da importação sugere a regra das escolhas à mão, e ela aparece em Regras (Fatia 04b)', async ({ page }) => {
  await entrar(page);
  await escolherOExtrato(page);
  await expect(page.getByRole('heading', { name: 'Revisar importação', level: 1 })).toBeVisible();
  await page.getByRole('button', { name: 'Categoria de CAFE DA ESQUINA: Sem categoria' }).first().click();
  const seletor = page.getByRole('dialog');
  await seletor.getByLabel('Restaurante', { exact: true }).check();
  await seletor.getByRole('button', { name: 'Aplicar' }).click();
  await expect(seletor).toBeHidden();
  await page.getByRole('button', { name: 'Importar 3 lançamentos' }).click();

  await expect(page.getByRole('heading', { name: '3 lançamentos importados em Conta Itaú', level: 2 })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Criar regras com as suas escolhas', level: 2 })).toBeVisible();
  await page.getByRole('button', { name: 'Criar regra para CAFE DA ESQUINA' }).click();
  const dialogo = page.getByRole('dialog');
  await expect(dialogo.getByRole('heading', { name: 'Nova regra' })).toBeVisible();
  await expect(dialogo.getByLabel('Texto', { exact: true })).toHaveValue('CAFE DA ESQUINA');
  await expect(dialogo.locator('.regra-dialogo__texto')).toHaveText('Sugerida a partir de CAFE DA ESQUINA, que você pôs em Alimentação › Restaurante.');
  await expect(dialogo.locator('.regra-dialogo__posicao')).toHaveText('Entra em 1º lugar. Dá para mudar a ordem na lista.');
  await expect(dialogo.locator('.regra-dialogo__titulo-previa')).toHaveText('Casa com 2 lançamentos seus, 0 sem categoria');
  if (!ehDesktop(page)) {
    expect(await dialogoDasRegrasSemRolagemHorizontal(page, '.regra-dialogo')).toBe(true);
  }
  await dialogo.getByRole('button', { name: 'Criar regra' }).click();
  await expect(dialogo).toBeHidden();
  await expect(page.getByText('Regra criada.', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Criar regra para CAFE DA ESQUINA' })).toHaveCount(0);

  await irPeloMenu(page, 'Regras');
  await expect(page.getByRole('heading', { name: 'Regras', level: 1 })).toBeVisible();
  await expect(page.locator('.regras__condicao')).toHaveText(['1ª: Contém CAFE DA ESQUINA, categoria Alimentação › Restaurante']);
});

test('a revisão seguinte vem categorizada pelas regras, e "Aplicar regras" de Lançamentos pega o que faltou (Fatia 04b)', async ({ page }) => {
  await entrar(page);
  await irPeloMenu(page, 'Regras');
  await page.getByRole('button', { name: 'Nova regra' }).click();
  const dialogo = page.getByRole('dialog');
  await expect(dialogo.locator('.regra-dialogo__posicao'))
    .toHaveText('Entra em 1º lugar, antes de "contém CAFE DA ESQUINA". Dá para mudar a ordem na lista.');
  await dialogo.getByLabel('Texto', { exact: true }).fill('pix recebido');
  await dialogo.getByLabel('Categoria', { exact: true }).selectOption({ label: 'Outras receitas' });
  await dialogo.getByLabel('Descrição legível (opcional)', { exact: true }).fill('Pix recebido');
  await expect(dialogo.locator('.regra-dialogo__titulo-previa')).toHaveText('Casa com 1 lançamento seu, 1 sem categoria');
  await dialogo.getByRole('button', { name: 'Criar regra' }).click();
  await expect(dialogo).toBeHidden();
  await expect(page.getByText('Regra criada. 1 lançamento sem categoria casa com ela.')).toBeVisible();
  await expect(page.locator('.regras__posicao')).toHaveText(['1', '2']);
  if (ehDesktop(page)) {
    await page.getByRole('button', { name: 'Descer a regra contém "pix recebido"' }).click();
  } else {
    await page.getByRole('button', { name: 'Ações da regra contém "pix recebido"' }).click();
    await page.getByRole('menuitem', { name: 'Descer para 2ª' }).click();
    expect(await semRolagemHorizontal(page)).toBe(true);
  }
  await expect(page.locator('.regras__condicao')).toHaveText(['1ª: Contém CAFE DA ESQUINA, categoria Alimentação › Restaurante',
    '2ª: Contém pix recebido, categoria Outras receitas']);

  await irPeloMenu(page, 'Importação');
  await page.getByLabel('Conta', { exact: true }).selectOption({ label: 'Conta Itaú' });
  await page.locator('#importacao-arquivo').setInputFiles({ name: 'extrato-2.ofx', mimeType: 'application/x-ofx', buffer: segundoExtratoDeHoje() });
  await expect(page.getByRole('heading', { name: 'Revisar importação', level: 1 })).toBeVisible();
  await expect(page.locator('.revisao__descricao b')).toHaveText(['Pix recebido', 'CAFE DA ESQUINA']);
  await expect(page.locator('.revisao__banco')).toHaveText(['No extrato: PIX RECEBIDO JOAO']);
  await expect(page.locator('.revisao__categoria')).toHaveText(['Outras receitas', 'Alimentação › Restaurante']);
  await expect(page.locator('.revisao__por')).toHaveText(['pela 2ª regra', 'pela 1ª regra']);
  // A faixa conta as categorizadas por regras (decisão 14): "Categorizadas por regras" no desktop, "Por regras" no celular,
  // onde "Ficam de fora" sai da faixa (continua na pílula); a condição da regra só aparece no desktop.
  const colunaDasRegras = page.locator('.revisao__faixa-regras');
  await expect(colunaDasRegras.locator('.revisao__numero')).toHaveText('2');
  await expect(colunaDasRegras.getByText(ehDesktop(page) ? 'Categorizadas por regras' : 'Por regras', { exact: true })).toBeVisible();
  await expect(page.locator('.revisao__faixa-fora')).toBeVisible({ visible: ehDesktop(page) });
  await expect(page.locator('.revisao__condicao-regra').first()).toBeVisible({ visible: ehDesktop(page) });
  if (ehDesktop(page)) {
    await expect(page.locator('.revisao__condicao-regra')).toHaveText(['contém "pix recebido"', 'contém "CAFE DA ESQUINA"']);
  } else {
    expect(await semRolagemHorizontal(page)).toBe(true);
  }
  await page.getByRole('button', { name: 'Importar 2 lançamentos' }).click();
  await expect(page.getByRole('heading', { name: '2 lançamentos importados em Conta Itaú', level: 2 })).toBeVisible();
  await expect(page.locator('.importacao__texto-pronto').first()).toContainText('2 categorizados por regras');
  await expect(page.getByRole('heading', { name: 'Criar regras com as suas escolhas', level: 2 })).toHaveCount(0);

  await irPeloMenu(page, 'Lançamentos');
  await page.locator('.lancamentos__filtro select').nth(1).selectOption({ label: 'Sem categoria' });
  await expect(linha(page, 'PIX RECEBIDO MARIA')).toHaveCount(1);
  await page.getByRole('button', { name: 'Aplicar regras' }).click();
  const aplicar = page.getByRole('dialog');
  await expect(aplicar.locator('.aplicar__regra')).toHaveText(['2ª · Contém pix recebido → Outras receitas']);
  if (!ehDesktop(page)) {
    expect(await dialogoDasRegrasSemRolagemHorizontal(page, '.aplicar')).toBe(true);
  }
  await aplicar.getByRole('button', { name: 'Aplicar a 1 lançamento' }).click();
  await expect(aplicar).toBeHidden();
  await expect(page.getByText('1 lançamento categorizado.')).toBeVisible();
  await expect(linha(page, 'PIX RECEBIDO MARIA')).toHaveCount(0);
  await page.locator('.lancamentos__filtro select').nth(1).selectOption({ label: 'Todas as categorias' });
  await expect(linha(page, 'Pix recebido')).toHaveCount(2);
});

// Fatia 04c: a conversão de importados em transferência, com o mesmo usuário (decisão 42 da 04b). A TED
// sai da Conta Itaú, e a receita que ela casa, lançada à mão, entra na Poupança, no mesmo dia.
function terceiroExtratoDeHoje(): Buffer {
  const hoje = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date()).replaceAll('-', '');
  return Buffer.from('OFXHEADER:100\nDATA:OFXSGML\nVERSION:102\nCHARSET:1252\n\n<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><CURDEF>BRL\n'
    + '<BANKACCTFROM><BANKID>0341<ACCTID>00000-0<ACCTTYPE>CHECKING</BANKACCTFROM>\n<BANKTRANLIST>\n'
    + `<STMTTRN><TRNTYPE>OTHER<DTPOSTED>${hoje}000000[-3:BRT]<TRNAMT>-300.00<FITID>e2e-6<MEMO>TED 341.0001 RESERVA</STMTTRN>\n`
    + '</BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>\n', 'utf8');
}

/** Hoje como a tela escreve: "11/11/2026". */
function hojeNoFormatoCurto(): string {
  return new Intl.DateTimeFormat('pt-BR', { timeZone: 'America/Sao_Paulo', day: '2-digit', month: '2-digit', year: 'numeric' }).format(new Date());
}

test('o resultado da importação sugere o par, "Converter" liga os dois, e a convertida é uma linha neutra (Fatia 04c)', async ({ page }) => {
  await entrar(page);
  await irPeloMenu(page, 'Lançamentos');
  await page.getByRole('button', { name: 'Novo lançamento' }).first().click();
  const novo = page.getByRole('dialog');
  await escolherTipo(novo, 'Receita');
  await novo.getByLabel('Valor', { exact: true }).fill('300');
  await novo.getByLabel('Descrição', { exact: true }).fill('Reserva');
  await novo.getByLabel('Conta', { exact: true }).selectOption({ label: 'Poupança' });
  await novo.getByRole('button', { name: 'Criar lançamento' }).click();
  await expect(novo).toBeHidden();

  await irPeloMenu(page, 'Importação');
  await page.getByLabel('Conta', { exact: true }).selectOption({ label: 'Conta Itaú' });
  await page.locator('#importacao-arquivo').setInputFiles({ name: 'extrato-3.ofx', mimeType: 'application/x-ofx', buffer: terceiroExtratoDeHoje() });
  await expect(page.getByRole('heading', { name: 'Revisar importação', level: 1 })).toBeVisible();
  await page.getByRole('button', { name: 'Importar 1 lançamento' }).click();
  await expect(page.getByRole('heading', { name: '1 lançamento importado em Conta Itaú', level: 2 })).toBeVisible();
  await expect(page.getByRole('heading', { name: ehDesktop(page) ? 'Parecem transferências entre suas contas' : 'Parecem transferências',
    level: 2 })).toBeVisible();
  await expect(page.locator('.importacao__lado-do-par b')).toHaveText(['TED 341.0001 RESERVA', 'Reserva']);
  if (!ehDesktop(page)) {
    expect(await semRolagemHorizontal(page)).toBe(true);
  }
  await page.getByRole('button', { name: 'Converter TED 341.0001 RESERVA em transferência' }).click();

  const conversao = page.locator('app-conversao-dialogo');
  await expect(conversao.getByRole('heading', { name: 'Converter em transferência' })).toBeVisible();
  await expect(conversao.getByRole('radio', { name: /^Reserva/ })).toBeChecked();
  await expect(conversao.locator('.conversao__fixo .conversao__meta')).toHaveText(/^Sai de Conta Itaú em \d{2}\/\d{2} · importado$/);
  await conversao.getByLabel('Descrição (opcional)', { exact: true }).fill('Reserva da viagem');
  if (!ehDesktop(page)) {
    await expect(conversao.getByRole('button', { name: 'Voltar' })).toBeVisible();
    expect(await dialogoDasRegrasSemRolagemHorizontal(page, '.conversao')).toBe(true);
  }
  await conversao.getByRole('button', { name: 'Converter', exact: true }).click();
  await expect(conversao).toHaveCount(0);
  await expect(page.getByText('Transferência criada.')).toBeVisible();
  await expect(page.locator('#importacao-pares')).toHaveCount(0);

  await irPeloMenu(page, 'Lançamentos');
  const convertida = linha(page, 'Reserva da viagem');
  await expect(convertida.locator('.valor__texto')).toHaveText('R$ 300,00');
  await expect(convertida.locator('app-valor')).toHaveClass(/flux-valor--neutro/);
  await expect(convertida.locator('.linha__meta-texto').filter({ visible: true })).toHaveText(
    ehDesktop(page) ? 'Conta Itaú → Poupança · fora dos totais' : 'Conta Itaú → Poupança');
  await expect(linha(page, 'Reserva')).toHaveCount(0);
  await expect(linha(page, 'TED 341.0001 RESERVA')).toHaveCount(0);
});

test('a convertida muda só o texto; desfazer devolve a despesa e a receita, e desfazer a importação desfaz a conversão (Fatia 04c)', async ({ page }) => {
  await entrar(page);
  await irPeloMenu(page, 'Lançamentos');
  await linha(page, 'Reserva da viagem').click();
  const dialogo = page.getByRole('dialog');
  await expect(dialogo.getByRole('heading', { name: 'Editar transferência' })).toBeVisible();
  await expect(dialogo).toContainText('Convertida de um lançamento importado');
  await expect(dialogo.getByLabel('Valor', { exact: true })).toBeDisabled();
  await expect(dialogo.locator('.convertida__meta')).toHaveText([`${hojeNoFormatoCurto()} · importado`, 'No extrato: TED 341.0001 RESERVA',
    `${hojeNoFormatoCurto()} · lançado à mão`]);
  await expect(dialogo.getByRole('button', { name: 'Excluir' })).toHaveCount(0);
  if (!ehDesktop(page)) {
    expect(await dialogoDasRegrasSemRolagemHorizontal(page, '.convertida')).toBe(true);
  }
  await dialogo.getByLabel('Descrição (opcional)', { exact: true }).fill('Viagem');
  await dialogo.getByRole('button', { name: 'Salvar' }).click();
  await expect(dialogo).toBeHidden();
  await expect(linha(page, 'Viagem')).toHaveCount(1);

  await linha(page, 'Viagem').click();
  await page.getByRole('dialog').getByRole('button', { name: 'Desfazer transferência' }).click();
  const confirmacao = page.getByRole('dialog').filter({ hasText: 'Desfazer a transferência?' });
  await expect(confirmacao).toContainText('Os dois lançamentos voltam a ser uma despesa em Conta Itaú e uma receita em Poupança, sem categoria.');
  await confirmacao.getByRole('button', { name: 'Desfazer transferência' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByText('Transferência desfeita.')).toBeVisible();
  await expect(linha(page, 'TED 341.0001 RESERVA').locator('.valor__texto')).toHaveText('−R$ 300,00');
  await expect(linha(page, 'Viagem').locator('.valor__texto')).toHaveText('+R$ 300,00');

  // Pelo atalho do diálogo do importado (decisão 26): o único par vem escolhido.
  await linha(page, 'TED 341.0001 RESERVA').click();
  await page.getByRole('dialog').getByRole('button', { name: 'Converter em transferência' }).click();
  const conversao = page.locator('app-conversao-dialogo');
  await expect(conversao.getByRole('radio', { name: /^Viagem/ })).toBeChecked();
  await conversao.getByLabel('Descrição (opcional)', { exact: true }).fill('Reserva outra vez');
  await conversao.getByRole('button', { name: 'Converter', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(linha(page, 'Reserva outra vez')).toHaveCount(1);

  await irPeloMenu(page, 'Importação');
  await page.getByRole('button', { name: 'Ações da importação extrato-3.ofx' }).click();
  await page.getByRole('menuitem', { name: 'Desfazer importação' }).click();
  const desfazer = page.getByRole('dialog').filter({ hasText: 'Desfazer a importação de extrato-3.ofx?' });
  await expect(desfazer.locator('.flux-alerta--atencao .flux-alerta__titulo')).toHaveText('1 deles está numa transferência');
  await expect(desfazer.locator('.flux-alerta--atencao .flux-alerta__texto')).toHaveText(`A transferência de ${hojeNoFormatoCurto().slice(0, 5)} `
    + 'com Poupança é desfeita, e o lançamento de Poupança volta a ser uma receita, sem categoria.');
  if (!ehDesktop(page)) {
    expect(await semRolagemHorizontal(page)).toBe(true);
  }
  await desfazer.getByRole('button', { name: 'Desfazer importação' }).click();
  await expect(page.locator('.importacao__item--desfeita').filter({ hasText: 'extrato-3.ofx' })).toHaveCount(1);

  await irPeloMenu(page, 'Lançamentos');
  await expect(linha(page, 'Reserva outra vez').locator('.valor__texto')).toHaveText('+R$ 300,00');
  await expect(linha(page, 'TED 341.0001 RESERVA')).toHaveCount(0);
});

const MESES = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'];

/** O mês de hoje no fuso do negócio ("2026-11"), ou o que fica `passo` meses depois: as faturas dependem da data real. */
function mesDeHoje(passo = 0): string {
  const [ano, mes] = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo', year: 'numeric', month: '2-digit' }).format(new Date())
    .split('-').map(Number);
  const contagem = ano * 12 + mes - 1 + passo;
  return `${Math.floor(contagem / 12)}-${String((contagem % 12) + 1).padStart(2, '0')}`;
}

/** "novembro". */
function mesSemAno(mes: string): string {
  return MESES[Number(mes.slice(5, 7)) - 1];
}

/** "novembro de 2026". */
function mesComAno(mes: string): string {
  return `${mesSemAno(mes)} de ${mes.slice(0, 4)}`;
}

/** "dezembro a fevereiro" ou, cruzando anos, "dezembro de 2026 a janeiro de 2027", como a fatura deste mês escreve. */
function faixaDeMeses(de: string, ate: string): string {
  return de.slice(0, 4) === ate.slice(0, 4) ? `${mesSemAno(de)} a ${mesSemAno(ate)}` : `${mesComAno(de)} a ${mesComAno(ate)}`;
}

/** "10/11": o dia `dia` do mês. */
function diaDoMes(dia: number, mes: string): string {
  return `${String(dia).padStart(2, '0')}/${mes.slice(5, 7)}`;
}

/** No celular, "Novo estorno" e "Ajustar fechamento" ficam no menu "Mais" da fatura. */
async function acaoDaFatura(page: Page, acao: 'Novo estorno' | 'Ajustar fechamento'): Promise<void> {
  if (ehDesktop(page)) {
    await page.getByRole('button', { name: acao }).click();
  } else {
    await page.getByRole('button', { name: 'Mais', exact: true }).click();
    await page.getByRole('menuitem', { name: acao }).click();
  }
}

function itemDaFatura(page: Page, descricao: string): Locator {
  return page.locator('.fatura__abrir, .fatura__item').filter({ visible: true }).filter({ hasText: descricao });
}

function totalDaFatura(page: Page): Locator {
  return page.locator('.fatura__totais > div').nth(2).locator('.fatura__numero');
}

// Fatia 05a: o cartão fecha dia 10 e vence dia 20. A compra é do dia 10 deste mês (M), o próprio fechamento:
// cai na fatura de M, e as 3 parcelas vão para M, M+1 e M+2, qualquer que seja o dia de hoje.
test('cadastra o cartão e lança uma compra em 3x, que se espalha pelas faturas e aparece em Lançamentos (Fatia 05a)', async ({ page }) => {
  const mes = mesDeHoje();
  await entrar(page);
  await irPeloMenu(page, 'Cartões');
  await expect(page.getByRole('heading', { name: 'Cartões', level: 1 })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Nenhum cartão ainda', level: 2 })).toBeVisible();
  await page.getByRole('button', { name: 'Novo cartão' }).first().click();
  const dialogo = page.getByRole('dialog');
  await expect(dialogo.getByRole('heading', { name: 'Novo cartão' })).toBeVisible();
  // A conta arquivada não paga fatura (decisão 4).
  await expect(dialogo.getByLabel('Conta de pagamento', { exact: true })).not.toContainText('Banco Horizonte');
  await dialogo.getByLabel('Nome', { exact: true }).fill('Cartão Horizonte');
  await dialogo.getByLabel('Limite', { exact: true }).fill('5.000');
  await dialogo.getByLabel('Dia de fechamento', { exact: true }).fill('10');
  await dialogo.getByLabel('Dia de vencimento', { exact: true }).fill('20');
  await dialogo.getByLabel('Conta de pagamento', { exact: true }).selectOption({ label: 'Conta Itaú' });
  await dialogo.getByRole('button', { name: 'Salvar' }).click();
  await expect(dialogo).toBeHidden();
  await expect(page.getByText('Cartão criado.')).toBeVisible();
  await expect(page.locator('.cartoes__nome')).toHaveText(['Cartão Horizonte']);
  await expect(page.locator('.cartoes__meta span').filter({ visible: true })).toHaveText(
    ehDesktop(page) ? 'Fecha dia 10 · vence dia 20 · paga com Conta Itaú' : 'Fecha 10 · vence 20 · Conta Itaú');
  expect(await semRolagemHorizontal(page)).toBe(true);

  await page.locator('.cartoes__ver').click();
  await expect(page.getByRole('heading', { name: /^Fatura de /, level: 1 })).toBeVisible();
  await page.getByRole('button', { name: 'Nova compra' }).first().click();
  const compra = page.getByRole('dialog');
  await expect(compra.getByRole('heading', { name: 'Nova compra' })).toBeVisible();
  await expect(compra.getByLabel('Cartão', { exact: true }).locator('option:checked')).toHaveText('Cartão Horizonte');
  await compra.getByLabel('Data da compra', { exact: true }).fill(`${mes}-10`);
  await compra.getByLabel('Descrição', { exact: true }).fill('Sapataria Passo Firme');
  await compra.getByLabel('Valor total', { exact: true }).fill('450');
  await compra.getByLabel('Parcelas', { exact: true }).selectOption({ label: '3x' });
  await compra.getByLabel('Categoria (opcional)', { exact: true }).selectOption({ label: 'Compras' });
  // A prévia vem da API (decisão 18).
  await expect(compra.locator('.compra__titulo-previa')).toHaveText('3x de R$ 150,00');
  await expect(compra.locator('.compra__previa')).toContainText(
    `1ª na fatura de ${mesSemAno(mes)} (vence 20/${mes.slice(5, 7)}/${mes.slice(0, 4)}) · última em ${mesComAno(mesDeHoje(2))}.`);
  if (!ehDesktop(page)) {
    expect(await dialogoDasRegrasSemRolagemHorizontal(page, '.compra')).toBe(true);
  }
  await compra.getByRole('button', { name: 'Lançar 3 parcelas' }).click();
  await expect(compra).toBeHidden();
  await expect(page.getByText('Compra lançada em 3 parcelas.')).toBeVisible();
  await page.getByRole('button', { name: 'Ver fatura' }).click();
  await expect(page).toHaveURL(new RegExp(`/cartoes/\\d+/faturas/${mes}$`));
  await expect(page.getByRole('heading', { name: `Fatura de ${mesComAno(mes)}`, level: 1 })).toBeVisible();
  await expect(itemDaFatura(page, 'Sapataria Passo Firme')).toHaveCount(1);
  await expect(page.locator('.fatura__pilula').filter({ visible: true })).toHaveText(['1/3']);
  await expect(page.locator('.fatura__marco')).toHaveText([`${diaDoMes(11, mesDeHoje(-1))}começa`, `${diaDoMes(10, mes)}fecha`, `${diaDoMes(20, mes)}vence`]);
  await expect(totalDaFatura(page)).toHaveText('R$ 150,00');
  await expect(page.locator('.fatura__totais > div').nth(3).locator('.fatura__nota'))
    .toHaveText(`parcelas de ${faixaDeMeses(mesDeHoje(1), mesDeHoje(2))}`);
  expect(await semRolagemHorizontal(page)).toBe(true);
  await page.getByRole('button', { name: `Fatura de ${mesSemAno(mesDeHoje(1))}` }).click();
  await expect(page.getByRole('heading', { name: `Fatura de ${mesComAno(mesDeHoje(1))}`, level: 1 })).toBeVisible();
  await expect(page.locator('.fatura__pilula').filter({ visible: true })).toHaveText(['2/3']);

  // Em Lançamentos, a parcela aparece no vencimento da fatura, com o cartão (decisão 22).
  await irPeloMenu(page, 'Lançamentos');
  const parcela = linha(page, 'Sapataria Passo Firme');
  await expect(parcela.locator('.linha__pilula--parcela')).toHaveText('1/3');
  await expect(parcela.locator('.valor__texto')).toHaveText('−R$ 150,00');
  await expect(parcela.locator('.linha__meta-texto').filter({ visible: true })).toHaveText(
    ehDesktop(page) ? `Compras · Cartão Horizonte · compra em ${diaDoMes(10, mes)}` : `Cartão Horizonte · compra em ${diaDoMes(10, mes)}`);
  await page.locator('.lancamentos__filtro select').first().selectOption({ label: 'Cartão Horizonte' });
  await expect(page).toHaveURL(/[?&]cartao=\d+/);
  await expect(page.locator('.linha__titulo')).toHaveText(['Sapataria Passo Firme']);
  await expect(page.locator('.lancamentos__subtitulo')).toContainText('Cartão Horizonte · 1 lançamento');
  await expect(page.locator('.lancamentos__fatura')).toContainText(
    `Fatura de ${mesSemAno(mes)} · fecha ${diaDoMes(10, mes)} · vence ${diaDoMes(20, mes)} · R$ 150,00`);
  expect(await semRolagemHorizontal(page)).toBe(true);
  await parcela.click();
  await expect(page.getByRole('dialog').getByRole('heading', { name: 'Editar compra' })).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
});

test('ajusta o fechamento e volta ao normal, lança um estorno e exclui a compra (Fatia 05a)', async ({ page }) => {
  const mes = mesDeHoje();
  await entrar(page);
  await irPeloMenu(page, 'Lançamentos');
  await page.locator('.lancamentos__filtro select').first().selectOption({ label: 'Cartão Horizonte' });
  await page.locator('.lancamentos__fatura').getByRole('link', { name: 'Ver fatura' }).click();
  await expect(page.getByRole('heading', { name: `Fatura de ${mesComAno(mes)}`, level: 1 })).toBeVisible();

  // Decisão 12: o ajuste para o dia 9 tira desta fatura a compra do dia 10, e as parcelas andam junto.
  await acaoDaFatura(page, 'Ajustar fechamento');
  const ajuste = page.getByRole('dialog');
  await expect(ajuste.getByRole('heading', { name: `Ajustar o fechamento de ${mesSemAno(mes)}` })).toBeVisible();
  await expect(ajuste.locator('#ajuste-data-dica')).toHaveText(`De ${diaDoMes(11, mesDeHoje(-1))} a ${diaDoMes(19, mes)}: depois do fechamento de `
    + `${mesSemAno(mesDeHoje(-1))} e antes do vencimento, ${diaDoMes(20, mes)}.`);
  await ajuste.getByLabel('Fecha em', { exact: true }).fill(`${mes}-09`);
  await expect(ajuste.locator('.flux-alerta__titulo')).toHaveText(`Compras de ${diaDoMes(10, mes)} passam desta fatura para ${mesSemAno(mesDeHoje(1))}.`);
  if (!ehDesktop(page)) {
    expect(await dialogoDasRegrasSemRolagemHorizontal(page, '.ajuste')).toBe(true);
  }
  await ajuste.getByRole('button', { name: 'Ajustar', exact: true }).click();
  await expect(ajuste).toBeHidden();
  await expect(page.getByText('Fechamento ajustado. 1 compra mudou de fatura.')).toBeVisible();
  await expect(page.locator('.fatura__marco').nth(1)).toHaveText(`${diaDoMes(9, mes)}fecha · ajustado`);
  await expect(page.getByRole('heading', { name: `Nada na fatura de ${mesComAno(mes)}`, level: 2 })).toBeVisible();
  await page.getByRole('button', { name: `Voltar ao fechamento normal (${diaDoMes(10, mes)})` }).click();
  await expect(page.getByText('O fechamento voltou ao normal.')).toBeVisible();
  await expect(page.locator('.fatura__pilula').filter({ visible: true })).toHaveText(['1/3']);

  // Decisão 20: o estorno é um crédito na fatura da data dele.
  await acaoDaFatura(page, 'Novo estorno');
  const estorno = page.getByRole('dialog');
  await expect(estorno.getByRole('heading', { name: 'Novo estorno' })).toBeVisible();
  await estorno.getByLabel('Data do estorno', { exact: true }).fill(`${mes}-10`);
  await estorno.getByLabel('Descrição', { exact: true }).fill('Estorno Sapataria');
  await estorno.getByLabel('Valor', { exact: true }).fill('50');
  await estorno.getByLabel('Categoria (opcional)', { exact: true }).selectOption({ label: 'Reembolsos' });
  await estorno.getByRole('button', { name: 'Lançar estorno' }).click();
  await expect(estorno).toBeHidden();
  await expect(page.getByText('Estorno lançado.')).toBeVisible();
  await expect(page.locator('.fatura__pilula').filter({ visible: true })).toHaveText(['Estorno', '1/3']);
  await expect(totalDaFatura(page)).toHaveText('R$ 100,00');

  // Decisão 19: excluir a compra tira as 3 parcelas; fica só o crédito do estorno.
  await itemDaFatura(page, 'Sapataria Passo Firme').click();
  const editar = page.getByRole('dialog');
  await expect(editar.getByRole('heading', { name: 'Editar compra' })).toBeVisible();
  await editar.getByRole('button', { name: 'Excluir', exact: true }).click();
  const confirmacao = page.getByRole('dialog').filter({ hasText: 'Excluir Sapataria Passo Firme?' });
  await expect(confirmacao).toContainText(`As 3 parcelas saem das faturas de ${mesComAno(mes)} a ${mesComAno(mesDeHoje(2))}. Isso não pode ser desfeito.`);
  await confirmacao.getByRole('button', { name: 'Excluir compra' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByText('Compra excluída.')).toBeVisible();
  await expect(page.locator('.fatura__pilula').filter({ visible: true })).toHaveText(['Estorno']);
  await expect(totalDaFatura(page)).toHaveText('Crédito de R$ 50,00');
  expect(await semRolagemHorizontal(page)).toBe(true);

  await irPeloMenu(page, 'Início');
  await expect(page.locator('.inicio-cartao__nome')).toHaveText(['Cartão Horizonte']);
});
