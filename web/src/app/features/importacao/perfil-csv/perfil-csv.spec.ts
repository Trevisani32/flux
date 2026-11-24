import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { NUBANK_CONTA, NUBANK_FATURA } from '../apresentacao';
import { Amostra, Destino } from '../importacao.service';
import { PerfilCsv } from './perfil-csv';

const amostra: Amostra = {
  linhas: [['Data', 'Valor', 'Identificador', 'Descrição'], ['03/11/2026', '-18.50', 'a1', 'PADARIA CENTRAL']],
  modelo: 'NUBANK_CONTA', formato: NUBANK_CONTA, primeiraLinha: { data: '2026-11-03', descricao: 'PADARIA CENTRAL', valor: -18.5 },
  erroDaPrimeiraLinha: null,
};

/** Fatia 05c: a fatura do Nubank (date,title,amount), lida com a compra negativa. */
const daFatura: Amostra = {
  linhas: [['date', 'title', 'amount'], ['2026-11-21', 'Padaria Pao Quente', '22.50']],
  modelo: 'NUBANK_FATURA', formato: NUBANK_FATURA, primeiraLinha: { data: '2026-11-21', descricao: 'Padaria Pao Quente', valor: -22.5 },
  erroDaPrimeiraLinha: null,
};

describe('PerfilCsv', () => {
  let httpTesting: HttpTestingController;

  afterEach(() => httpTesting.verify());

  function criar(inicial: Amostra = amostra, destino: Destino = { tipo: 'conta', id: 1 }, nome = 'Conta conjunta') {
    TestBed.configureTestingModule({ imports: [PerfilCsv], providers: [provideHttpClient(), provideHttpClientTesting()] });
    httpTesting = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(PerfilCsv);
    fixture.componentRef.setInput('arquivo', new File(['Data,Valor,Identificador,Descrição'], 'nubank.csv'));
    fixture.componentRef.setInput('destino', destino);
    fixture.componentRef.setInput('nome', nome);
    fixture.componentRef.setInput('amostraInicial', inicial);
    fixture.detectChanges();
    return { fixture, componente: fixture.componentInstance, elemento: fixture.nativeElement as HTMLElement };
  }

  it('um 400 da amostra vira o erro do campo e não congela a primeira linha lida: a mudança seguinte pede de novo', async () => {
    const { fixture, componente, elemento } = criar();
    componente.formulario.controls.linhasDeCabecalho.setValue(25);
    const recusada = await vi.waitFor(() => httpTesting.expectOne('/api/importacoes/amostra'), { timeout: 2000 });
    recusada.flush({ campos: [{ campo: 'linhasDeCabecalho', mensagem: 'Use de 0 a 20 linhas.' }] }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    expect(elemento.querySelector('#perfil-cabecalho-erro')?.textContent).toBe('Use de 0 a 20 linhas.');

    componente.formulario.controls.linhasDeCabecalho.setValue(1);
    const aceita = await vi.waitFor(() => httpTesting.expectOne('/api/importacoes/amostra'), { timeout: 2000 });
    expect((aceita.request.body as FormData).has('cartao')).toBe(false);
    aceita.flush({ ...amostra, primeiraLinha: { data: '2026-11-04', descricao: 'MERCADO BOM PRECO', valor: -50 } });
    fixture.detectChanges();
    expect(elemento.querySelector('#perfil-cabecalho-erro')).toBeNull();
    expect(elemento.querySelector('.perfil__lida-descricao')?.textContent).toBe('MERCADO BOM PRECO');
  });

  it('o modelo vem do perfil carregado; as saídas em coluna separada têm a mensagem no campo; o que não é de um campo vai para o '
    + 'alerta', () => {
    // Perfil salvo de outro banco num arquivo com o cabeçalho do Nubank: "Personalizado", sem o alerta do modelo pronto.
    const { fixture, componente, elemento } = criar({ ...amostra, formato: { ...NUBANK_CONTA, separador: 'PONTO_E_VIRGULA', separadorDecimal: 'VIRGULA' } });
    expect((elemento.querySelector('#perfil-modelo') as HTMLSelectElement).value).toBe('PERSONALIZADO');
    expect(elemento.querySelector('.flux-alerta--info')).toBeNull();
    componente.formulario.controls.modelo.setValue('NUBANK_CONTA');
    expect(componente.formato()).toEqual(NUBANK_CONTA);

    componente.formulario.controls.modo.setValue('DUAS');
    componente.formulario.controls.colunaSaidas.setValue(null);
    componente.salvar();
    fixture.detectChanges();
    expect(elemento.querySelector('#perfil-saidas-erro')?.textContent).toBe('Escolha a coluna das saídas.');
    expect(elemento.querySelector('#perfil-saidas')?.getAttribute('aria-describedby')).toBe('perfil-saidas-erro');

    componente.formulario.controls.colunaSaidas.setValue(2);
    componente.salvar();
    fixture.detectChanges();
    expect(elemento.querySelector('#perfil-saidas-erro')?.textContent).toBe('Use colunas diferentes para entradas e saídas.');
    httpTesting.expectNone({ method: 'PUT', url: '/api/importacoes/perfis/1' });

    componente.formulario.controls.colunaSaidas.setValue(3);
    componente.salvar();
    httpTesting.expectOne({ method: 'PUT', url: '/api/importacoes/perfis/1' })
      .flush({ campos: [{ campo: 'colunas', mensagem: 'Use uma coluna diferente para cada campo.' }] }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    expect(elemento.querySelector('#perfil-saidas-erro')).toBeNull();
    expect(elemento.querySelector('.flux-alerta--erro .flux-alerta__titulo')?.textContent).toBe('Use uma coluna diferente para cada campo.');
  });

  it('no cartão (Fatia 05c), o modelo pronto é "Nubank (fatura)", a coluna única tem "Compras vêm positivas" e o perfil vai para o '
    + 'cartão', async () => {
    const { fixture, componente, elemento } = criar(daFatura, { tipo: 'cartao', id: 5 }, 'Cartão Horizonte');
    expect(elemento.querySelector('app-alerta .flux-alerta__titulo')?.textContent).toBe('Parece a fatura do cartão Nubank');
    expect(Array.from((elemento.querySelector('#perfil-modelo') as HTMLSelectElement).options).map((opcao) => opcao.textContent))
      .toEqual(['Nubank (fatura)', 'Personalizado']);
    expect((elemento.querySelector('#perfil-modelo') as HTMLSelectElement).value).toBe('NUBANK_FATURA');
    const caixa = Array.from(elemento.querySelectorAll('label')).find((rotulo) => rotulo.textContent?.trim() === 'Compras vêm positivas')
      ?.querySelector('input') as HTMLInputElement;
    expect(caixa.checked).toBe(true);
    expect(componente.formato()).toEqual(NUBANK_FATURA);
    expect(Array.from(elemento.querySelectorAll('button')).map((botao) => botao.textContent?.trim())).toContain('Salvar perfil e continuar');

    // Desmarcar a opção pede a amostra de novo, com o cartão; nas colunas separadas, a opção some e não vai.
    caixa.click();
    const amostraDoCartao = await vi.waitFor(() => httpTesting.expectOne('/api/importacoes/amostra'), { timeout: 2000 });
    expect((amostraDoCartao.request.body as FormData).get('cartao')).toBe('true');
    amostraDoCartao.flush(daFatura);
    componente.formulario.controls.modo.setValue('DUAS');
    componente.formulario.controls.colunaSaidas.setValue(3);
    componente.formulario.controls.colunaValor.setValue(2);
    fixture.detectChanges();
    expect(elemento.querySelector('label[for="perfil-saidas"]')?.textContent).toBe('Coluna das compras');
    expect(elemento.querySelector('.perfil__compras-positivas')).toBeNull();
    expect(componente.formato().comprasPositivas).toBe(false);
    await vi.waitFor(() => httpTesting.expectOne('/api/importacoes/amostra').flush(daFatura), { timeout: 2000 });

    componente.formulario.controls.modo.setValue('UMA');
    componente.formulario.controls.comprasPositivas.setValue(true);
    componente.salvar();
    const salvo = httpTesting.expectOne({ method: 'PUT', url: '/api/importacoes/perfis/cartao/5' });
    expect(salvo.request.body).toEqual({ ...NUBANK_FATURA, colunaValor: 2 });
    salvo.flush(NUBANK_FATURA);
  });
});
