import { describe, expect, it } from 'vitest';

import {
  avisoDaRegraCriada, avisoDosAplicados, CONDICOES, condicaoNoInicio, fraseDaCondicao, metaDaRegra, motivoDaPausa, nomeDaCategoria,
  notaDoGrupo, posicaoDaNova, quantidadeDeLancamentos, rotuloDaCondicao, textoDoAplicar, tituloDaPrevia,
} from './apresentacao';
import { letrasOuNumeros } from './regra-dialogo/regra-dialogo';
import { GrupoDaAplicacao, Regra } from './regras.service';

function regra(parcial: Partial<Regra>): Regra {
  return { id: 30, posicao: 1, condicao: 'CONTEM', texto: 'uber', contaId: null, contaNome: null, contaArquivada: false, natureza: 'DESPESA',
    categoriaId: 5, categoriaNome: 'App de transporte', categoriaMaeNome: 'Transporte', categoriaArquivada: false, descricao: null,
    pausada: false, ...parcial };
}

function grupo(parcial: Partial<GrupoDaAplicacao>): GrupoDaAplicacao {
  return { regraId: 30, posicao: 2, condicao: 'CONTEM', texto: 'uber', categoriaId: 5, categoriaNome: 'App de transporte',
    categoriaMaeNome: 'Transporte', descricao: null, quantidade: 1, manuais: 0, renomeados: 0, exemplos: [], ...parcial };
}

describe('apresentação das regras', () => {
  it('condições, frase da regra e nome da categoria', () => {
    expect(CONDICOES.map((condicao) => condicao.rotulo)).toEqual(['contém', 'começa com', 'igual a']);
    expect(rotuloDaCondicao('IGUAL_A')).toBe('igual a');
    expect(condicaoNoInicio('COMECA_COM')).toBe('Começa com');
    expect(fraseDaCondicao('CONTEM', 'uber')).toBe('contém "uber"');
    expect(nomeDaCategoria('App de transporte', 'Transporte')).toBe('Transporte › App de transporte');
    expect(nomeDaCategoria('Lazer', null)).toBe('Lazer');
    expect(posicaoDaNova(null)).toBe('Entra em 1º lugar. Dá para mudar a ordem na lista.');
    expect(posicaoDaNova(regra({ texto: 'uber eats' }))).toBe('Entra em 1º lugar, antes de "contém uber eats". Dá para mudar a ordem na lista.');
  });

  it('a meta da linha e o motivo da pausa (decisões 23 e 24)', () => {
    expect(metaDaRegra(regra({}))).toBe('Despesas · todas as contas');
    expect(metaDaRegra(regra({ natureza: 'RECEITA', contaId: 1, contaNome: 'Banco Horizonte', descricao: 'Salário' })))
      .toBe('Receitas · só Banco Horizonte · descrição vira "Salário"');
    expect(motivoDaPausa(regra({}))).toBeNull();
    expect(motivoDaPausa(regra({ pausada: true, categoriaArquivada: true, categoriaNome: 'Lazer', contaArquivada: true, contaNome: 'Poupança' })))
      .toBe('Pausada: Lazer está arquivada');
    expect(motivoDaPausa(regra({ pausada: true, contaArquivada: true, contaNome: 'Poupança antiga' }))).toBe('Pausada: Poupança antiga está arquivada');
  });

  it('contagens de "Aplicar regras", os avisos curtos e o título da prévia, sem somar dinheiro', () => {
    expect(quantidadeDeLancamentos(1)).toBe('1 lançamento');
    expect(quantidadeDeLancamentos(1234)).toBe('1.234 lançamentos');
    expect(textoDoAplicar(0)).toBe('Escolha ao menos uma regra.');
    expect(textoDoAplicar(37)).toBe('Aplicar a 37 lançamentos');
    expect(notaDoGrupo(grupo({}))).toBeNull();
    expect(notaDoGrupo(grupo({ descricao: 'Uber', renomeados: 19, manuais: 1 }))).toBe('e a descrição vira "Uber" · 1 lançado à mão: só a categoria muda');
    expect(notaDoGrupo(grupo({ descricao: 'Uber', renomeados: 0, manuais: 2 }))).toBe('2 lançados à mão: só a categoria muda');
    expect(avisoDaRegraCriada(0)).toBe('Regra criada.');
    expect(avisoDaRegraCriada(1)).toBe('Regra criada. 1 lançamento sem categoria casa com ela.');
    expect(avisoDaRegraCriada(4)).toBe('Regra criada. 4 lançamentos sem categoria casam com ela.');
    expect(avisoDosAplicados(0)).toBe('Nenhum lançamento mudou.');
    expect(avisoDosAplicados(1)).toBe('1 lançamento categorizado.');
    expect(avisoDosAplicados(37)).toBe('37 lançamentos categorizados.');
    expect(tituloDaPrevia(0, 0)).toBe('Não casa com nenhum lançamento seu ainda');
    expect(tituloDaPrevia(1, 1)).toBe('Casa com 1 lançamento seu, 1 sem categoria');
    expect(tituloDaPrevia(6, 4)).toBe('Casa com 6 lançamentos seus, 4 sem categoria');
  });

  it('o texto da regra conta letras e números depois da comparação (decisões 4 e 5)', () => {
    expect(letrasOuNumeros('* - /')).toBe(0);
    expect(letrasOuNumeros('a *')).toBe(1);
    expect(letrasOuNumeros('Ç')).toBe(1);
    expect(letrasOuNumeros('PAG*UBER')).toBe(7);
    expect(letrasOuNumeros('99')).toBe(2);
  });
});
