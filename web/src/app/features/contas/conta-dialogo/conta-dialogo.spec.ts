import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { Conta } from '../contas.service';
import { ContaDialogo, DadosDoDialogoDeConta } from './conta-dialogo';

const conjunta: Conta = {
  id: 7, nome: 'Conta conjunta', tipo: 'CORRENTE', saldoInicial: -120, dataSaldoInicial: '2026-09-01',
  cor: 'ROSA', saldoAtual: -120, arquivadaEm: null,
};

describe('ContaDialogo', () => {
  let httpTesting: HttpTestingController;
  let referencia: MatDialogRef<ContaDialogo, Conta>;

  function abrir(conta: Conta | null): void {
    referencia = TestBed.inject(MatDialog).open<ContaDialogo, DadosDoDialogoDeConta, Conta>(ContaDialogo, {
      data: { conta, hoje: '2026-11-09' },
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

  function campo(seletor: string): HTMLInputElement {
    return painel().querySelector<HTMLInputElement>(seletor) as HTMLInputElement;
  }

  function digitar(seletor: string, valor: string): void {
    const entrada = campo(seletor);
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input'));
    entrada.dispatchEvent(new Event('blur'));
    TestBed.tick();
  }

  function enviar(): void {
    painel().querySelector<HTMLButtonElement>('button[type="submit"]')?.click();
    TestBed.tick();
  }

  it('abre a Nova conta com os padrões: corrente, R$ 0,00, hoje e sem cor', () => {
    abrir(null);
    expect(painel().querySelector('h2')?.textContent).toBe('Nova conta');
    expect(campo('#conta-nome').value).toBe('');
    expect(campo('#conta-saldo').value).toBe('0,00');
    expect(campo('#conta-data').value).toBe('2026-11-09');
    expect(campo('#conta-data').max).toBe('2026-11-09');
    const tipos = Array.from(painel().querySelectorAll<HTMLInputElement>('.flux-opcao-tipo input'));
    expect(tipos.map((radio) => radio.parentElement?.textContent?.trim())).toEqual(['Conta corrente', 'Poupança', 'Carteira', 'Investimento']);
    expect(tipos[0].checked).toBe(true);
    const cores = Array.from(painel().querySelectorAll<HTMLInputElement>('.flux-cor input'));
    expect(cores.map((radio) => radio.getAttribute('aria-label'))).toEqual(['Sem cor', 'Ouro', 'Laranja', 'Rosa', 'Roxo', 'Índigo', 'Azul', 'Ciano', 'Cinza']);
    expect(cores[0].checked).toBe(true);
    expect(painel().querySelector('#conta-saldo-dica')?.textContent).toBe('Negativo com "-" (cheque especial).');
    expect(painel().querySelector('button[type="submit"]')?.textContent?.trim()).toBe('Criar conta');
  });

  it('mostra os erros de cada campo e não chama a API', () => {
    abrir(null);
    digitar('#conta-saldo', '10,005');
    digitar('#conta-data', '2026-11-10');
    enviar();
    expect(painel().querySelector('#conta-nome-erro')?.textContent).toBe('Informe o nome.');
    expect(campo('#conta-nome').getAttribute('aria-invalid')).toBe('true');
    expect(painel().querySelector('#conta-saldo-erro')?.textContent).toBe('Use no máximo 2 casas decimais.');
    expect(painel().querySelector('#conta-data-erro')?.textContent).toBe('A data não pode ser depois de hoje.');
    digitar('#conta-nome', 'a'.repeat(61));
    digitar('#conta-saldo', '12.5');
    expect(painel().querySelector('#conta-nome-erro')?.textContent).toBe('Use no máximo 60 caracteres.');
    expect(painel().querySelector('#conta-saldo-erro')?.textContent).toBe('Digite um valor como 1.234,56.');
    digitar('#conta-saldo', '');
    expect(painel().querySelector('#conta-saldo-erro')?.textContent).toBe('Informe o saldo inicial.');
    digitar('#conta-saldo', '99999999999999');
    expect(painel().querySelector('#conta-saldo-erro')?.textContent).toBe('Use no máximo 13 dígitos antes da vírgula.');
    httpTesting.expectNone('/api/contas');
  });

  it('reescreve o saldo ao sair do campo e envia número, tipo, data e cor', () => {
    abrir(null);
    const fechar = vi.spyOn(referencia, 'close');
    digitar('#conta-nome', '  Banco Horizonte ');
    digitar('#conta-saldo', '4218,4');
    expect(campo('#conta-saldo').value).toBe('4.218,40');
    painel().querySelectorAll<HTMLInputElement>('.flux-cor input')[4].click();
    TestBed.tick();
    enviar();
    const requisicao = httpTesting.expectOne({ method: 'POST', url: '/api/contas' });
    expect(requisicao.request.body).toEqual({ nome: 'Banco Horizonte', tipo: 'CORRENTE', saldoInicial: 4218.4, dataSaldoInicial: '2026-11-09', cor: 'ROXO' });
    requisicao.flush({ ...conjunta, id: 9, nome: 'Banco Horizonte' });
    expect(fechar).toHaveBeenCalledWith(expect.objectContaining({ id: 9 }));
  });

  it('põe o nome repetido da API no campo e tira quando o nome muda', () => {
    abrir(null);
    digitar('#conta-nome', 'poupança');
    enviar();
    httpTesting.expectOne('/api/contas').flush(
      { detail: 'Já existe uma conta ativa com este nome.', codigo: 'NOME_EM_USO' },
      { status: 422, statusText: 'Entidade não processável' },
    );
    TestBed.tick();
    expect(painel().querySelector('#conta-nome-erro')?.textContent).toBe('Já existe uma conta ativa com este nome.');
    expect(painel().querySelector('[role="alert"]')).toBeNull();
    digitar('#conta-nome', 'Poupança 2');
    expect(painel().querySelector('#conta-nome-erro')).toBeNull();
  });

  it('põe os campos de um 400 da API nos campos e outros 422 no alerta', () => {
    abrir(null);
    digitar('#conta-nome', 'Amanhã');
    enviar();
    httpTesting.expectOne('/api/contas').flush(
      { campos: [{ campo: 'dataSaldoInicial', mensagem: 'A data não pode ser depois de hoje.' }] },
      { status: 400, statusText: 'Requisição inválida' },
    );
    TestBed.tick();
    expect(painel().querySelector('#conta-data-erro')?.textContent).toBe('A data não pode ser depois de hoje.');
    enviar();
    httpTesting.expectOne('/api/contas').flush(
      { detail: 'Você chegou ao limite de 50 contas.', codigo: 'LIMITE_DE_CONTAS' },
      { status: 422, statusText: 'Entidade não processável' },
    );
    TestBed.tick();
    expect(painel().querySelector('[role="alert"]')?.textContent).toContain('Você chegou ao limite de 50 contas.');
    enviar();
    httpTesting.expectOne('/api/contas').flush(null, { status: 500, statusText: 'Erro' });
    TestBed.tick();
    expect(painel().querySelector('[role="alert"]')?.textContent).toContain('Não foi possível salvar a conta. Tente novamente.');
  });

  it('durante o envio, não fecha por Esc, clique fora nem Cancelar; depois de um erro, volta a fechar', () => {
    abrir(null);
    const cancelar = Array.from(painel().querySelectorAll<HTMLButtonElement>('button')).find((botao) => botao.textContent?.trim() === 'Cancelar');
    digitar('#conta-nome', 'Banco Horizonte');
    enviar();
    const requisicao = httpTesting.expectOne('/api/contas');
    expect(referencia.disableClose).toBe(true);
    expect(cancelar?.disabled).toBe(true);
    requisicao.flush(null, { status: 500, statusText: 'Erro' });
    TestBed.tick();
    expect(referencia.disableClose).toBe(false);
    expect(cancelar?.disabled).toBe(false);
  });

  it('Cancelar fecha sem resultado e sem chamar a API', () => {
    abrir(null);
    const fechar = vi.spyOn(referencia, 'close');
    Array.from(painel().querySelectorAll<HTMLButtonElement>('button')).find((botao) => botao.textContent?.trim() === 'Cancelar')?.click();
    expect(fechar).toHaveBeenCalledWith();
    httpTesting.expectNone('/api/contas');
  });

  it('Editar conta preenche os campos e manda PUT', () => {
    abrir(conjunta);
    expect(painel().querySelector('h2')?.textContent).toBe('Editar conta');
    expect(campo('#conta-nome').value).toBe('Conta conjunta');
    expect(campo('#conta-saldo').value).toBe('−120,00');
    expect(campo('#conta-data').value).toBe('2026-09-01');
    expect(painel().querySelectorAll<HTMLInputElement>('.flux-cor input')[3].checked).toBe(true);
    expect(painel().querySelector('button[type="submit"]')?.textContent?.trim()).toBe('Salvar');
    enviar();
    const requisicao = httpTesting.expectOne({ method: 'PUT', url: '/api/contas/7' });
    expect(requisicao.request.body).toEqual({ nome: 'Conta conjunta', tipo: 'CORRENTE', saldoInicial: -120, dataSaldoInicial: '2026-09-01', cor: 'ROSA' });
    requisicao.flush(conjunta);
  });
});
