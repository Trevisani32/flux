import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { RecorrenciasService } from './recorrencias.service';

describe('RecorrenciasService', () => {
  let http: HttpTestingController;
  let servico: RecorrenciasService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    servico = TestBed.inject(RecorrenciasService);
  });

  afterEach(() => http.verify());

  it('cada método usa o método HTTP e a URL da API', () => {
    servico.listar().subscribe();
    http.expectOne({ method: 'GET', url: '/api/recorrencias' }).flush({});
    servico.buscar(7).subscribe();
    http.expectOne({ method: 'GET', url: '/api/recorrencias/7' }).flush({});
    servico.encerrar(7).subscribe();
    http.expectOne({ method: 'POST', url: '/api/recorrencias/7/encerrar' }).flush({});
    servico.retomar(7).subscribe();
    http.expectOne({ method: 'POST', url: '/api/recorrencias/7/retomar' }).flush({});
    servico.excluir(7).subscribe();
    http.expectOne({ method: 'DELETE', url: '/api/recorrencias/7' }).flush(null);
    servico.aConfirmar().subscribe();
    http.expectOne({ method: 'GET', url: '/api/recorrencias/a-confirmar' }).flush({});
    servico.historico(7).subscribe();
    http.expectOne({ method: 'GET', url: '/api/recorrencias/7/historico' }).flush({});
    servico.candidatos(7, '2026-11-01').subscribe();
    http.expectOne({ method: 'GET', url: '/api/recorrencias/7/ocorrencias/2026-11-01/candidatos' }).flush({});
    servico.pular(7, '2026-11-01').subscribe();
    http.expectOne({ method: 'POST', url: '/api/recorrencias/7/ocorrencias/2026-11-01/pular' }).flush({});
    servico.voltarAPrever(7, '2026-11-01').subscribe();
    http.expectOne({ method: 'POST', url: '/api/recorrencias/7/ocorrencias/2026-11-01/voltar-a-prever' }).flush(null);
  });

  it('a prévia, salvar, confirmar e ligar mandam o corpo', () => {
    const dados = {
      tipo: 'DESPESA' as const,
      descricao: 'Conta de luz Fictícia',
      valor: 189.9,
      valorVaria: true,
      categoriaId: 12,
      contaId: 1,
      cartaoId: null,
      frequencia: 'MENSAL' as const,
      diaDoMes: 30,
      comecaEm: '2026-11-25',
      terminaEm: null,
    };
    servico.criar(dados).subscribe();
    const criar = http.expectOne('/api/recorrencias');
    expect(criar.request.method).toBe('POST');
    expect(criar.request.body).toEqual(dados);
    criar.flush({});
    servico.editar(7, dados).subscribe();
    const editar = http.expectOne('/api/recorrencias/7');
    expect(editar.request.method).toBe('PUT');
    expect(editar.request.body).toEqual(dados);
    editar.flush({});
    const previa = {
      frequencia: 'MENSAL' as const,
      diaDoMes: 30,
      comecaEm: '2026-11-25',
      terminaEm: null,
      cartaoId: null,
      recorrenciaId: null,
    };
    servico.previa(previa).subscribe();
    const pedidoPrevia = http.expectOne('/api/recorrencias/previa');
    expect(pedidoPrevia.request.method).toBe('POST');
    expect(pedidoPrevia.request.body).toEqual(previa);
    pedidoPrevia.flush({});
    const confirmar = { data: '2026-11-30', valor: 201.37, observacao: null };
    servico.confirmar(7, '2026-11-01', confirmar).subscribe();
    const pedidoConfirmar = http.expectOne('/api/recorrencias/7/ocorrencias/2026-11-01/confirmar');
    expect(pedidoConfirmar.request.method).toBe('POST');
    expect(pedidoConfirmar.request.body).toEqual(confirmar);
    pedidoConfirmar.flush({});
    const ligar = { lancamentoId: 812, compraId: null };
    servico.ligar(7, '2026-11-01', ligar).subscribe();
    const pedidoLigar = http.expectOne('/api/recorrencias/7/ocorrencias/2026-11-01/ligar');
    expect(pedidoLigar.request.method).toBe('POST');
    expect(pedidoLigar.request.body).toEqual(ligar);
    pedidoLigar.flush({});
  });
});
