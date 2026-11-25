import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { detalheDoErro, statusDoErro } from '../auth/erros-http';
import { ExportacaoService, lerCorpoDoErro, salvarArquivo } from './exportacao.service';

describe('ExportacaoService', () => {
  let service: ExportacaoService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ExportacaoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('pede o arquivo com o formato e a senha no corpo e usa o nome do Content-Disposition', async () => {
    let arquivo: { nome: string; conteudo: Blob } | undefined;
    service.baixar('CSV', 'senha-certa-123', 'padrao.csv').subscribe((resultado) => { arquivo = resultado; });
    const requisicao = http.expectOne('/api/minha-conta/exportacao');
    expect(requisicao.request.method).toBe('POST');
    expect(requisicao.request.body).toEqual({ formato: 'CSV', senhaAtual: 'senha-certa-123' });
    expect(requisicao.request.responseType).toBe('blob');
    requisicao.flush(new Blob(['\uFEFFData;Descrição'], { type: 'text/csv;charset=UTF-8' }), {
      headers: { 'Content-Disposition': 'attachment; filename="flux-lancamentos-2026-11-23.csv"' },
    });
    if (arquivo === undefined) {
      throw new Error('arquivo não recebido');
    }
    expect(arquivo.nome).toBe('flux-lancamentos-2026-11-23.csv');
    const bytes = new Uint8Array(await arquivo.conteudo.arrayBuffer());
    expect([...bytes.slice(0, 3)]).toEqual([0xef, 0xbb, 0xbf]);
  });

  it('sem o cabeçalho, usa o nome padrão', () => {
    let nome = '';
    service.baixar('JSON', 'senha-certa-123', 'flux-dados-2026-11-24.json').subscribe((arquivo) => { nome = arquivo.nome; });
    http.expectOne('/api/minha-conta/exportacao').flush(new Blob(['{}']));
    expect(nome).toBe('flux-dados-2026-11-24.json');
  });

  it('o erro em Blob vira o ProblemDetail', async () => {
    const lido = await lerCorpoDoErro(new HttpErrorResponse({
      status: 422,
      error: new Blob([JSON.stringify({ detail: 'Senha atual incorreta.' })], { type: 'application/problem+json' }),
    }));
    expect(statusDoErro(lido)).toBe(422);
    expect(detalheDoErro(lido, 'padrão')).toBe('Senha atual incorreta.');
  });

  it('corpo de erro que não é JSON vira nulo', async () => {
    const lido = await lerCorpoDoErro(new HttpErrorResponse({ status: 500, error: new Blob(['falhou']) }));
    expect(statusDoErro(lido)).toBe(500);
    expect(detalheDoErro(lido, 'mensagem padrão')).toBe('mensagem padrão');
  });

  it('salvarArquivo clica num link com download e revoga a URL depois', () => {
    vi.useFakeTimers();
    const criarUrl = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:arquivo');
    const revogarUrl = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    const clique = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      expect(this.download).toBe('dados.json');
      expect(this.href).toBe('blob:arquivo');
    });
    const documento = document.implementation.createHTMLDocument('teste');

    salvarArquivo(documento, { nome: 'dados.json', conteudo: new Blob(['{}']) });

    expect(criarUrl).toHaveBeenCalledOnce();
    expect(clique).toHaveBeenCalledOnce();
    expect(documento.querySelector('a')).toBeNull();
    expect(revogarUrl).not.toHaveBeenCalled();
    vi.advanceTimersByTime(40_000);
    expect(revogarUrl).toHaveBeenCalledWith('blob:arquivo');
    clique.mockRestore();
    criarUrl.mockRestore();
    revogarUrl.mockRestore();
    vi.useRealTimers();
  });
});
