import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { CategoriasService } from './categorias.service';

describe('CategoriasService', () => {
  let servico: CategoriasService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servico = TestBed.inject(CategoriasService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('usa os endereços e os métodos da API de categorias', () => {
    servico.listar().subscribe();
    httpTesting.expectOne({ method: 'GET', url: '/api/categorias' }).flush({});

    servico.criar({ nome: 'Padaria', natureza: 'DESPESA', categoriaMaeId: 3 }).subscribe();
    expect(httpTesting.expectOne({ method: 'POST', url: '/api/categorias' }).request.body)
      .toEqual({ nome: 'Padaria', natureza: 'DESPESA', categoriaMaeId: 3 });

    servico.atualizar(9, { nome: 'Restaurantes', categoriaMaeId: null }).subscribe();
    expect(httpTesting.expectOne({ method: 'PUT', url: '/api/categorias/9' }).request.body)
      .toEqual({ nome: 'Restaurantes', categoriaMaeId: null });

    servico.arquivar(9).subscribe();
    httpTesting.expectOne({ method: 'POST', url: '/api/categorias/9/arquivar' }).flush({});
    servico.reativar(9).subscribe();
    httpTesting.expectOne({ method: 'POST', url: '/api/categorias/9/reativar' }).flush({});
    let excluida = false;
    servico.excluir(9).subscribe(() => (excluida = true));
    httpTesting.expectOne({ method: 'DELETE', url: '/api/categorias/9' }).flush(null, { status: 204, statusText: 'Sem conteúdo' });
    expect(excluida).toBe(true);
  });
});
