import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it } from 'vitest';

import { CONFIGURACAO_PADRAO, ConfiguracaoPublica } from './configuracao';
import { ConfiguracaoService } from './configuracao.service';

describe('ConfiguracaoService', () => {
  let http: HttpTestingController;

  afterEach(() => http.verify());

  function criar(): ConfiguracaoService {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    return TestBed.inject(ConfiguracaoService);
  }

  const resposta: ConfiguracaoPublica = {
    cadastroPublico: true,
    controlador: { nome: 'Responsável', email: 'privacidade@teste.local' },
    versaoDosTermos: '2026-11-06',
  };

  it('carregar guarda a resposta e expõe cadastro público ligado', async () => {
    const service = criar();
    const promessa = service.carregar();
    http.expectOne('/api/publico/configuracao').flush(resposta);

    await expect(promessa).resolves.toEqual(resposta);
    expect(service.configuracao()).toEqual(resposta);
    expect(service.cadastroPublico()).toBe(true);
  });

  it('carregar resolve no padrão quando a API falha', async () => {
    const service = criar();
    const promessa = service.carregar();
    http.expectOne('/api/publico/configuracao').flush(null, { status: 500, statusText: 'Erro' });

    await expect(promessa).resolves.toEqual(CONFIGURACAO_PADRAO);
    expect(service.cadastroPublico()).toBe(false);
  });

  it('garantir tenta novamente depois de uma falha', async () => {
    const service = criar();
    const primeira = service.garantir();
    http.expectOne('/api/publico/configuracao').flush(null, { status: 500, statusText: 'Erro' });
    await primeira;
    const segunda = service.garantir();
    http.expectOne('/api/publico/configuracao').flush(resposta);

    await expect(segunda).resolves.toEqual(resposta);
  });

  it('garantir não consulta de novo depois de um sucesso', async () => {
    const service = criar();
    const primeira = service.garantir();
    http.expectOne('/api/publico/configuracao').flush(resposta);
    await primeira;

    await expect(service.garantir()).resolves.toEqual(resposta);
    http.expectNone('/api/publico/configuracao');
  });
});
