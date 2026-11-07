import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it } from 'vitest';

import { ContaPublicaService } from './conta-publica.service';

describe('ContaPublicaService', () => {
  let service: ContaPublicaService;
  let http: HttpTestingController;

  afterEach(() => http.verify());

  function criar(): void {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ContaPublicaService);
    http = TestBed.inject(HttpTestingController);
  }

  it('cadastra com o corpo exato', () => {
    criar();
    service.cadastrar({ nome: 'Ana', email: 'ana@teste.local', senha: 'senha-de-teste-123', aceiteDosTermos: true }).subscribe();
    const requisicao = http.expectOne('/api/auth/cadastrar');
    expect(requisicao.request.method).toBe('POST');
    expect(requisicao.request.body).toEqual({ nome: 'Ana', email: 'ana@teste.local', senha: 'senha-de-teste-123', aceiteDosTermos: true });
    requisicao.flush(null);
  });

  it('reenvia confirmação com o corpo exato', () => {
    criar();
    service.reenviarConfirmacao('ana@teste.local').subscribe();
    const requisicao = http.expectOne({ method: 'POST', url: '/api/auth/reenviar-confirmacao' });
    expect(requisicao.request.body).toEqual({ email: 'ana@teste.local' });
    requisicao.flush(null);
  });

  it('pede redefinição com o corpo exato', () => {
    criar();
    service.esqueciASenha('ana@teste.local').subscribe();
    const requisicao = http.expectOne({ method: 'POST', url: '/api/auth/esqueci-a-senha' });
    expect(requisicao.request.body).toEqual({ email: 'ana@teste.local' });
    requisicao.flush(null);
  });

  it('redefine senha com o corpo exato', () => {
    criar();
    service.redefinirSenha('abc', 'senha-nova-123').subscribe();
    const requisicao = http.expectOne({ method: 'POST', url: '/api/auth/redefinir-senha' });
    expect(requisicao.request.body).toEqual({ token: 'abc', novaSenha: 'senha-nova-123' });
    requisicao.flush(null);
  });

  it('confirma email e devolve a resposta', () => {
    criar();
    let resposta: unknown;
    service.confirmarEmail('abc').subscribe((valor) => resposta = valor);
    const requisicao = http.expectOne({ method: 'POST', url: '/api/auth/confirmar-email' });
    expect(requisicao.request.body).toEqual({ token: 'abc' });
    requisicao.flush({ tipo: 'CADASTRO', email: 'ana@teste.local' });
    expect(resposta).toEqual({ tipo: 'CADASTRO', email: 'ana@teste.local' });
  });
});
