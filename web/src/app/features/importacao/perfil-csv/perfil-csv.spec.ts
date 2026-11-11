import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { NUBANK_CONTA } from '../apresentacao';
import { Amostra } from '../importacao.service';
import { PerfilCsv } from './perfil-csv';

const amostra: Amostra = {
  linhas: [['Data', 'Valor', 'Identificador', 'Descrição'], ['03/11/2026', '-18.50', 'a1', 'PADARIA CENTRAL']],
  modelo: 'NUBANK_CONTA', formato: NUBANK_CONTA, primeiraLinha: { data: '2026-11-03', descricao: 'PADARIA CENTRAL', valor: -18.5 },
  erroDaPrimeiraLinha: null,
};

describe('PerfilCsv', () => {
  let httpTesting: HttpTestingController;

  afterEach(() => httpTesting.verify());

  function criar(inicial: Amostra = amostra) {
    TestBed.configureTestingModule({ imports: [PerfilCsv], providers: [provideHttpClient(), provideHttpClientTesting()] });
    httpTesting = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(PerfilCsv);
    fixture.componentRef.setInput('arquivo', new File(['Data,Valor,Identificador,Descrição'], 'nubank.csv'));
    fixture.componentRef.setInput('contaId', 1);
    fixture.componentRef.setInput('contaNome', 'Conta conjunta');
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
    aceita.flush({ ...amostra, primeiraLinha: { data: '2026-11-04', descricao: 'MERCADO BOM PRECO', valor: -50 } });
    fixture.detectChanges();
    expect(elemento.querySelector('#perfil-cabecalho-erro')).toBeNull();
    expect(elemento.querySelector('.perfil__lida-descricao')?.textContent).toBe('MERCADO BOM PRECO');
  });

  it('o modelo vem do perfil carregado; as saídas em coluna separada têm a mensagem no campo; o que não é de um campo vai para o '
    + 'alerta (mockup, "Campos do perfil")', () => {
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
});
