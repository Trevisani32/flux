import { expect, type APIRequestContext, type Locator, type Page } from '@playwright/test';

export const email = process.env['E2E_EMAIL'] ?? 'dev@flux.local';
export const senha = process.env['E2E_SENHA'] ?? 'senha-de-dev-123';
export const nome = process.env['E2E_NOME'] ?? 'Dev';
/** O e-mail como a API devolve (minúsculas, sem espaços nas pontas). */
export const emailExibido = email.trim().toLowerCase();
/** API HTTP do Mailpit: 8026 no docker-compose.dev.yml, 8027 no docker-compose.e2e.yml. */
export const mailpit = process.env['E2E_MAILPIT_URL'] ?? 'http://localhost:8026';

export async function entrar(page: Page): Promise<void> {
  await entrarCom(page, email, senha);
}

export async function entrarCom(page: Page, emailDaConta: string, senhaDaConta: string): Promise<void> {
  await page.goto('/entrar');
  await page.getByLabel('E-mail', { exact: true }).fill(emailDaConta);
  await page.getByLabel('Senha', { exact: true }).fill(senhaDaConta);
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
}

export async function entrarNoInicio(page: Page): Promise<void> {
  await entrar(page);
  await expect(page.getByRole('heading', { name: `Olá, ${nome}` })).toBeVisible();
}

export function ehDesktop(page: Page): boolean {
  return (page.viewportSize()?.width ?? 1280) >= 960;
}

function escaparRegex(texto: string): string {
  return texto.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/** Nome acessível do botão do usuário: começa pelo nome visível. */
export function nomeDoBotaoDe(nomeVisivel: string): RegExp {
  return new RegExp(`^${escaparRegex(nomeVisivel)}(\\s|$)`);
}

export const nomeDoBotaoDoUsuario = nomeDoBotaoDe(nome);

export function botaoDoUsuario(page: Page, nomeVisivel: string = nome): Locator {
  return page.getByRole('button', { name: nomeDoBotaoDe(nomeVisivel) });
}

export async function semRolagemHorizontal(page: Page): Promise<boolean> {
  return page.evaluate(() => [
    document.documentElement,
    document.body,
    ...Array.from(document.querySelectorAll('mat-sidenav-content, main')),
  ].every((elemento) => elemento.scrollWidth <= elemento.clientWidth));
}

/** Endereço fictício e único por execução (o Mailpit busca por "to:" com substring). */
export function emailUnico(prefixo: string): string {
  return `${prefixo}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@teste.local`;
}

export interface EmailRecebido {
  readonly assunto: string;
  readonly texto: string;
}

interface ResumoDoMailpit {
  readonly ID: string;
  readonly Subject: string;
  readonly To: readonly { readonly Address: string }[];
}

/** E-mails para o endereço exato e com o assunto exato, do mais novo para o mais antigo. */
export async function emailsPara(request: APIRequestContext, para: string, assunto: string): Promise<EmailRecebido[]> {
  const busca = await request.get(`${mailpit}/api/v1/search`, { params: { query: `to:"${para}"` } });
  expect(busca.ok()).toBe(true);
  const { messages } = (await busca.json()) as { messages: ResumoDoMailpit[] };
  const recebidos: EmailRecebido[] = [];
  for (const resumo of messages) {
    if (resumo.Subject === assunto && resumo.To.some((destino) => destino.Address.toLowerCase() === para.toLowerCase())) {
      const mensagem = await request.get(`${mailpit}/api/v1/message/${resumo.ID}`);
      const { Text } = (await mensagem.json()) as { Text: string };
      recebidos.push({ assunto: resumo.Subject, texto: Text });
    }
  }
  return recebidos;
}

/** Espera (até 15 s) chegarem pelo menos `quantidade` e-mails; devolve do mais novo para o mais antigo. */
export async function esperarEmails(request: APIRequestContext, para: string, assunto: string, quantidade = 1): Promise<EmailRecebido[]> {
  let recebidos: EmailRecebido[] = [];
  await expect.poll(async () => {
    recebidos = await emailsPara(request, para, assunto);
    return recebidos.length;
  }, { timeout: 15_000 }).toBeGreaterThanOrEqual(quantidade);
  return recebidos;
}

export function tokenDoEmail(recebido: EmailRecebido): string {
  const encontrado = /#token=([A-Za-z0-9_-]{43})/.exec(recebido.texto);
  expect(encontrado, `o e-mail "${recebido.assunto}" não tem link com token`).not.toBeNull();
  return encontrado?.[1] ?? '';
}
export interface UsuarioDaJornada {
  readonly nome: string;
  readonly email: string;
  readonly senha: string;
}

/**
 * Cria e confirma um usuário pela API (cadastro + link do Mailpit), para jornadas que
 * começam sem contas. Cada chamada conta 1 no limite de cadastros por IP (10 por hora).
 */
export async function criarUsuarioConfirmado(request: APIRequestContext, prefixo: string, nomeDoUsuario: string): Promise<UsuarioDaJornada> {
  const usuario: UsuarioDaJornada = { nome: nomeDoUsuario, email: emailUnico(prefixo), senha: 'senha-da-jornada-123' };
  const cadastro = await request.post('/api/auth/cadastrar', { data: { ...usuario, aceiteDosTermos: true } });
  expect(cadastro.status()).toBe(202);
  const [recebido] = await esperarEmails(request, usuario.email, 'Confirme seu e-mail no Flux');
  const confirmacao = await request.post('/api/auth/confirmar-email', { data: { token: tokenDoEmail(recebido) } });
  expect(confirmacao.status()).toBe(200);
  return usuario;
}

/** Navega pelo menu principal; no celular, abre a gaveta antes. */
export async function irPeloMenu(page: Page, item: 'Início' | 'Lançamentos' | 'Importação' | 'Contas' | 'Categorias' | 'Regras'): Promise<void> {
  if (!ehDesktop(page)) {
    await page.getByRole('button', { name: 'Abrir menu' }).click();
  }
  await page.getByRole('navigation', { name: 'Menu principal' }).getByRole('link', { name: item, exact: true }).click();
}
