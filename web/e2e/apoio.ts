import { expect, type Locator, type Page } from '@playwright/test';

export const email = process.env['E2E_EMAIL'] ?? 'dev@flux.local';
export const senha = process.env['E2E_SENHA'] ?? 'senha-de-dev-123';
export const nome = process.env['E2E_NOME'] ?? 'Dev';
/** O e-mail como a API devolve (minúsculas, sem espaços nas pontas). */
export const emailExibido = email.trim().toLowerCase();

export async function entrar(page: Page): Promise<void> {
  await page.goto('/entrar');
  await page.getByLabel('E-mail', { exact: true }).fill(email);
  await page.getByLabel('Senha', { exact: true }).fill(senha);
  await page.getByRole('button', { name: 'Entrar' }).click();
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
export const nomeDoBotaoDoUsuario = new RegExp(`^${escaparRegex(nome)}(\\s|$)`);

export function botaoDoUsuario(page: Page): Locator {
  return page.getByRole('button', { name: nomeDoBotaoDoUsuario });
}

export async function semRolagemHorizontal(page: Page): Promise<boolean> {
  return page.evaluate(() => [
    document.documentElement,
    document.body,
    ...Array.from(document.querySelectorAll('mat-sidenav-content, main')),
  ].every((elemento) => elemento.scrollWidth <= elemento.clientWidth));
}
