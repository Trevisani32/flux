import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ExcluirContaDialogo } from './excluir-conta-dialogo';

describe('ExcluirContaDialogo', () => {
  let httpTesting: HttpTestingController;
  let referencia: MatDialogRef<ExcluirContaDialogo, boolean>;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    httpTesting = TestBed.inject(HttpTestingController);
    referencia = TestBed.inject(MatDialog).open<ExcluirContaDialogo, { email: string }, boolean>(ExcluirContaDialogo, {
      data: { email: 'dev@flux.local' },
    });
    TestBed.tick();
  });

  afterEach(() => {
    httpTesting.verify();
    referencia.close();
  });

  function painel(): HTMLElement {
    return document.querySelector('.mat-mdc-dialog-container') as HTMLElement;
  }

  it('mostra o e-mail e só libera o botão com a senha preenchida', () => {
    expect(painel().querySelector('h2')?.textContent).toBe('Excluir sua conta?');
    expect(painel().textContent).toContain('o acesso com dev@flux.local;');
    const botao = painel().querySelector<HTMLButtonElement>('button[type="submit"]');
    expect(botao?.disabled).toBe(true);
    referencia.componentInstance.senha.setValue('senha-de-dev-123');
    TestBed.tick();
    expect(botao?.disabled).toBe(false);
  });

  it('mostra a senha incorreta no campo com 422', () => {
    referencia.componentInstance.senha.setValue('errada');
    referencia.componentInstance.excluir();
    const requisicao = httpTesting.expectOne({ method: 'DELETE', url: '/api/usuarios/me' });
    expect(requisicao.request.body).toEqual({ senhaAtual: 'errada' });
    requisicao.flush({ detail: 'Senha atual incorreta.' }, { status: 422, statusText: 'Entidade não processável' });
    TestBed.tick();
    const campo = painel().querySelector('#excluir-conta-senha');
    expect(campo?.getAttribute('aria-invalid')).toBe('true');
    expect(campo?.getAttribute('aria-describedby')).toBe('excluir-conta-senha-erro');
    expect(painel().querySelector('#excluir-conta-senha-erro')?.textContent).toBe('Senha atual incorreta.');
  });

  it('fecha com true depois do 204', () => {
    const fechar = vi.spyOn(referencia, 'close');
    referencia.componentInstance.senha.setValue('senha-de-dev-123');
    referencia.componentInstance.excluir();
    httpTesting.expectOne('/api/usuarios/me').flush(null, { status: 204, statusText: 'Sem conteúdo' });
    expect(fechar).toHaveBeenCalledWith(true);
  });

  it('o botão "Excluir definitivamente" envia o DELETE pelo formulário, sem submissão nativa', () => {
    const campo = painel().querySelector<HTMLInputElement>('#excluir-conta-senha');
    if (campo === null) {
      throw new Error('campo de senha ausente');
    }
    campo.value = 'senha-de-dev-123';
    campo.dispatchEvent(new Event('input'));
    TestBed.tick();
    let submissaoNativa = true;
    painel().querySelector('form')?.addEventListener('submit', (evento) => {
      submissaoNativa = !evento.defaultPrevented;
    });
    painel().querySelector<HTMLButtonElement>('button[type="submit"]')?.click();
    const requisicao = httpTesting.expectOne({ method: 'DELETE', url: '/api/usuarios/me' });
    expect(requisicao.request.body).toEqual({ senhaAtual: 'senha-de-dev-123' });
    expect(submissaoNativa).toBe(false);
    requisicao.flush(null, { status: 204, statusText: 'Sem conteúdo' });
  });

  it('tem o texto do desktop e o texto resumido do celular', () => {
    expect(painel().querySelector('.excluir__texto--desktop')?.textContent).toContain('o histórico de importações.');
    const paragrafos = Array.from(painel().querySelectorAll('.excluir__texto--celular p')).map((paragrafo) => paragrafo.textContent?.trim());
    expect(paragrafos).toEqual([
      'Isso apaga de forma definitiva o acesso com dev@flux.local e todos os seus dados: contas, lançamentos, cartões, categorias, regras, orçamentos e importações.',
      'Não dá para desfazer.',
    ]);
  });
});
