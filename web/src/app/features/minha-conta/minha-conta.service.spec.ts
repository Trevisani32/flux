import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it } from 'vitest';

import { MinhaContaService } from './minha-conta.service';

describe('MinhaContaService', () => {
  let service: MinhaContaService;
  let http: HttpTestingController;

  afterEach(() => http.verify());

  function criar(): void {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(MinhaContaService);
    http = TestBed.inject(HttpTestingController);
  }

  it('carrega a conta atual', () => {
    criar(); service.carregar().subscribe();
    const req = http.expectOne('/api/usuarios/me'); expect(req.request.method).toBe('GET'); req.flush({});
  });

  it('renomeia com o corpo exato', () => {
    criar(); service.renomear('Ana').subscribe();
    const req = http.expectOne('/api/usuarios/me/nome'); expect(req.request.method).toBe('PUT'); expect(req.request.body).toEqual({ nome: 'Ana' }); req.flush({});
  });

  it('pede troca de email com o corpo exato', () => {
    criar(); service.pedirTrocaDeEmail('novo@teste.local', 'senha').subscribe();
    const req = http.expectOne('/api/usuarios/me/email'); expect(req.request.method).toBe('POST'); expect(req.request.body).toEqual({ novoEmail: 'novo@teste.local', senhaAtual: 'senha' }); req.flush({});
  });

  it('exclui com a senha no corpo', () => {
    criar(); service.excluir('senha').subscribe();
    const req = http.expectOne('/api/usuarios/me'); expect(req.request.method).toBe('DELETE'); expect(req.request.body).toEqual({ senhaAtual: 'senha' }); req.flush(null);
  });
});
