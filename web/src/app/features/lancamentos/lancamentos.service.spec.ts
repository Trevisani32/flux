import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { LancamentosService } from './lancamentos.service';

describe('LancamentosService', () => {
  let servico: LancamentosService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servico = TestBed.inject(LancamentosService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('lista o mês com os filtros que têm valor', () => {
    servico.listar({ mes: '2026-10', conta: 3, categoria: 'sem', tipo: null, busca: 'mercado bom' }).subscribe();
    const pedido = httpTesting.expectOne((req) => req.url === '/api/lancamentos');
    expect(pedido.request.urlWithParams).toBe('/api/lancamentos?mes=2026-10&conta=3&categoria=sem&busca=mercado%20bom');
    pedido.flush({});
  });

  it('usa as rotas de lançamentos e de transferências', () => {
    servico.ultimos().subscribe();
    servico.excluirLancamento(7).subscribe();
    servico.atualizarTransferencia(4, { valor: 1, data: '2026-11-05', contaOrigemId: 1, contaDestinoId: 2, descricao: null, observacao: null }).subscribe();
    servico.excluirTransferencia(4).subscribe();
    httpTesting.expectOne({ method: 'GET', url: '/api/lancamentos/ultimos' }).flush([]);
    httpTesting.expectOne({ method: 'DELETE', url: '/api/lancamentos/7' }).flush(null);
    httpTesting.expectOne({ method: 'PUT', url: '/api/transferencias/4' }).flush({});
    httpTesting.expectOne({ method: 'DELETE', url: '/api/transferencias/4' }).flush(null);
  });
});
