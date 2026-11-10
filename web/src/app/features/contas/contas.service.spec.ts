import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { ContasService, DadosDaConta } from './contas.service';

describe('ContasService', () => {
  let servico: ContasService;
  let httpTesting: HttpTestingController;
  const dados: DadosDaConta = { nome: 'Poupança', tipo: 'POUPANCA', saldoInicial: 3902.05, dataSaldoInicial: '2026-10-01', cor: 'CIANO' };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servico = TestBed.inject(ContasService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('usa os endereços e os métodos da API de contas', () => {
    servico.listar().subscribe();
    httpTesting.expectOne({ method: 'GET', url: '/api/contas' }).flush({ ativas: [], arquivadas: [], totais: { geral: 0, disponivel: 0, investimentos: 0 } });

    servico.criar(dados).subscribe();
    const criar = httpTesting.expectOne({ method: 'POST', url: '/api/contas' });
    expect(criar.request.body).toEqual(dados);
    criar.flush({});

    servico.atualizar(7, dados).subscribe();
    expect(httpTesting.expectOne({ method: 'PUT', url: '/api/contas/7' }).request.body).toEqual(dados);

    servico.arquivar(7).subscribe();
    httpTesting.expectOne({ method: 'POST', url: '/api/contas/7/arquivar' }).flush({});
    servico.reativar(7).subscribe();
    httpTesting.expectOne({ method: 'POST', url: '/api/contas/7/reativar' }).flush({});

    let excluida = false;
    servico.excluir(7).subscribe(() => (excluida = true));
    httpTesting.expectOne({ method: 'DELETE', url: '/api/contas/7' }).flush(null, { status: 204, statusText: 'Sem conteúdo' });
    expect(excluida).toBe(true);
  });
});
