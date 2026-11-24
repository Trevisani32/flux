import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { LancamentosService } from '../lancamentos/lancamentos.service';
import { FormatoCsv, ImportacaoService } from './importacao.service';

const NUBANK: FormatoCsv = {
  separador: 'VIRGULA', linhasDeCabecalho: 1, colunaData: 1, formatoData: 'DD_MM_AAAA', colunaDescricao: 4, colunaValor: 2,
  colunaSaidas: null, colunaIdentificador: 3, separadorDecimal: 'PONTO',
};

describe('ImportacaoService', () => {
  let servico: ImportacaoService;
  let httpTesting: HttpTestingController;
  const arquivo = new File(['OFXHEADER:100'], 'extrato.ofx');

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servico = TestBed.inject(ImportacaoService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('a revisão manda o arquivo e a conta num multipart; na fatura (Fatia 05c), o cartão e a fatura escolhida', () => {
    servico.previa(arquivo, { tipo: 'conta', id: 3 }).subscribe();
    const pedido = httpTesting.expectOne({ method: 'POST', url: '/api/importacoes/previa' });
    const corpo = pedido.request.body as FormData;
    expect((corpo.get('arquivo') as File).name).toBe('extrato.ofx');
    expect(corpo.get('contaId')).toBe('3');
    expect(corpo.has('cartaoId')).toBe(false);
    expect(corpo.has('fatura')).toBe(false);
    pedido.flush({});

    servico.previa(arquivo, { tipo: 'cartao', id: 2 }).subscribe();
    const sugerida = httpTesting.expectOne({ method: 'POST', url: '/api/importacoes/previa' }).request.body as FormData;
    expect(sugerida.get('cartaoId')).toBe('2');
    expect(sugerida.has('contaId')).toBe(false);
    expect(sugerida.has('fatura')).toBe(false);
    servico.previa(arquivo, { tipo: 'cartao', id: 2 }, '2026-12').subscribe();
    expect((httpTesting.expectOne({ method: 'POST', url: '/api/importacoes/previa' }).request.body as FormData).get('fatura')).toBe('2026-12');
  });

  it('a confirmação manda o arquivo de novo e a parte JSON "pedido", com a descrição de cada linha (decisão 13; 04b, decisão 15)', async () => {
    servico.confirmar(arquivo, { tipo: 'conta', id: 3 }, 'abc', [{ linha: 2, categoriaId: 9, descricao: 'Uber' },
      { linha: 3, categoriaId: null, descricao: null }]).subscribe();
    const pedido = httpTesting.expectOne({ method: 'POST', url: '/api/importacoes' });
    const corpo = pedido.request.body as FormData;
    expect((corpo.get('arquivo') as File).name).toBe('extrato.ofx');
    const parte = corpo.get('pedido') as Blob;
    expect(parte.type).toBe('application/json');
    expect(JSON.parse(await parte.text())).toEqual({ contaId: 3, hash: 'abc',
      linhas: [{ linha: 2, categoriaId: 9, descricao: 'Uber' }, { linha: 3, categoriaId: null, descricao: null }] });
    pedido.flush({});
  });

  it('a confirmação da fatura (Fatia 05c) manda o cartão e a fatura no lugar da conta', async () => {
    servico.confirmar(arquivo, { tipo: 'cartao', id: 2 }, 'abc', [{ linha: 2, categoriaId: null, descricao: null }], '2026-12').subscribe();
    const pedido = httpTesting.expectOne({ method: 'POST', url: '/api/importacoes' });
    expect(JSON.parse(await ((pedido.request.body as FormData).get('pedido') as Blob).text())).toEqual({ cartaoId: 2, fatura: '2026-12',
      hash: 'abc', linhas: [{ linha: 2, categoriaId: null, descricao: null }] });
    pedido.flush({});
  });

  it('a amostra leva o formato só quando há um; perfil, histórico, desfazer e excluídos usam as rotas da API', async () => {
    servico.amostra(arquivo, null).subscribe();
    const semFormato = httpTesting.expectOne('/api/importacoes/amostra').request.body as FormData;
    expect(semFormato.has('formato')).toBe(false);
    expect(semFormato.has('cartao')).toBe(false);
    servico.amostra(arquivo, NUBANK).subscribe();
    const comFormato = httpTesting.expectOne('/api/importacoes/amostra').request.body as FormData;
    expect(JSON.parse(await (comFormato.get('formato') as Blob).text())).toEqual(NUBANK);
    servico.perfil({ tipo: 'conta', id: 3 }).subscribe();
    httpTesting.expectOne({ method: 'GET', url: '/api/importacoes/perfis/3' }).flush(NUBANK);
    servico.salvarPerfil({ tipo: 'conta', id: 3 }, NUBANK).subscribe();
    expect(httpTesting.expectOne({ method: 'PUT', url: '/api/importacoes/perfis/3' }).request.body).toEqual(NUBANK);
    servico.listar().subscribe();
    httpTesting.expectOne({ method: 'GET', url: '/api/importacoes' }).flush([]);
    servico.desfazer(12).subscribe();
    httpTesting.expectOne({ method: 'POST', url: '/api/importacoes/12/desfazer' }).flush({});
    servico.excluidos(12).subscribe();
    httpTesting.expectOne({ method: 'GET', url: '/api/importacoes/12/excluidos' }).flush([]);
  });

  it('o perfil e a amostra do cartão (Fatia 05c) usam as rotas do cartão e o parâmetro "cartao"', () => {
    servico.amostra(arquivo, null, true).subscribe();
    expect((httpTesting.expectOne('/api/importacoes/amostra').request.body as FormData).get('cartao')).toBe('true');
    servico.perfil({ tipo: 'cartao', id: 2 }).subscribe();
    httpTesting.expectOne({ method: 'GET', url: '/api/importacoes/perfis/cartao/2' }).flush(NUBANK);
    servico.salvarPerfil({ tipo: 'cartao', id: 2 }, { ...NUBANK, comprasPositivas: true }).subscribe();
    expect(httpTesting.expectOne({ method: 'PUT', url: '/api/importacoes/perfis/cartao/2' }).request.body)
      .toEqual({ ...NUBANK, comprasPositivas: true });
  });

  it('restaurar um importado manda a categoria e avisa as telas pelo restaurado$', () => {
    const lancamentos = TestBed.inject(LancamentosService);
    let avisos = 0;
    lancamentos.restaurado$.subscribe(() => avisos++);
    lancamentos.restaurarLancamento(10, 5).subscribe();
    const pedido = httpTesting.expectOne({ method: 'POST', url: '/api/lancamentos/10/restaurar' });
    expect(pedido.request.body).toEqual({ categoriaId: 5 });
    expect(avisos).toBe(0);
    pedido.flush({});
    expect(avisos).toBe(1);
  });
});
