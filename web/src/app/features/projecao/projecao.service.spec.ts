import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it } from 'vitest';

import { ProjecaoService } from './projecao.service';

describe('ProjecaoService', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('projetar manda ate e contas só quando vêm', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const servico = TestBed.inject(ProjecaoService);
    const http = TestBed.inject(HttpTestingController);
    servico.projetar(null, null).subscribe();
    const padrao = http.expectOne('/api/projecao');
    expect(padrao.request.method).toBe('GET');
    padrao.flush({});
    servico.projetar('2027-02-25', [1, 4]).subscribe();
    const comTudo = http.expectOne('/api/projecao?ate=2027-02-25&contas=1,4');
    expect(comTudo.request.params.keys()).toEqual(['ate', 'contas']);
    comTudo.flush({});
    servico.projetar(null, []).subscribe();
    const vazio = http.expectOne('/api/projecao?contas=');
    expect(vazio.request.params.get('contas')).toBe('');
    vazio.flush({});
  });

  it('aviso usa GET /api/projecao/aviso', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const servico = TestBed.inject(ProjecaoService);
    const http = TestBed.inject(HttpTestingController);
    servico.aviso().subscribe();
    const pedido = http.expectOne('/api/projecao/aviso');
    expect(pedido.request.method).toBe('GET');
    expect(pedido.request.params.keys()).toEqual([]);
    pedido.flush({ contas: [] });
  });
});
