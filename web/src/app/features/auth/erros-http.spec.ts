import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';

import { camposDoErro, codigoDoErro, detalheDoErro, propriedadeDoErro } from './erros-http';

describe('erros-http', () => {
  it('camposDoErro junta os campos de um 400, ficando com a primeira mensagem de cada um', () => {
    const erro = new HttpErrorResponse({
      status: 400,
      error: { campos: [{ campo: 'nome', mensagem: 'Informe o nome.' }, { campo: 'dataSaldoInicial', mensagem: 'A data não pode ser depois de hoje.' }, { campo: 'nome', mensagem: 'Outra.' }] },
    });
    expect(camposDoErro(erro)).toEqual({ nome: 'Informe o nome.', dataSaldoInicial: 'A data não pode ser depois de hoje.' });
  });

  it('camposDoErro ignora o que não é uma lista de campos', () => {
    expect(camposDoErro(new HttpErrorResponse({ status: 400, error: { detail: 'x' } }))).toEqual({});
    expect(camposDoErro(new HttpErrorResponse({ status: 400, error: { campos: [{ campo: 1 }, null, 'x'] } }))).toEqual({});
    expect(camposDoErro(new Error('rede'))).toEqual({});
  });

  it('codigoDoErro e detalheDoErro continuam como na Fatia 02', () => {
    const erro = new HttpErrorResponse({ status: 422, error: { codigo: 'NOME_EM_USO', detail: 'Já existe uma conta ativa com este nome.' } });
    expect(codigoDoErro(erro)).toBe('NOME_EM_USO');
    expect(detalheDoErro(erro, 'padrão')).toBe('Já existe uma conta ativa com este nome.');
    expect(detalheDoErro(new HttpErrorResponse({ status: 500 }), 'padrão')).toBe('padrão');
  });

  it('propriedadeDoErro lê um texto do ProblemDetail', () => {
    const erro = new HttpErrorResponse({ status: 422, error: { codigo: 'CONTA_EM_USO', uso: '2 lançamentos e 1 transferência', total: 3 } });
    expect(propriedadeDoErro(erro, 'uso')).toBe('2 lançamentos e 1 transferência');
    expect(propriedadeDoErro(erro, 'total')).toBeNull();
    expect(propriedadeDoErro(erro, 'outra')).toBeNull();
    expect(propriedadeDoErro(new Error('rede'), 'uso')).toBeNull();
  });
});
