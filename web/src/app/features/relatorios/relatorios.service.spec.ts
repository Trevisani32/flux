import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it } from 'vitest';

import { RelatoriosService } from './relatorios.service';

describe('RelatoriosService', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('pede o relatório do mês, e sem mês não manda o parâmetro', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const servico = TestBed.inject(RelatoriosService);
    const http = TestBed.inject(HttpTestingController);
    servico.doMes('2026-10').subscribe();
    const pedido = http.expectOne('/api/relatorios/mes?mes=2026-10');
    expect(pedido.request.method).toBe('GET');
    pedido.flush({});
    servico.doMes(null).subscribe();
    http.expectOne('/api/relatorios/mes').flush({});
  });
});
