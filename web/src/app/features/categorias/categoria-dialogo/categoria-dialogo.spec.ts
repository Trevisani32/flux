import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { Categoria } from '../categorias.service';
import { CategoriaDialogo, DadosDoDialogoDeCategoria } from './categoria-dialogo';

function categoria(parcial: Partial<Categoria>): Categoria {
  return { id: 1, nome: 'Categoria', natureza: 'DESPESA', categoriaMaeId: null, nomeDaMae: null, arquivadaEm: null, subcategorias: [], ...parcial };
}

const alimentacao = categoria({ id: 1, nome: 'Alimentação' });
const lazer = categoria({ id: 2, nome: 'Lazer' });
const salario = categoria({ id: 3, nome: 'Salário', natureza: 'RECEITA' });
const restaurante = categoria({ id: 4, nome: 'Restaurante', categoriaMaeId: 1, nomeDaMae: 'Alimentação' });
const principais = { DESPESA: [alimentacao, lazer], RECEITA: [salario] };

describe('CategoriaDialogo', () => {
  let httpTesting: HttpTestingController;
  let referencia: MatDialogRef<CategoriaDialogo, Categoria>;

  function abrir(dados: Partial<DadosDoDialogoDeCategoria>): void {
    referencia = TestBed.inject(MatDialog).open<CategoriaDialogo, DadosDoDialogoDeCategoria, Categoria>(CategoriaDialogo, {
      data: { categoria: null, natureza: 'DESPESA', maeId: null, principais, temSubcategorias: false, ...dados },
    });
    TestBed.tick();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    referencia.close();
  });

  function painel(): HTMLElement {
    return document.querySelector('.mat-mdc-dialog-container') as HTMLElement;
  }

  function opcoes(): string[] {
    return Array.from(painel().querySelectorAll('#categoria-mae option')).map((opcao) => opcao.textContent ?? '');
  }

  function digitarNome(valor: string): void {
    const entrada = painel().querySelector<HTMLInputElement>('#categoria-nome') as HTMLInputElement;
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input'));
    entrada.dispatchEvent(new Event('blur'));
    TestBed.tick();
  }

  function enviar(): void {
    painel().querySelector<HTMLButtonElement>('button[type="submit"]')?.click();
    TestBed.tick();
  }

  it('Nova categoria lista só as principais da natureza escolhida e limpa "Dentro de" ao trocar', () => {
    abrir({ maeId: 1 });
    expect(painel().querySelector('h2')?.textContent).toBe('Nova categoria');
    expect(opcoes()).toEqual(['Nenhuma (categoria principal)', 'Alimentação', 'Lazer']);
    expect(painel().querySelector<HTMLSelectElement>('#categoria-mae')?.selectedIndex).toBe(1);
    painel().querySelector<HTMLInputElement>('input[value="RECEITA"]')?.click();
    TestBed.tick();
    expect(opcoes()).toEqual(['Nenhuma (categoria principal)', 'Salário']);
    expect(referencia.componentInstance.formulario.controls.maeId.value).toBeNull();
    expect(painel().querySelector('#categoria-mae-dica')?.textContent).toBe('Escolha uma categoria para criar uma subcategoria.');
  });

  it('cria a subcategoria com a natureza e a principal', () => {
    abrir({ maeId: 1 });
    const fechar = vi.spyOn(referencia, 'close');
    digitarNome('  Padaria ');
    enviar();
    const requisicao = httpTesting.expectOne({ method: 'POST', url: '/api/categorias' });
    expect(requisicao.request.body).toEqual({ nome: 'Padaria', natureza: 'DESPESA', categoriaMaeId: 1 });
    requisicao.flush(categoria({ id: 9, nome: 'Padaria', categoriaMaeId: 1 }));
    expect(fechar).toHaveBeenCalledWith(expect.objectContaining({ id: 9 }));
  });

  it('valida o nome e mostra o nome em uso da API no campo', () => {
    abrir({});
    enviar();
    expect(painel().querySelector('#categoria-nome-erro')?.textContent).toBe('Informe o nome.');
    digitarNome('a'.repeat(61));
    expect(painel().querySelector('#categoria-nome-erro')?.textContent).toBe('Use no máximo 60 caracteres.');
    digitarNome('mercado');
    enviar();
    httpTesting.expectOne('/api/categorias').flush(
      { detail: 'Já existe uma categoria ativa com este nome entre as categorias principais.', codigo: 'NOME_EM_USO' },
      { status: 422, statusText: 'Entidade não processável' },
    );
    TestBed.tick();
    expect(painel().querySelector('#categoria-nome-erro')?.textContent).toBe('Já existe uma categoria ativa com este nome entre as categorias principais.');
    expect(painel().querySelector('#categoria-nome')?.getAttribute('aria-invalid')).toBe('true');
  });

  it('durante o envio, não fecha por Esc, clique fora nem Cancelar; depois de um erro, volta a fechar', () => {
    abrir({});
    const cancelar = Array.from(painel().querySelectorAll<HTMLButtonElement>('button')).find((botao) => botao.textContent?.trim() === 'Cancelar');
    digitarNome('Pets');
    enviar();
    const requisicao = httpTesting.expectOne('/api/categorias');
    expect(referencia.disableClose).toBe(true);
    expect(cancelar?.disabled).toBe(true);
    requisicao.flush(null, { status: 500, statusText: 'Erro' });
    TestBed.tick();
    expect(referencia.disableClose).toBe(false);
    expect(cancelar?.disabled).toBe(false);
  });

  it('Cancelar fecha sem resultado e sem chamar a API', () => {
    abrir({});
    const fechar = vi.spyOn(referencia, 'close');
    Array.from(painel().querySelectorAll<HTMLButtonElement>('button')).find((botao) => botao.textContent?.trim() === 'Cancelar')?.click();
    expect(fechar).toHaveBeenCalledWith();
    httpTesting.expectNone('/api/categorias');
  });

  it('mostra outras recusas da API no alerta', () => {
    abrir({ maeId: 1 });
    digitarNome('X');
    enviar();
    httpTesting.expectOne('/api/categorias').flush(
      { detail: 'Escolha uma categoria principal ativa.' },
      { status: 422, statusText: 'Entidade não processável' },
    );
    TestBed.tick();
    expect(painel().querySelector('[role="alert"]')?.textContent).toContain('Escolha uma categoria principal ativa.');
  });

  it('Editar trava a natureza, tira a própria categoria das opções e manda PUT', () => {
    abrir({ categoria: restaurante, natureza: 'DESPESA', maeId: 1 });
    expect(painel().querySelector('h2')?.textContent).toBe('Editar categoria');
    expect(painel().querySelector<HTMLInputElement>('input[value="DESPESA"]')?.disabled).toBe(true);
    expect(painel().querySelector('#categoria-natureza-dica')?.textContent).toBe('A natureza não muda depois de criada.');
    expect(painel().querySelector<HTMLInputElement>('#categoria-nome')?.value).toBe('Restaurante');
    digitarNome('Restaurantes');
    enviar();
    const requisicao = httpTesting.expectOne({ method: 'PUT', url: '/api/categorias/4' });
    expect(requisicao.request.body).toEqual({ nome: 'Restaurantes', categoriaMaeId: 1 });
    requisicao.flush(restaurante);
  });

  it('principal com subcategorias não escolhe "Dentro de" e não aparece nas próprias opções', () => {
    abrir({ categoria: alimentacao, natureza: 'DESPESA', maeId: null, temSubcategorias: true });
    expect(opcoes()).toEqual(['Nenhuma (categoria principal)', 'Lazer']);
    expect(painel().querySelector<HTMLSelectElement>('#categoria-mae')?.disabled).toBe(true);
    expect(painel().querySelector('#categoria-mae-dica')?.textContent).toBe('Uma categoria com subcategorias não pode ficar dentro de outra.');
    enviar();
    expect(httpTesting.expectOne({ method: 'PUT', url: '/api/categorias/1' }).request.body).toEqual({ nome: 'Alimentação', categoriaMaeId: null });
  });
});
