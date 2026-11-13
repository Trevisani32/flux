import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { CartoesService } from './cartoes.service';
import { ComprasService } from './compras.service';

describe('CartoesService e ComprasService', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('chama os endereços dos cartões e das faturas (decisão 50 da 05a)', () => {
    const cartoes = TestBed.inject(CartoesService);
    cartoes.listar().subscribe();
    http.expectOne({ method: 'GET', url: '/api/cartoes' }).flush({ ativos: [], arquivados: [] });
    let troca: unknown = 'nada';
    cartoes.previaDosDias(2, 13, 20).subscribe((resposta) => (troca = resposta));
    const previa = http.expectOne({ method: 'POST', url: '/api/cartoes/2/previa-dos-dias' });
    expect(previa.request.body).toEqual({ diaFechamento: 13, diaVencimento: 20 });
    previa.flush({ troca: null, valemJa: true });
    expect(troca).toEqual({ troca: null, valemJa: true });
    cartoes.fatura(2, '2026-12').subscribe();
    http.expectOne({ method: 'GET', url: '/api/cartoes/2/faturas/2026-12' }).flush({});
    cartoes.ajustarFechamento(2, '2026-12', '2026-12-04').subscribe();
    const ajuste = http.expectOne({ method: 'PUT', url: '/api/cartoes/2/faturas/2026-12/fechamento' });
    expect(ajuste.request.body).toEqual({ data: '2026-12-04' });
    ajuste.flush({});
    cartoes.voltarAoFechamentoNormal(2, '2026-12').subscribe();
    http.expectOne({ method: 'DELETE', url: '/api/cartoes/2/faturas/2026-12/fechamento' }).flush({});
    cartoes.arquivar(2).subscribe();
    http.expectOne({ method: 'POST', url: '/api/cartoes/2/arquivar' }).flush({});
    cartoes.excluir(2).subscribe();
    http.expectOne({ method: 'DELETE', url: '/api/cartoes/2' }).flush(null);
  });

  it('chama os endereços das compras e dos estornos', () => {
    const compras = TestBed.inject(ComprasService);
    compras.buscar('COMPRA', 5).subscribe();
    http.expectOne({ method: 'GET', url: '/api/compras/5' }).flush({});
    compras.buscar('ESTORNO', 6).subscribe();
    http.expectOne({ method: 'GET', url: '/api/estornos/6' }).flush({});
    compras.previa({ cartaoId: 2, data: '2026-11-09', valorTotal: 1990, parcelas: 10, parcelaInicial: 1 }).subscribe();
    http.expectOne({ method: 'POST', url: '/api/compras/previa' }).flush({ parcelas: [], primeiraFatura: null });
    compras.excluir('ESTORNO', 6).subscribe();
    http.expectOne({ method: 'DELETE', url: '/api/estornos/6' }).flush(null);
    compras.excluir('COMPRA', 5).subscribe();
    http.expectOne({ method: 'DELETE', url: '/api/compras/5' }).flush(null);
  });
});
