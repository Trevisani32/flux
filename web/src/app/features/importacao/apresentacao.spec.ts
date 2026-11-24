import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';

import {
  chaveDaDescricao, contagens, dicaDaFatura, erroDeTamanho, fraseDaCorrecao, fraseDaJaImportadaNaFatura, fraseDasOrigens, fraseDoParecido,
  fraseDoParecidoNaFatura, frasesDoResultado, LIMITE_DO_ARQUIVO, linhaDoHistoricoDaFatura, mensagemDaCategoria, mensagemDoArquivo,
  notaDoResultadoDaFatura, NUBANK_CONTA, NUBANK_FATURA, parcelasParaCorrigir, PassoDoIndicador, passosDaImportacao, pedePerfil, perfilNaoLe,
  periodoDoArquivo, resumoDaFatura, resumoDoHistorico, resumoDoPerfil, rotuloDaFatura, rotuloDaSituacao, subtituloDaRevisaoDaFatura,
  tamanhoDoArquivo, textoDaLinhaDaFatura, textoDasParcelas, textoDoBotaoImportar, tituloDasCorrecoes,
} from './apresentacao';
import { Importacao, LinhaDaPrevia, Previa, Resultado } from './importacao.service';

function linha(parcial: Partial<LinhaDaPrevia>): LinhaDaPrevia {
  return { linha: 1, data: '2026-11-05', descricao: 'CAFE', valor: -6, situacao: 'NOVA', motivo: null, marcada: true, futuro: false,
    erro: null, parecido: null, sugestao: null, ...parcial };
}

function resultado(parcial: Partial<Resultado>): Resultado {
  return { importacaoId: 1, contaId: 1, contaNome: 'Banco Horizonte', importadas: 7, desmarcadas: 0, jaImportadas: 0, deFora: 0,
    entraramPorOutra: 0, saldoDeHoje: 0, mesDaUltimaLinha: '2026-11', ...parcial };
}

describe('apresentação da importação', () => {
  it('confere o tamanho no navegador (decisão 35) e escreve o tamanho do arquivo', () => {
    expect(erroDeTamanho(LIMITE_DO_ARQUIVO)).toBeNull();
    expect(erroDeTamanho(LIMITE_DO_ARQUIVO + 1)).toBe('O arquivo tem 5,1 MB. O limite é 5 MB.');
    expect(erroDeTamanho(7_549_747)).toBe('O arquivo tem 7,2 MB. O limite é 5 MB.');
    expect(tamanhoDoArquivo(38_000)).toBe('38 KB');
    expect(tamanhoDoArquivo(10)).toBe('1 KB');
    expect(tamanhoDoArquivo(1_100_000)).toBe('1,1 MB');
  });

  it('trata o 413 pelo status (o do nginx vem em HTML), o 422 pelo detail e o resto como falha', () => {
    expect(mensagemDoArquivo(new HttpErrorResponse({ status: 413, error: '<html>413 Request Entity Too Large</html>' })))
      .toBe('O arquivo pode ter no máximo 5 MB.');
    expect(mensagemDoArquivo(new HttpErrorResponse({ status: 422, error: { detail: 'Este arquivo tem extratos de 2 contas. Exporte uma conta por arquivo.' } })))
      .toBe('Este arquivo tem extratos de 2 contas. Exporte uma conta por arquivo.');
    expect(mensagemDoArquivo(new HttpErrorResponse({ status: 500 }))).toBe('Não foi possível ler o arquivo. Tente de novo.');
    expect(mensagemDoArquivo(new HttpErrorResponse({ status: 0 }))).toBe('Não foi possível ler o arquivo. Tente de novo.');
    expect(pedePerfil(new HttpErrorResponse({ status: 422, error: { codigo: 'PERFIL_NECESSARIO' } }))).toBe(true);
    expect(pedePerfil(new HttpErrorResponse({ status: 422, error: { codigo: 'ARQUIVO_VAZIO' } }))).toBe(false);
  });

  it('conta os grupos e escreve o botão, sem somar dinheiro', () => {
    const linhas = [linha({ situacao: 'NOVA' }), linha({ situacao: 'NOVA' }), linha({ situacao: 'PROVAVEL' }),
      linha({ situacao: 'JA_IMPORTADA' }), linha({ situacao: 'FORA', motivo: 'VALOR_ZERO' }), linha({ situacao: 'ANTES_DO_SALDO_INICIAL' })];
    expect(contagens(linhas)).toEqual({ paraImportar: 3, novas: 2, provaveis: 1, jaImportadas: 1, deFora: 2 });
    expect(textoDoBotaoImportar(0)).toBe('Marque ao menos uma linha.');
    expect(textoDoBotaoImportar(1)).toBe('Importar 1 lançamento');
    expect(textoDoBotaoImportar(1234)).toBe('Importar 1.234 lançamentos');
  });

  it('escreve a pílula da situação e a frase do parecido', () => {
    // 06/11 às 22:30 em São Paulo (07/11 em UTC): a frase usa o dia do fuso do negócio.
    const parecido = { descricao: 'Uber', data: '2026-11-06', manual: true, excluido: false, criadoEm: '2026-11-07T01:30:00Z',
      excluidoEm: null };
    expect(rotuloDaSituacao(linha({ situacao: 'PROVAVEL', parecido }), '2026-10-01', '2026-11-10')).toBe('Provável duplicada');
    expect(rotuloDaSituacao(linha({ situacao: 'JA_IMPORTADA', parecido: { ...parecido, manual: false, excluido: true } }), '2026-10-01',
      '2026-11-10')).toBe('Já importada (excluída)');
    expect(rotuloDaSituacao(linha({ situacao: 'ANTES_DO_SALDO_INICIAL' }), '2026-10-01', '2026-11-10'))
      .toBe('Antes do saldo inicial (01/10/2026)');
    expect(rotuloDaSituacao(linha({ situacao: 'FORA', motivo: 'LINHA_DE_SALDO' }), '2026-10-01', '2026-11-10')).toBe('Linha de saldo');
    expect(rotuloDaSituacao(linha({ situacao: 'FORA', motivo: 'DATA_ALEM_DO_LIMITE' }), '2026-10-01', '2026-11-10'))
      .toBe('Data depois de 10/11/2027');
    expect(rotuloDaSituacao(linha({ linha: 14, situacao: 'FORA', motivo: 'ILEGIVEL', erro: 'data ilegível', data: null }), '2026-10-01',
      '2026-11-10')).toBe('Linha 14: data ilegível.');
    expect(fraseDoParecido(parecido)).toBe('Parece com "Uber", lançado à mão em 06/11.');
    expect(fraseDoParecido({ ...parecido, manual: false, criadoEm: '2026-11-04T23:41:00Z' }))
      .toBe('Parece com "Uber", importado em 04/11.');
    expect(fraseDoParecido({ ...parecido, manual: false, excluido: true, criadoEm: '2026-11-04T23:41:00Z', excluidoEm: '2026-11-06T12:00:00Z' }))
      .toBe('Parece com "Uber", excluído em 06/11.');
  });

  it('monta os passos (o OFX pula o perfil depois do arquivo), o resumo do perfil e a origem das já importadas', () => {
    const resumo = (passos: PassoDoIndicador[]) => passos.map((passo) => `${passo.numero}:${passo.estado}${passo.nota === null ? '' : ` ${passo.nota}`}`);
    expect(resumo(passosDaImportacao('arquivo', null))).toEqual(['1:atual', '2:depois (só CSV)', '3:depois', '4:depois']);
    expect(resumo(passosDaImportacao('perfil', null))).toEqual(['1:feito', '2:atual', '3:depois', '4:depois']);
    expect(resumo(passosDaImportacao('revisao', 'OFX'))).toEqual(['1:feito', '3:atual', '4:depois']);
    expect(resumo(passosDaImportacao('pronto', 'CSV'))).toEqual(['1:feito', '2:feito', '3:feito', '4:atual']);
    expect(passosDaImportacao('revisao', 'OFX').map((passo) => passo.nome)).toEqual(['Arquivo', 'Revisar', 'Pronto']);
    expect(resumoDoPerfil(NUBANK_CONTA)).toEqual({ modelo: 'Nubank (conta)', detalhes: 'vírgula · DD/MM/AAAA · ponto decimal' });
    expect(resumoDoPerfil({ ...NUBANK_CONTA, separador: 'PONTO_E_VIRGULA', separadorDecimal: 'VIRGULA', colunaSaidas: 3, colunaIdentificador: null }))
      .toEqual({ modelo: 'Personalizado', detalhes: 'ponto e vírgula · DD/MM/AAAA · vírgula decimal · entradas e saídas separadas' });
    expect(fraseDasOrigens([{ nomeArquivo: 'extrato-outubro.ofx', importadaEm: '2026-11-04T23:41:00Z' }]))
      .toBe('Vieram de extrato-outubro.ofx, importado em 04/11/2026, e não entram de novo.');
    expect(fraseDasOrigens([{ nomeArquivo: 'a.ofx', importadaEm: '2026-11-04T23:41:00Z' }, { nomeArquivo: 'b.ofx', importadaEm: '2026-10-01T12:00:00Z' }]))
      .toBe('Vieram de 2 importações anteriores e não entram de novo.');
  });

  it('escreve o histórico com os textos aprovados e reconhece os 422 do perfil e da categoria', () => {
    const item: Importacao = { id: 1, nomeArquivo: 'extrato-novembro.ofx', contaId: 1, contaNome: 'Banco Horizonte', contaArquivada: false,
      criadaEm: '2026-11-10T17:32:00Z', dataInicio: '2026-09-28', dataFim: '2026-11-09', importadas: 7, jaImportadas: 34, desmarcadas: 6,
      deFora: 4, editados: 0, excluidos: 0, desfeitaEm: null };
    expect(resumoDoHistorico(item)).toEqual({ titulo: '7 importados', nota: '34 já existiam · 6 desmarcados · 4 de fora' });
    expect(resumoDoHistorico({ ...item, importadas: 1, jaImportadas: 1, desmarcadas: 0 }))
      .toEqual({ titulo: '1 importado', nota: '1 já existia · 4 de fora' });
    expect(resumoDoHistorico({ ...item, jaImportadas: 0, desmarcadas: 0, deFora: 0 })).toEqual({ titulo: '7 importados', nota: '' });
    expect(resumoDoHistorico({ ...item, importadas: 3, excluidos: 2 })).toEqual({ titulo: '3 importados', nota: '2 excluídos depois' });
    expect(resumoDoHistorico({ ...item, importadas: 12, desfeitaEm: '2026-11-02T21:03:00Z' }))
      .toEqual({ titulo: 'Desfeita em 02/11', nota: '12 lançamentos saíram' });

    expect(perfilNaoLe(new HttpErrorResponse({ status: 422, error: { codigo: 'PERFIL_NAO_LE' } }))).toBe(true);
    expect(perfilNaoLe(new HttpErrorResponse({ status: 422, error: { codigo: 'PERFIL_NECESSARIO' } }))).toBe(false);
    const linhas = [linha({ linha: 7, descricao: 'CAFE DA ESQUINA' })];
    expect(mensagemDaCategoria(new HttpErrorResponse({ status: 422, error: { codigo: 'CATEGORIA_INVALIDA', detail: 'Revise as linhas marcadas.',
      linha: 7, data: '05/11' } }), linhas)).toBe('Revise as linhas marcadas. Linha 7 (05/11): CAFE DA ESQUINA.');
    expect(mensagemDaCategoria(new HttpErrorResponse({ status: 422, error: { codigo: 'LIMITE_DE_LANCAMENTOS', detail: 'Limite.' } }), linhas))
      .toBeNull();
  });

  it('compara descrições como a API: sem acento, minúsculas e espaços colapsados', () => {
    expect(chaveDaDescricao('  CAFÉ\u00a0 DA   Esquina ')).toBe('cafe da esquina');
    expect(chaveDaDescricao('CAFE DA ESQUINA')).toBe('cafe da esquina');
  });

  it('escreve o resultado (decisão 27), com a corrida de outra importação', () => {
    expect(frasesDoResultado(resultado({ desmarcadas: 6, jaImportadas: 34, deFora: 4 }))).toEqual({
      titulo: '7 lançamentos importados em Banco Horizonte',
      texto: '6 ficaram desmarcados, 34 já tinham sido importados e 4 ficaram de fora.',
    });
    expect(frasesDoResultado(resultado({ importadas: 1, desmarcadas: 1, jaImportadas: 1 }))).toEqual({
      titulo: '1 lançamento importado em Banco Horizonte', texto: '1 ficou desmarcado e 1 já tinha sido importado.',
    });
    expect(frasesDoResultado(resultado({ importadas: 0, importacaoId: null, jaImportadas: 0, entraramPorOutra: 3 }))).toEqual({
      titulo: 'Nada foi importado',
      texto: '3 linhas já tinham entrado por outra importação e ficaram de fora.',
    });
    expect(frasesDoResultado(resultado({})).texto).toBe('');
    // Fatia 05c ("Corrigir ao importar"): a parcela corrigida, mesmo sem nada importado.
    expect(frasesDoResultado(resultado({ importadas: 0, importacaoId: null, jaImportadas: 0, desmarcadas: 1, corrigidas: 1 }))).toEqual({
      titulo: 'Nada novo foi importado', texto: '1 parcela já importada ficou com o valor do banco. 1 ficou desmarcado.',
    });
    expect(frasesDoResultado(resultado({ importadas: 2, jaImportadas: 0, corrigidas: 3 })).texto)
      .toBe('3 parcelas já importadas ficaram com o valor do banco.');
    // Fatia 04b: as categorizadas por regras vêm primeiro, nunca mais que as importadas.
    expect(frasesDoResultado(resultado({ importadas: 9, jaImportadas: 31 }), 6).texto).toBe('6 categorizados por regras e 31 já tinham sido importados.');
    expect(frasesDoResultado(resultado({ importadas: 1 }), 1).texto).toBe('1 categorizado por regra.');
    expect(frasesDoResultado(resultado({ importadas: 1 }), 2).texto).toBe('1 categorizado por regra.');
  });

  it('a fatura (Fatia 05c): o seletor e a dica da fatura, o arquivo lido, o que cada linha é, a parcela e o pagamento', () => {
    expect(rotuloDaFatura({ mes: '2026-11', fechamento: '2026-11-03', vencimento: '2026-11-10', situacao: 'FECHADA' }, '2026-11-10'))
      .toBe('Novembro de 2026 · fechou 03/11');
    expect(rotuloDaFatura({ mes: '2027-01', fechamento: '2027-01-03', vencimento: '2027-01-10', situacao: 'FUTURA' }, '2026-11-10'))
      .toBe('Janeiro de 2027 · fecha 03/01');
    expect(dicaDaFatura('2026-11-21')).toBe('Sugerida pela compra mais recente (21/11). Todas as linhas vão para esta fatura, como no banco.');
    expect(dicaDaFatura(null)).toBe('Sugerida pela data de hoje. Todas as linhas vão para esta fatura, como no banco.');
    const previa = { formato: 'OFX', perfil: null, linhas: [linha({})], dataInicio: '2026-11-05', dataFim: '2026-11-05' } as unknown as Previa;
    expect(resumoDaFatura(previa)).toBe('OFX de cartão · 1 linha · de 05/11 a 05/11/2026');
    expect(resumoDaFatura({ ...previa, formato: 'CSV', perfil: NUBANK_FATURA }))
      .toBe('CSV da fatura do Nubank · 1 linha · de 05/11 a 05/11/2026');
    expect(periodoDoArquivo('2025-12-03', '2026-11-20')).toBe('de 03/12/2025 a 20/11/2026');
    expect(periodoDoArquivo('2015-01-10', '2028-01-10')).toBe('de 10/01/2015 a 10/01/2028');
    expect(periodoDoArquivo('2015-01-10', '2015-01-10')).toBe('de 10/01 a 10/01/2015');
    expect(subtituloDaRevisaoDaFatura({ ...previa, nomeArquivo: 'fatura.csv', cartaoNome: 'Cartão Horizonte', fatura: {
      mes: '2026-11', fechamento: '2026-11-03', vencimento: '2026-11-10', situacao: 'FECHADA',
    } }, '2026-11-12')).toBe('fatura.csv em Cartão Horizonte · fatura de novembro de 2026 · fechou 03/11 · vence 10/11');
    expect(resumoDoPerfil(NUBANK_FATURA)).toEqual({ modelo: 'Nubank (fatura)', detalhes: 'vírgula · AAAA-MM-DD · ponto decimal · compras positivas' });

    const parcela = linha({ descricao: 'Curso - Parcela 3/10', valor: -120, parcela: 3, parcelas: 10 });
    expect(textoDaLinhaDaFatura(parcela, '2026-12', '2026-11-10')).toEqual({
      longo: 'Cria a compra com as parcelas 3 a 10 (até julho de 2027)', curto: 'cria as parcelas 3 a 10' });
    expect(textoDaLinhaDaFatura(linha({ valor: -4.37 }), '2026-12', '2026-11-10')).toEqual({ longo: 'À vista', curto: 'À vista' });
    expect(textoDaLinhaDaFatura(linha({ valor: 15 }), '2026-12', '2026-11-10')).toBeNull();
    const parecido = { descricao: 'Mercado Bom Preço', data: '2026-11-04', manual: true, excluido: false, criadoEm: '2026-11-06T13:00:00Z',
      excluidoEm: null };
    expect(fraseDoParecidoNaFatura(linha({ valor: -312.45 }), parecido).longo.replace(/\s/g, ' '))
      .toBe('Parece Mercado Bom Preço, lançada à mão (R$ 312,45 em 04/11).');
    expect(fraseDoParecidoNaFatura(parcela, { ...parecido, manual: false })).toEqual({
      longo: 'Parece a parcela 3/10 de Mercado Bom Preço, importada em 06/11.', curto: 'importada em 06/11' });
    expect(fraseDaJaImportadaNaFatura({ ...parcela, parecido })).toBe('A parcela 3/10 de Mercado Bom Preço já está no Flux');
    expect(fraseDaJaImportadaNaFatura(linha({ parecido }))).toBeNull();
    const corrige = { ...parcela, situacao: 'JA_IMPORTADA' as const, valor: -33.33, valorNoFlux: -33.34, parcela: 2, parcelas: 3,
      parecido: { ...parecido, descricao: 'Fone Fictício', manual: false } };
    expect(fraseDaCorrecao(corrige).replace(/\s/g, ' ')).toBe('A parcela 2/3 de Fone Fictício: R$ 33,34 no Flux, R$ 33,33 no arquivo.');
    expect(tituloDasCorrecoes(1)).toBe('1 parcela já importada fica com o valor do banco');
    expect(tituloDasCorrecoes(2)).toBe('2 parcelas já importadas ficam com o valor do banco');
    expect(parcelasParaCorrigir(1)).toBe('1 parcela');
    expect(parcelasParaCorrigir(12)).toBe('12 parcelas');
    expect(textoDasParcelas([parcela, linha({})])).toBe('a compra em 10x cria 8 parcelas');
    expect(textoDasParcelas([parcela, { ...parcela, parcela: 1, parcelas: 3 }])).toBe('as 2 compras parceladas criam 11 parcelas');
    expect(textoDasParcelas([linha({})])).toBeNull();
    expect(rotuloDaSituacao(linha({ situacao: 'FORA', motivo: 'PAGAMENTO_DA_FATURA' }), null, '2026-11-10')).toBe('Pagamento da fatura: registre pela conta');
    expect(rotuloDaSituacao(linha({ situacao: 'FORA', motivo: 'DATA_ANTES_DO_LIMITE' }), null, '2026-11-10')).toBe('Data antes de 10/11/2016');
    expect(rotuloDaSituacao(linha({ situacao: 'FORA', motivo: 'COMPRA_JA_NO_FLUX', parcela: 2, parcelas: 10, parcelaQueCobre: 3 }), null,
      '2026-11-10')).toBe('A compra já está no Flux a partir da parcela 3/10');
    expect(rotuloDaSituacao(linha({ situacao: 'FORA', motivo: 'PARCELA_DO_ARQUIVO', parcela: 6, parcelas: 10, parcelaQueCobre: 5 }), null,
      '2026-11-10')).toBe('Entra com a parcela 5/10 deste arquivo');
  });

  it('o resultado e o histórico da fatura (Fatia 05c) dizem o cartão, a fatura e as parcelas criadas', () => {
    const daFatura = resultado({ contaId: null, contaNome: null, saldoDeHoje: null, cartaoId: 5, cartaoNome: 'Cartão Horizonte', fatura: '2026-12',
      importadas: 4, lancamentos: 11, parceladas: [{ descricao: 'Curso', parcelaInicial: 3, parcelas: 10 }] });
    expect(frasesDoResultado(daFatura).titulo).toBe('4 lançamentos importados');
    expect(notaDoResultadoDaFatura(daFatura, '2026-11-10'))
      .toBe('em Cartão Horizonte, fatura de dezembro · a compra em 10x criou as parcelas 3 a 10 (11 lançamentos no total)');
    expect(notaDoResultadoDaFatura({ ...daFatura, parceladas: [{ descricao: 'A', parcelaInicial: 3, parcelas: 10 },
      { descricao: 'B', parcelaInicial: 1, parcelas: 3 }] }, '2026-11-10'))
      .toBe('em Cartão Horizonte, fatura de dezembro · as 2 compras parceladas criaram 11 parcelas (11 lançamentos no total)');
    expect(notaDoResultadoDaFatura({ ...daFatura, fatura: '2027-01', parceladas: [] }, '2026-11-10')).toBe('em Cartão Horizonte, fatura de janeiro de 2027');
    expect(notaDoResultadoDaFatura(resultado({}), '2026-11-10')).toBeNull();
    const item = { cartaoNome: 'Cartão Horizonte', faturaMes: '2026-12', criadaEm: '2026-11-04T23:41:00Z' } as Importacao;
    expect(linhaDoHistoricoDaFatura(item, '2026-11-10')).toMatch(/^Cartão Horizonte · fatura de dezembro · 04\/11\/2026/);
  });
});
