import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { RegrasService } from './regras.service';

describe('RegrasService', () => {
  let servico: RegrasService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servico = TestBed.inject(RegrasService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('lista, cria, edita, exclui e reordena pelas rotas de /api/regras (decisão 39)', () => {
    const dados = { condicao: 'CONTEM' as const, texto: 'uber', contaId: null, categoriaId: 5, descricao: 'Uber' };
    servico.listar().subscribe();
    httpTesting.expectOne({ method: 'GET', url: '/api/regras' }).flush([]);
    servico.criar(dados).subscribe();
    expect(httpTesting.expectOne({ method: 'POST', url: '/api/regras' }).request.body).toEqual(dados);
    servico.atualizar(31, dados).subscribe();
    expect(httpTesting.expectOne({ method: 'PUT', url: '/api/regras/31' }).request.body).toEqual(dados);
    let excluida = false;
    servico.excluir(31).subscribe(() => (excluida = true));
    httpTesting.expectOne({ method: 'DELETE', url: '/api/regras/31' }).flush(null);
    expect(excluida).toBe(true);
    servico.reordenar([32, 31]).subscribe();
    expect(httpTesting.expectOne({ method: 'PUT', url: '/api/regras/ordem' }).request.body).toEqual({ ids: [32, 31] });
  });

  it('prévia, aplicação (com e sem a regra), aplicar e sugestões', () => {
    const pedido = { condicao: 'COMECA_COM' as const, texto: 'rend', contaId: 3, categoriaId: null };
    servico.previa(pedido).subscribe();
    expect(httpTesting.expectOne({ method: 'POST', url: '/api/regras/previa' }).request.body).toEqual(pedido);
    servico.aplicacao(null).subscribe();
    expect(httpTesting.expectOne({ method: 'GET', url: '/api/regras/aplicacao' }).request.params.has('regra')).toBe(false);
    servico.aplicacao(31).subscribe();
    expect(httpTesting.expectOne((req) => req.url === '/api/regras/aplicacao').request.params.get('regra')).toBe('31');
    let categorizados = -1;
    servico.aplicar([31, 32]).subscribe((quantos) => (categorizados = quantos));
    const aplicar = httpTesting.expectOne({ method: 'POST', url: '/api/regras/aplicacao' });
    expect(aplicar.request.body).toEqual({ regras: [31, 32] });
    aplicar.flush({ categorizados: 21 });
    expect(categorizados).toBe(21);
    const itens = [{ contaId: 1, descricaoBanco: 'PADARIA', descricao: null, categoriaId: 2 }];
    servico.sugestoes(itens).subscribe();
    expect(httpTesting.expectOne({ method: 'POST', url: '/api/regras/sugestoes' }).request.body).toEqual({ itens });
  });
});
