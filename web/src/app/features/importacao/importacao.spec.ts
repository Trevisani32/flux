import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AGORA } from '../../core/tempo/agora';
import { Cartao, ListaDeCartoes } from '../cartoes/cartoes.service';
import { ListaDeCategorias } from '../categorias/categorias.service';
import { Conta, ListaDeContas } from '../contas/contas.service';
import { ItemDaLista } from '../lancamentos/lancamentos.service';
import { NUBANK_CONTA, NUBANK_FATURA } from './apresentacao';
import { Importacao } from './importacao';
import { Importacao as ItemDoHistorico, LadoSugerido, Previa, Resultado } from './importacao.service';
import { FluxoDeRegras } from '../regras/fluxo-de-regras';
import { Regra, SugestaoDeRegra } from '../regras/regras.service';

function conta(id: number, nome: string): Conta {
  return { id, nome, tipo: 'CORRENTE', saldoInicial: 0, dataSaldoInicial: '2026-10-01', cor: null, saldoAtual: 0, arquivadaEm: null };
}

const contas: ListaDeContas = { ativas: [conta(1, 'Banco Horizonte'), conta(2, 'Conta conjunta')], arquivadas: [],
  totais: { geral: 0, disponivel: 0, investimentos: 0 } };
const categorias: ListaDeCategorias = { despesas: { ativas: [], arquivadas: [] }, receitas: { ativas: [], arquivadas: [] } };

/** Fatia 05c: o cartão de "Importar em". */
const horizonte = { id: 5, nome: 'Cartão Horizonte' } as Cartao;
const cartoes: ListaDeCartoes = { ativos: [horizonte], arquivados: [] };

/** Fatia 05c: a prévia de uma fatura (decisões 40 e 41): a sugerida, a anterior e a seguinte, e uma linha "Parcela 3/10". */
const previaDaFatura: Previa = {
  hash: 'def', formato: 'CSV', nomeArquivo: 'fatura-dezembro.csv', contaId: null, contaNome: null, dataSaldoInicial: null,
  dataInicio: '2026-08-20', dataFim: '2026-11-21', arquivoRepetido: null, outraConta: null, perfil: NUBANK_FATURA, origens: [],
  cartaoId: 5, cartaoNome: 'Cartão Horizonte', dataDaSugestao: '2026-11-21',
  fatura: { mes: '2026-12', fechamento: '2026-12-03', vencimento: '2026-12-10', situacao: 'ABERTA' },
  faturas: [{ mes: '2026-11', fechamento: '2026-11-03', vencimento: '2026-11-10', situacao: 'FECHADA' },
    { mes: '2026-12', fechamento: '2026-12-03', vencimento: '2026-12-10', situacao: 'ABERTA' },
    { mes: '2027-01', fechamento: '2027-01-03', vencimento: '2027-01-10', situacao: 'FUTURA' }],
  linhas: [
    { linha: 3, data: '2026-11-21', descricao: 'Padaria Pao Quente', valor: -22.5, situacao: 'NOVA', motivo: null, marcada: true, futuro: false,
      erro: null, parecido: null, sugestao: null, parcela: null, parcelas: null, parcelaQueCobre: null, descricaoDaCompra: null },
    { linha: 2, data: '2026-08-20', descricao: 'Curso Online Ficticio - Parcela 3/10', valor: -120, situacao: 'NOVA', motivo: null,
      marcada: true, futuro: false, erro: null, parecido: null, sugestao: null, parcela: 3, parcelas: 10, parcelaQueCobre: null,
      descricaoDaCompra: 'Curso Online Ficticio' },
  ],
};

const previa: Previa = {
  hash: 'abc', formato: 'OFX', nomeArquivo: 'extrato.ofx', contaId: 1, contaNome: 'Banco Horizonte', dataSaldoInicial: '2026-10-01',
  dataInicio: '2026-11-05', dataFim: '2026-11-06', arquivoRepetido: null, outraConta: null, perfil: null, origens: [],
  linhas: [
    { linha: 2, data: '2026-11-06', descricao: 'PADARIA', valor: -12.5, situacao: 'NOVA', motivo: null, marcada: true, futuro: false,
      erro: null, parecido: null, sugestao: null },
    { linha: 1, data: '2026-11-05', descricao: 'CAFE', valor: -6, situacao: 'NOVA', motivo: null, marcada: true, futuro: false, erro: null,
      parecido: null, sugestao: null },
  ],
};

const historico: ItemDoHistorico[] = [{
  id: 12, nomeArquivo: 'extrato-outubro.ofx', contaId: 1, contaNome: 'Banco Horizonte', contaArquivada: false,
  criadaEm: '2026-11-04T23:41:00Z', dataInicio: '2026-10-01', dataFim: '2026-11-03', importadas: 3, jaImportadas: 0, desmarcadas: 0,
  deFora: 0, editados: 1, excluidos: 1, desfeitaEm: null,
}];

describe('Importacao', () => {
  let httpTesting: HttpTestingController;

  // Mesmo quando o verify acusa um pedido pendente, o diálogo fecha e o TestBed volta ao zero: um afterEach que lança
  // pula a limpeza do Angular, e o diálogo e o módulo velhos derrubariam os casos (e os arquivos) seguintes.
  afterEach(() => {
    try {
      httpTesting.verify();
    } finally {
      TestBed.inject(MatDialog).closeAll();
      TestBed.resetTestingModule();
    }
  });

  /** `parametros`: o atalho da URL ({ conta: '2' } ou, na Fatia 05c, { cartao: '5' }). */
  function criar(conta: string | null = null, parametros: Record<string, string> = {}, listas = { contas, cartoes }) {
    TestBed.configureTestingModule({
      imports: [Importacao],
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: AGORA, useValue: () => new Date('2026-11-10T15:00:00Z') },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(conta === null ? parametros : { conta }) } } },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(Importacao);
    httpTesting.expectOne('/api/contas').flush(listas.contas);
    httpTesting.expectOne('/api/cartoes').flush(listas.cartoes);
    httpTesting.expectOne('/api/categorias').flush(categorias);
    httpTesting.expectOne({ method: 'GET', url: '/api/importacoes' }).flush(historico);
    fixture.detectChanges();
    return { fixture, componente: fixture.componentInstance, elemento: fixture.nativeElement as HTMLElement };
  }

  function lista(arquivo: File): FileList {
    return { item: () => arquivo } as unknown as FileList;
  }

  function botao(elemento: ParentNode, texto: string): HTMLButtonElement {
    return Array.from(elemento.querySelectorAll<HTMLButtonElement>('button')).find((item) => item.textContent?.trim() === texto) as HTMLButtonElement;
  }

  function alertaDaPagina(elemento: HTMLElement): string | undefined {
    return elemento.querySelector(':scope > .flux-conteudo > app-alerta .flux-alerta__texto')?.textContent?.trim();
  }

  it('acima de 5 MB, o erro aparece no navegador e nada vai para a API (decisão 35)', () => {
    const { fixture, componente, elemento } = criar();
    componente.escolherArquivo(lista(new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'grande.ofx')));
    fixture.detectChanges();
    expect(elemento.querySelector('#importacao-arquivo-erro')?.textContent).toBe('O arquivo tem 5,1 MB. O limite é 5 MB.');
    httpTesting.expectNone('/api/importacoes/previa');
  });

  it('o 413 da API ou do nginx (em HTML) vira a mensagem dos 5 MB; a conta vem da URL', () => {
    const { fixture, componente, elemento } = criar('2');
    expect(componente.destino()).toEqual({ tipo: 'conta', id: 2 });
    componente.escolherArquivo(lista(new File(['x'], 'extrato.ofx')));
    const pedido = httpTesting.expectOne('/api/importacoes/previa');
    expect((pedido.request.body as FormData).get('contaId')).toBe('2');
    pedido.flush('<html><body>413 Request Entity Too Large</body></html>', { status: 413, statusText: 'Request Entity Too Large' });
    fixture.detectChanges();
    expect(elemento.querySelector('#importacao-arquivo-erro')?.textContent).toBe('O arquivo pode ter no máximo 5 MB.');
  });

  it('CSV sem perfil vai para o passo do perfil, com a amostra (decisão 12)', () => {
    const { fixture, componente, elemento } = criar('1');
    componente.escolherArquivo(lista(new File(['Data,Valor,Identificador,Descrição'], 'nubank.csv')));
    httpTesting.expectOne('/api/importacoes/previa').flush({ codigo: 'PERFIL_NECESSARIO', detail: 'Esta conta ainda não tem perfil de CSV.' },
      { status: 422, statusText: 'Unprocessable Entity' });
    const amostra = httpTesting.expectOne('/api/importacoes/amostra');
    expect((amostra.request.body as FormData).has('formato')).toBe(false);
    amostra.flush({ linhas: [['Data', 'Valor', 'Identificador', 'Descrição']], modelo: 'NUBANK_CONTA', primeiraLinha: null,
      erroDaPrimeiraLinha: 'O arquivo não tem nenhuma linha depois do cabeçalho.', formato: { separador: 'VIRGULA', linhasDeCabecalho: 1,
        colunaData: 1, formatoData: 'DD_MM_AAAA', colunaDescricao: 4, colunaValor: 2, colunaSaidas: null, colunaIdentificador: 3,
        separadorDecimal: 'PONTO' } });
    fixture.detectChanges();
    expect(elemento.querySelector('h1')?.textContent).toBe('Como ler este CSV');
    expect(elemento.querySelector('app-perfil-csv .flux-alerta__titulo')?.textContent).toBe('Parece um extrato da conta do Nubank');
    expect((elemento.querySelector('#perfil-descricao') as HTMLSelectElement).selectedOptions[0]?.textContent).toBe('4 · Descrição');
    expect(elemento.querySelector('.importacao__passo--atual')?.textContent).toBe('2Perfil do CSV');

    // "Salvar perfil e revisar" lê de novo; o erro aparece no passo do perfil, que continua aberto.
    componente.lerArquivo();
    httpTesting.expectOne('/api/importacoes/previa').flush({ codigo: 'PERFIL_NAO_LE',
      detail: 'Nenhuma linha pôde ser lida com este perfil. Confira as colunas e o formato da data.' }, { status: 422, statusText: 'Unprocessable Entity' });
    fixture.detectChanges();
    expect(componente.passo()).toBe('perfil');
    expect(alertaDaPagina(elemento)).toBe('Nenhuma linha pôde ser lida com este perfil. Confira as colunas e o formato da data.');
  });

  it('CSV com perfil salvo mostra o resumo do perfil e espera o "Revisar" (decisão 12), com o indicador de passos', () => {
    const { fixture, componente, elemento } = criar('1');
    expect(elemento.querySelector('.importacao__passo--atual')?.textContent).toBe('1Arquivo');
    expect(elemento.querySelectorAll('.importacao__passo')).toHaveLength(4);
    componente.escolherArquivo(lista(new File(['Data,Valor,Identificador,Descrição'], 'nubank.csv')));
    httpTesting.expectOne('/api/importacoes/previa').flush({ ...previa, formato: 'CSV', nomeArquivo: 'nubank.csv', perfil: NUBANK_CONTA });
    fixture.detectChanges();

    expect(componente.passo()).toBe('arquivo');
    expect(elemento.querySelector('.importacao__perfil-resumo span')?.textContent)
      .toBe('Perfil de Banco Horizonte: Nubank (conta) · vírgula · DD/MM/AAAA · ponto decimal');
    expect(botao(elemento, 'Editar perfil')).toBeDefined();
    botao(elemento, 'Revisar').click();
    fixture.detectChanges();
    expect(elemento.querySelector('h1')?.textContent).toBe('Revisar importação');
    expect(elemento.querySelector('.importacao__passo--atual')?.textContent).toBe('3Revisar');
  });

  it('na revisão, o erro de trocar de conta aparece no alerta da página, e a revisão continua', () => {
    const { fixture, componente, elemento } = criar('1');
    componente.escolherArquivo(lista(new File(['OFXHEADER:100'], 'extrato.ofx')));
    httpTesting.expectOne('/api/importacoes/previa').flush({ ...previa, outraConta: { contaId: 2, contaNome: 'Conta conjunta', finalDaConta: '5678' } });
    fixture.detectChanges();
    expect(elemento.querySelectorAll('.importacao__passo')).toHaveLength(3);

    botao(elemento, 'Importar em Conta conjunta').click();
    httpTesting.expectOne('/api/importacoes/previa').flush({ codigo: 'CONTA_ARQUIVADA',
      detail: 'Conta conjunta está arquivada. Reative a conta para importar nela.' }, { status: 422, statusText: 'Unprocessable Entity' });
    fixture.detectChanges();
    expect(componente.passo()).toBe('revisao');
    expect(alertaDaPagina(elemento)).toBe('Conta conjunta está arquivada. Reative a conta para importar nela.');
    expect(componente.destino()).toEqual({ tipo: 'conta', id: 1 });
  });

  it('"Voltar" do perfil aberto pelo "Editar perfil" volta ao resumo ou à revisão, com o mesmo arquivo e a mesma prévia', () => {
    const { fixture, componente, elemento } = criar('1');
    const amostra = { linhas: [['Data', 'Valor', 'Identificador', 'Descrição']], modelo: 'NUBANK_CONTA', primeiraLinha: null,
      erroDaPrimeiraLinha: 'O arquivo não tem nenhuma linha depois do cabeçalho.', formato: NUBANK_CONTA };
    componente.escolherArquivo(lista(new File(['Data,Valor,Identificador,Descrição'], 'nubank.csv')));
    httpTesting.expectOne('/api/importacoes/previa').flush({ ...previa, formato: 'CSV', nomeArquivo: 'nubank.csv', perfil: NUBANK_CONTA });
    fixture.detectChanges();

    // Do resumo do perfil, no passo do arquivo.
    botao(elemento, 'Editar perfil').click();
    httpTesting.expectOne({ method: 'GET', url: '/api/importacoes/perfis/1' }).flush(NUBANK_CONTA);
    httpTesting.expectOne('/api/importacoes/amostra').flush(amostra);
    fixture.detectChanges();
    expect(componente.passo()).toBe('perfil');
    botao(elemento, 'Voltar').click();
    fixture.detectChanges();
    expect(componente.passo()).toBe('arquivo');
    expect(elemento.querySelector('.importacao__perfil-resumo span')?.textContent)
      .toBe('Perfil de Banco Horizonte: Nubank (conta) · vírgula · DD/MM/AAAA · ponto decimal');

    // Da revisão, com uma linha desmarcada: ela fica montada e escondida durante o perfil.
    botao(elemento, 'Revisar').click();
    fixture.detectChanges();
    const padaria = () => Array.from(elemento.querySelectorAll('label.revisao__caixa'))
      .find((rotulo) => rotulo.textContent?.trim() === 'Importar PADARIA de 06/11')?.querySelector('input') as HTMLInputElement;
    padaria().click();
    fixture.detectChanges();
    botao(elemento, 'Editar perfil do CSV').click();
    httpTesting.expectOne({ method: 'GET', url: '/api/importacoes/perfis/1' }).flush(NUBANK_CONTA);
    httpTesting.expectOne('/api/importacoes/amostra').flush(amostra);
    fixture.detectChanges();
    expect(componente.passo()).toBe('perfil');
    expect((elemento.querySelector('app-revisao')?.parentElement as HTMLElement).hidden).toBe(true);
    botao(elemento, 'Voltar').click();
    fixture.detectChanges();
    expect(elemento.querySelector('h1')?.textContent).toBe('Revisar importação');
    expect(componente.previa()?.nomeArquivo).toBe('nubank.csv');
    expect(componente.arquivo()?.name).toBe('nubank.csv');
    expect(padaria().checked).toBe(false);
    expect(botao(elemento, 'Importar 1 lançamento')).toBeDefined();
    httpTesting.expectNone('/api/importacoes/previa');
  });

  it('depois de salvar o perfil, a leitura que falha não deixa a prévia velha: o "Voltar" recomeça', () => {
    const { fixture, componente, elemento } = criar('1');
    componente.escolherArquivo(lista(new File(['Data,Valor,Identificador,Descrição'], 'nubank.csv')));
    httpTesting.expectOne('/api/importacoes/previa').flush({ ...previa, formato: 'CSV', nomeArquivo: 'nubank.csv', perfil: NUBANK_CONTA });
    fixture.detectChanges();
    botao(elemento, 'Revisar').click();
    fixture.detectChanges();
    botao(elemento, 'Editar perfil do CSV').click();
    httpTesting.expectOne({ method: 'GET', url: '/api/importacoes/perfis/1' }).flush(NUBANK_CONTA);
    httpTesting.expectOne('/api/importacoes/amostra').flush({ linhas: [['Data', 'Valor', 'Identificador', 'Descrição']], modelo: 'NUBANK_CONTA',
      primeiraLinha: null, erroDaPrimeiraLinha: 'O arquivo não tem nenhuma linha depois do cabeçalho.', formato: NUBANK_CONTA });
    fixture.detectChanges();

    botao(elemento, 'Salvar perfil e revisar').click();
    httpTesting.expectOne({ method: 'PUT', url: '/api/importacoes/perfis/1' }).flush(NUBANK_CONTA);
    httpTesting.expectOne('/api/importacoes/previa').flush({ codigo: 'PERFIL_NAO_LE',
      detail: 'Nenhuma linha pôde ser lida com este perfil. Confira as colunas e o formato da data.' }, { status: 422, statusText: 'Unprocessable Entity' });
    fixture.detectChanges();
    expect(componente.passo()).toBe('perfil');
    expect(componente.previa()).toBeNull();
    expect(elemento.querySelector('app-revisao')).toBeNull();

    botao(elemento, 'Voltar').click();
    fixture.detectChanges();
    expect(componente.passo()).toBe('arquivo');
    expect(componente.arquivo()).toBeNull();
    expect(elemento.querySelector('.importacao__perfil-resumo')).toBeNull();
  });

  it('fechar os excluídos sem restaurar (Esc ou clique fora) também recarrega o histórico', async () => {
    const { componente } = criar();
    componente.verExcluidos(historico[0]);
    TestBed.tick();
    httpTesting.expectOne({ method: 'GET', url: '/api/importacoes/12/excluidos' }).flush([]);
    TestBed.inject(MatDialog).closeAll();
    await vi.waitFor(() => httpTesting.expectOne({ method: 'GET', url: '/api/importacoes' }).flush(historico));
  });

  it('o perfil salvo que não lê o arquivo abre o passo do perfil, com o perfil e o alerta', () => {
    const { fixture, componente, elemento } = criar('1');
    componente.escolherArquivo(lista(new File(['Data;Valor'], 'outro-banco.csv')));
    httpTesting.expectOne('/api/importacoes/previa').flush({ codigo: 'PERFIL_NAO_LE',
      detail: 'Nenhuma linha pôde ser lida com este perfil. Confira as colunas e o formato da data.' }, { status: 422, statusText: 'Unprocessable Entity' });
    httpTesting.expectOne({ method: 'GET', url: '/api/importacoes/perfis/1' }).flush(NUBANK_CONTA);
    const amostra = httpTesting.expectOne('/api/importacoes/amostra');
    expect((amostra.request.body as FormData).get('formato')).toBeInstanceOf(Blob);
    amostra.flush({ linhas: [['Data', 'Valor']], modelo: null, primeiraLinha: null, erroDaPrimeiraLinha: 'Linha 2: data ilegível.',
      formato: NUBANK_CONTA });
    fixture.detectChanges();

    expect(componente.passo()).toBe('perfil');
    expect(elemento.querySelector('h1')?.textContent).toBe('Como ler este CSV');
    expect(alertaDaPagina(elemento)).toBe('Nenhuma linha pôde ser lida com este perfil. Confira as colunas e o formato da data.');
  });

  it('o 422 de categoria diz a linha, e as categorias são recarregadas para a revisão limpar a que saiu', () => {
    const { fixture, componente, elemento } = criar('1');
    componente.escolherArquivo(lista(new File(['OFXHEADER:100'], 'extrato.ofx')));
    httpTesting.expectOne('/api/importacoes/previa').flush(previa);
    componente.importar({ escolhas: [{ linha: 2, categoriaId: 9, descricao: null }], aMao: [], porRegras: 0 });
    httpTesting.expectOne({ method: 'POST', url: '/api/importacoes' }).flush({ codigo: 'CATEGORIA_INVALIDA',
      detail: 'Uma das categorias escolhidas foi arquivada ou excluída. Revise as linhas marcadas.', linha: 2, data: '06/11' },
    { status: 422, statusText: 'Unprocessable Entity' });
    httpTesting.expectOne({ method: 'GET', url: '/api/categorias' }).flush(categorias);
    fixture.detectChanges();
    expect(elemento.querySelector('app-revisao [role="alert"]')?.textContent?.trim().replace(/\s+/g, ' '))
      .toContain('Revise as linhas marcadas. Linha 2 (06/11): PADARIA.');
    expect(componente.passo()).toBe('revisao');
  });

  it('revisa, importa o arquivo de novo com as escolhas e mostra o resultado, com o histórico recarregado', () => {
    const { fixture, componente, elemento } = criar('1');
    const arquivo = new File(['OFXHEADER:100'], 'extrato.ofx');
    componente.escolherArquivo(lista(arquivo));
    httpTesting.expectOne('/api/importacoes/previa').flush(previa);
    fixture.detectChanges();
    expect(elemento.querySelector('h1')?.textContent).toBe('Revisar importação');
    expect(elemento.querySelector('.importacao__subtitulo')?.textContent).toBe('extrato.ofx · Banco Horizonte · de 05/11 a 06/11/2026');

    (Array.from(elemento.querySelectorAll<HTMLButtonElement>('app-revisao button')).find((botao) => botao.textContent?.trim()
      === 'Importar 2 lançamentos') as HTMLButtonElement).click();
    const pedido = httpTesting.expectOne({ method: 'POST', url: '/api/importacoes' });
    expect(((pedido.request.body as FormData).get('arquivo') as File).name).toBe(arquivo.name);
    expect((pedido.request.body as FormData).get('pedido')).toBeInstanceOf(Blob);
    const resultado: Resultado = { importacaoId: 13, contaId: 1, contaNome: 'Banco Horizonte', importadas: 2, desmarcadas: 0,
      jaImportadas: 1, deFora: 0, entraramPorOutra: 0, saldoDeHoje: 5941.1, mesDaUltimaLinha: '2026-11' };
    pedido.flush(resultado);
    httpTesting.expectOne({ method: 'GET', url: '/api/importacoes' }).flush(historico);
    fixture.detectChanges();
    expect(elemento.querySelector('.importacao__titulo-pronto')?.textContent).toBe('2 lançamentos importados em Banco Horizonte');
    expect(elemento.querySelector('.importacao__texto-pronto')?.textContent).toBe('1 já tinha sido importado.');
  });

  it('depois de importar, conta as categorizadas por regras e sugere regras com as escolhas à mão; o erro (e o 422) tem "Tentar de novo", '
    + 'e "Criar regra" tira a sugestão (Fatia 04b)', () => {
    const { fixture, componente, elemento } = criar('1');
    componente.escolherArquivo(lista(new File(['OFXHEADER:100'], 'extrato.ofx')));
    httpTesting.expectOne('/api/importacoes/previa').flush(previa);
    const aMao = [{ contaId: 1, descricaoBanco: 'PADARIA', descricao: null, categoriaId: 9 }];
    componente.importar({ escolhas: [{ linha: 2, categoriaId: 9, descricao: null }], aMao, porRegras: 1 });
    httpTesting.expectOne({ method: 'POST', url: '/api/importacoes' }).flush({ importacaoId: 13, contaId: 1, contaNome: 'Banco Horizonte',
      importadas: 1, desmarcadas: 1, jaImportadas: 0, deFora: 0, entraramPorOutra: 0, saldoDeHoje: 0, mesDaUltimaLinha: '2026-11' });
    httpTesting.expectOne({ method: 'GET', url: '/api/importacoes' }).flush(historico);
    const primeira = httpTesting.expectOne({ method: 'POST', url: '/api/regras/sugestoes' });
    expect(primeira.request.body).toEqual({ itens: aMao });
    primeira.flush(null, { status: 500, statusText: 'Erro' });
    fixture.detectChanges();
    expect(elemento.querySelector('.importacao__texto-pronto')?.textContent).toBe('1 categorizado por regra e 1 ficou desmarcado.');
    expect(elemento.querySelector('.flux-alerta__titulo')?.textContent).toBe('Não foi possível sugerir regras agora.');
    expect(elemento.querySelector('app-alerta .flux-alerta__texto')).toBeNull();

    const tentarDeNovo = () => (Array.from(elemento.querySelectorAll<HTMLButtonElement>('button'))
      .find((botao) => botao.textContent?.trim() === 'Tentar de novo') as HTMLButtonElement).click();
    tentarDeNovo();
    httpTesting.expectOne({ method: 'POST', url: '/api/regras/sugestoes' })
      .flush({ detail: 'Categoria não encontrada.', codigo: 'CATEGORIA_INVALIDA' }, { status: 422, statusText: 'Erro' });
    fixture.detectChanges();
    expect(elemento.querySelector('app-alerta .flux-alerta__texto')?.textContent).toBe('Categoria não encontrada.');

    const sugestao: SugestaoDeRegra = { condicao: 'CONTEM', texto: 'PADARIA', categoriaId: 9, categoriaNome: 'Padaria', categoriaMaeNome: 'Alimentação',
      natureza: 'DESPESA', descricao: null, deDescricao: 'PADARIA' };
    tentarDeNovo();
    httpTesting.expectOne({ method: 'POST', url: '/api/regras/sugestoes' }).flush([sugestao]);
    fixture.detectChanges();
    expect(elemento.querySelector('#importacao-sugestoes')?.textContent).toBe('Criar regras com as suas escolhas');
    expect(elemento.querySelector('.importacao__texto-sugestao')?.textContent?.trim()).toBe('Contém "PADARIA"→ Alimentação › Padaria · de PADARIA');

    const novaRegra = vi.spyOn(TestBed.inject(FluxoDeRegras), 'novaRegra').mockReturnValue(of({ id: 40 } as Regra));
    (elemento.querySelector('button[aria-label="Criar regra para PADARIA"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(novaRegra).toHaveBeenCalledWith(sugestao);
    expect(elemento.querySelector('#importacao-sugestoes')).toBeNull();
  });

  it('o 422 da confirmação aparece na revisão, que continua aberta (decisão 24)', () => {
    const { fixture, componente, elemento } = criar('1');
    componente.escolherArquivo(lista(new File(['OFXHEADER:100'], 'extrato.ofx')));
    httpTesting.expectOne('/api/importacoes/previa').flush(previa);
    componente.importar({ escolhas: [{ linha: 2, categoriaId: null, descricao: null }], aMao: [], porRegras: 0 });
    httpTesting.expectOne({ method: 'POST', url: '/api/importacoes' }).flush({ codigo: 'LIMITE_DE_LANCAMENTOS', detail: 'Esta importação '
      + 'passaria do limite de 1.000 lançamentos em novembro de 2026. Desmarque linhas desse mês ou importe um período menor.' },
    { status: 422, statusText: 'Unprocessable Entity' });
    fixture.detectChanges();
    expect(elemento.querySelector('app-revisao [role="alert"]')?.textContent).toContain('Desmarque linhas desse mês');
    expect(componente.passo()).toBe('revisao');
  });

  it('desfazer pede confirmação dizendo o que sai, e o histórico recarrega (decisão 32)', async () => {
    const { componente } = criar();
    const abrir = vi.spyOn(TestBed.inject(MatDialog), 'open');
    componente.desfazer(historico[0]);
    TestBed.tick();
    expect(abrir).toHaveBeenCalledTimes(1);
    const confirmacao = document.querySelector('app-confirmacao-dialogo') as HTMLElement;
    expect(confirmacao.querySelector('h2')?.textContent).toBe('Desfazer a importação de extrato-outubro.ofx?');
    expect(Array.from(confirmacao.querySelectorAll('.confirmacao__texto')).map((p) => p.textContent?.replace(/\s/g, ' '))).toEqual([
      'Os 3 lançamentos que ela trouxe saem de Banco Horizonte, inclusive 1 que você editou e 1 que você excluiu. O saldo muda na hora.',
      'O registro fica no histórico. Para trazer as linhas de volta, importe o arquivo de novo.']);
    (Array.from(confirmacao.querySelectorAll<HTMLButtonElement>('button')).find((botao) => botao.textContent?.trim()
      === 'Desfazer importação') as HTMLButtonElement).click();
    TestBed.tick();
    httpTesting.expectOne({ method: 'POST', url: '/api/importacoes/12/desfazer' }).flush({ ...historico[0], desfeitaEm: '2026-11-10T15:00:00Z' });
    await vi.waitFor(() => httpTesting.expectOne({ method: 'GET', url: '/api/importacoes' }).flush([]));
  });

  it('com a conta arquivada, desfazer só avisa, com "Fechar" e "Ir para Contas", sem chamar a API', () => {
    const { componente } = criar();
    componente.desfazer({ ...historico[0], contaArquivada: true });
    TestBed.tick();
    const aviso = document.querySelector('app-confirmacao-dialogo') as HTMLElement;
    expect(aviso.querySelector('h2')?.textContent).toBe('Esta importação não pode ser desfeita agora');
    expect(Array.from(aviso.querySelectorAll('button')).map((item) => item.textContent?.trim())).toEqual(['Fechar', 'Ir para Contas']);
    httpTesting.expectNone({ method: 'POST', url: '/api/importacoes/12/desfazer' });
  });

  it('o resultado sugere os pares de transferência; "Converter" abre a conversão com o par escolhido e tira a linha (Fatia 04c)', async () => {
    const { fixture, componente, elemento } = criar('1');
    componente.escolherArquivo(lista(new File(['OFXHEADER:100'], 'extrato.ofx')));
    httpTesting.expectOne('/api/importacoes/previa').flush(previa);
    componente.importar({ escolhas: [{ linha: 2, categoriaId: null, descricao: null }], aMao: [], porRegras: 0 });
    const ted: LadoSugerido = { id: 70, data: '2026-11-05', descricao: 'TED 341.0001DEV FICTICIO', valor: -1000, contaId: 1, contaNome: 'Banco Horizonte' };
    const recebida: LadoSugerido = { id: 71, data: '2026-11-06', descricao: 'Transferência recebida', valor: 1000, contaId: 2,
      contaNome: 'Conta conjunta' };
    httpTesting.expectOne({ method: 'POST', url: '/api/importacoes' }).flush({ importacaoId: 13, contaId: 1, contaNome: 'Banco Horizonte',
      importadas: 1, desmarcadas: 1, jaImportadas: 0, deFora: 0, entraramPorOutra: 0, saldoDeHoje: 0, mesDaUltimaLinha: '2026-11',
      pares: [{ lancamento: ted, par: recebida }] });
    httpTesting.expectOne({ method: 'GET', url: '/api/importacoes' }).flush(historico);
    fixture.detectChanges();

    expect(Array.from(elemento.querySelectorAll('#importacao-pares span')).map((span) => span.textContent))
      .toEqual(['Parecem transferências entre suas contas', 'Parecem transferências']);
    expect(Array.from(elemento.querySelectorAll('.importacao__lado-do-par b')).map((b) => b.textContent))
      .toEqual(['TED 341.0001DEV FICTICIO', 'Transferência recebida']);
    expect(Array.from(elemento.querySelectorAll('.importacao__lado-do-par .valor__texto')).map((valor) => valor.textContent?.replace(/\s/g, ' ')))
      .toEqual(['−R$ 1.000,00', '+R$ 1.000,00']);

    (elemento.querySelector('button[aria-label="Converter TED 341.0001DEV FICTICIO em transferência"]') as HTMLButtonElement).click();
    TestBed.tick();
    const fixo = { tipo: 'DESPESA', id: 70, data: '2026-11-05', descricao: 'TED 341.0001DEV FICTICIO', valor: -1000, observacao: null, contaId: 1,
      contaNome: 'Banco Horizonte', categoriaId: null, categoriaNome: null, categoriaMaeNome: null, categoriaArquivada: false, contaDestinoId: null,
      contaDestinoNome: null, direcao: null, futuro: false, somenteLeitura: false, origem: 'IMPORTADO', descricaoBanco: 'TED 341.0001DEV FICTICIO',
      lados: null } satisfies ItemDaLista;
    httpTesting.expectOne({ method: 'GET', url: '/api/lancamentos/70/pares' }).flush({ lancamento: fixo, inicio: '2026-10-31', fim: '2026-11-10',
      pares: [{ ...recebida, origem: 'IMPORTADO' }, { ...recebida, id: 72, descricao: 'Outra entrada', origem: 'MANUAL' }] });
    TestBed.tick();
    const conversao = document.querySelector('app-conversao-dialogo') as HTMLElement;
    expect(Array.from(conversao.querySelectorAll<HTMLInputElement>('input[type="radio"]')).map((radio) => radio.checked))
      .toEqual([true, false, false]);
    (Array.from(conversao.querySelectorAll<HTMLButtonElement>('button')).find((botao) => botao.textContent?.trim() === 'Converter') as HTMLButtonElement)
      .click();
    TestBed.tick();
    const pedido = httpTesting.expectOne({ method: 'POST', url: '/api/lancamentos/70/converter' });
    expect(pedido.request.body).toEqual({ parId: 71, contaId: null, descricao: null });
    pedido.flush({ ...fixo, tipo: 'TRANSFERENCIA', id: 40 });
    await vi.waitFor(() => httpTesting.expectOne({ method: 'GET', url: '/api/importacoes' }).flush(historico));
    fixture.detectChanges();
    expect(elemento.querySelector('#importacao-pares')).toBeNull();
  });

  it('desfazer com lançamentos em transferências diz o que acontece com o outro lado (Fatia 04c, decisão 34)', async () => {
    const { componente } = criar();
    const conjunta = { data: '2026-11-02', contaId: 2, contaNome: 'Conta conjunta', contaArquivada: false, tipo: 'RECEITA' } as const;
    componente.desfazer({ ...historico[0], transferencias: [conjunta] });
    TestBed.tick();
    let confirmacao = document.querySelector('app-confirmacao-dialogo') as HTMLElement;
    expect(confirmacao.querySelector('h2')?.textContent).toBe('Desfazer a importação de extrato-outubro.ofx?');
    expect(confirmacao.querySelector('.flux-alerta--atencao .flux-alerta__titulo')?.textContent).toBe('1 deles está numa transferência');
    expect(confirmacao.querySelector('.flux-alerta--atencao .flux-alerta__texto')?.textContent).toBe('A transferência de 02/11 com Conta conjunta é '
      + 'desfeita, e o lançamento de Conta conjunta volta a ser uma receita, sem categoria.');
    TestBed.inject(MatDialog).closeAll();
    await vi.waitFor(() => expect(document.querySelector('app-confirmacao-dialogo')).toBeNull());

    componente.desfazer({ ...historico[0], transferencias: [conjunta, { ...conjunta, data: '2026-11-03', tipo: 'DESPESA' },
      { ...conjunta, contaId: 3, contaNome: 'Carteira' }] });
    TestBed.tick();
    confirmacao = document.querySelector('app-confirmacao-dialogo') as HTMLElement;
    expect(confirmacao.querySelector('.flux-alerta--atencao .flux-alerta__titulo')?.textContent).toBe('3 deles estão em transferências');
    expect(confirmacao.querySelector('.flux-alerta--atencao .flux-alerta__texto')?.textContent).toBe('As transferências são desfeitas, e os '
      + 'lançamentos de Conta conjunta e Carteira voltam a ser receitas ou despesas, sem categoria.');
    TestBed.inject(MatDialog).closeAll();
    await vi.waitFor(() => expect(document.querySelector('app-confirmacao-dialogo')).toBeNull());

    componente.desfazer(historico[0]);
    TestBed.tick();
    expect(document.querySelector('app-confirmacao-dialogo .flux-alerta--atencao')).toBeNull();
  });

  it('com o outro lado de uma transferência numa conta arquivada, desfazer só avisa, sem chamar a API (Fatia 04c)', () => {
    const { componente } = criar();
    componente.desfazer({ ...historico[0], transferencias: [{ data: '2026-11-02', contaId: 2, contaNome: 'Conta conjunta', contaArquivada: true,
      tipo: 'RECEITA' }] });
    TestBed.tick();
    const aviso = document.querySelector('app-confirmacao-dialogo') as HTMLElement;
    expect(aviso.querySelector('h2')?.textContent).toBe('Esta importação não pode ser desfeita agora');
    expect(aviso.querySelector('.confirmacao__texto')?.textContent).toBe('A transferência de 02/11 liga esta importação a Conta conjunta, que está '
      + 'arquivada. Reative a conta para desfazer esta importação.');
    expect(Array.from(aviso.querySelectorAll('button')).map((item) => item.textContent?.trim())).toEqual(['Fechar', 'Ir para Contas']);
    httpTesting.expectNone({ method: 'POST', url: '/api/importacoes/12/desfazer' });
  });

  it('desfazer com pagamento de fatura convertido avisa que ele sai e a fatura volta a ter o pendente (Fatia 05b)', async () => {
    const { componente } = criar();
    const pagamento = { data: '2026-11-09', cartaoId: 7, cartaoNome: 'Cartão Horizonte', cartaoArquivado: false, faturaMes: '2026-10' } as const;
    componente.desfazer({ ...historico[0], pagamentos: [pagamento] });
    TestBed.tick();
    let confirmacao = document.querySelector('app-confirmacao-dialogo') as HTMLElement;
    expect(confirmacao.querySelector('h2')?.textContent).toBe('Desfazer a importação de extrato-outubro.ofx?');
    expect(confirmacao.querySelector('.flux-alerta--atencao .flux-alerta__titulo')?.textContent).toBe('1 deles é pagamento de fatura');
    expect(confirmacao.querySelector('.flux-alerta--atencao .flux-alerta__texto')?.textContent).toBe('O pagamento de 09/11 sai, e a fatura de '
      + 'outubro de Cartão Horizonte volta a ter esse valor pendente.');
    TestBed.inject(MatDialog).closeAll();
    await vi.waitFor(() => expect(document.querySelector('app-confirmacao-dialogo')).toBeNull());

    componente.desfazer({ ...historico[0], transferencias: [{ data: '2026-11-02', contaId: 2, contaNome: 'Conta conjunta', contaArquivada: false,
      tipo: 'RECEITA' }], pagamentos: [pagamento, { ...pagamento, data: '2026-11-03', cartaoId: 8, cartaoNome: 'Cartão Lua', faturaMes: '2025-12' }] });
    TestBed.tick();
    confirmacao = document.querySelector('app-confirmacao-dialogo') as HTMLElement;
    expect(confirmacao.querySelector('.flux-alerta--atencao .flux-alerta__titulo')?.textContent)
      .toBe('3 deles estão em transferências ou são pagamentos de fatura');
    expect(confirmacao.querySelector('.flux-alerta--atencao .flux-alerta__texto')?.textContent).toBe('A transferência de 02/11 com Conta conjunta é '
      + 'desfeita, e o lançamento de Conta conjunta volta a ser uma receita, sem categoria. Os pagamentos saem, e as faturas de Cartão '
      + 'Horizonte e Cartão Lua voltam a ter esses valores pendentes.');
  });

  it('com o cartão de um pagamento convertido arquivado, desfazer só avisa e leva a Cartões, sem chamar a API (Fatia 05b)', async () => {
    const { componente } = criar();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    componente.desfazer({ ...historico[0], pagamentos: [{ data: '2026-11-09', cartaoId: 8, cartaoNome: 'Cartão Lua', cartaoArquivado: true,
      faturaMes: '2026-10' }] });
    TestBed.tick();
    const aviso = document.querySelector('app-confirmacao-dialogo') as HTMLElement;
    expect(aviso.querySelector('h2')?.textContent).toBe('Esta importação não pode ser desfeita agora');
    expect(aviso.querySelector('.confirmacao__texto')?.textContent).toBe('O pagamento de 09/11 liga esta importação a Cartão Lua, que está '
      + 'arquivado. Reative o cartão para desfazer esta importação.');
    expect(Array.from(aviso.querySelectorAll('button')).map((item) => item.textContent?.trim())).toEqual(['Fechar', 'Ir para Cartões']);
    Array.from(aviso.querySelectorAll('button'))[1].click();
    await vi.waitFor(() => expect(navegar).toHaveBeenCalledWith(['/cartoes']));
    httpTesting.expectNone({ method: 'POST', url: '/api/importacoes/12/desfazer' });
  });

  it('"Importar em" tem as contas e os cartões; o atalho do cartão o escolhe, e a leitura volta ao arquivo com a fatura sugerida '
    + '(Fatia 05c, decisão 40)', () => {
    const { fixture, componente, elemento } = criar(null, { cartao: '5' });
    const seletor = elemento.querySelector('#importacao-destino') as HTMLSelectElement;
    expect(elemento.querySelector('label[for="importacao-destino"]')?.textContent).toBe('Importar em');
    expect(seletor.getAttribute('aria-describedby')).toBe('importacao-destino-dica');
    expect(Array.from(seletor.querySelectorAll('optgroup')).map((grupo) => `${grupo.label}: ${Array.from(grupo.querySelectorAll('option'))
      .map((opcao) => opcao.textContent).join(', ')}`)).toEqual(['Contas: Banco Horizonte, Conta conjunta', 'Cartões: Cartão Horizonte']);
    expect(componente.destino()).toEqual({ tipo: 'cartao', id: 5 });

    componente.escolherArquivo(lista(new File(['date,title,amount'], 'fatura-dezembro.csv')));
    const pedido = httpTesting.expectOne('/api/importacoes/previa');
    expect((pedido.request.body as FormData).get('cartaoId')).toBe('5');
    expect((pedido.request.body as FormData).has('fatura')).toBe(false);
    pedido.flush(previaDaFatura);
    fixture.detectChanges();

    expect(componente.passo()).toBe('arquivo');
    expect(elemento.querySelector('.importacao__arquivo-nome span')?.textContent).toBe('CSV da fatura do Nubank · 2 linhas · de 20/08 a 21/11/2026');
    expect(elemento.querySelector('.importacao__perfil-resumo span')?.textContent)
      .toBe('Perfil de Cartão Horizonte: Nubank (fatura) · vírgula · AAAA-MM-DD · ponto decimal · compras positivas');
    const fatura = elemento.querySelector('#importacao-fatura') as HTMLSelectElement;
    expect(Array.from(fatura.options).map((opcao) => opcao.textContent)).toEqual(['Novembro de 2026 · fechou 03/11',
      'Dezembro de 2026 · fecha 03/12', 'Janeiro de 2027 · fecha 03/01']);
    expect(fatura.selectedOptions[0]?.textContent).toBe('Dezembro de 2026 · fecha 03/12');
    expect(elemento.querySelector('#importacao-fatura-dica')?.textContent)
      .toBe('Sugerida pela compra mais recente (21/11). Todas as linhas vão para esta fatura, como no banco.');

    // Outra fatura lê de novo, com ela; "Revisar" abre a revisão com o subtítulo da fatura.
    componente.escolherFatura('2027-01');
    expect((httpTesting.expectOne('/api/importacoes/previa').request.body as FormData).get('fatura')).toBe('2027-01');
    expect(componente.passo()).toBe('arquivo');
    botao(elemento, 'Revisar').click();
    fixture.detectChanges();
    expect(elemento.querySelector('h1')?.textContent).toBe('Revisar importação');
  });

  it('a revisão da fatura importa com o cartão e a fatura; o resultado diz as parcelas criadas e "Ver fatura" leva a ela (Fatia 05c)', async () => {
    const { fixture, componente, elemento } = criar(null, { cartao: '5' });
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    componente.escolherArquivo(lista(new File(['date,title,amount'], 'fatura-dezembro.csv')));
    httpTesting.expectOne('/api/importacoes/previa').flush(previaDaFatura);
    componente.passo.set('revisao');
    fixture.detectChanges();
    expect(elemento.querySelector('.importacao__subtitulo .flux-so-desktop')?.textContent)
      .toBe('fatura-dezembro.csv em Cartão Horizonte · fatura de dezembro de 2026 · fecha 03/12 · vence 10/12');
    expect(elemento.querySelector('.importacao__subtitulo .flux-so-celular')?.textContent)
      .toBe('fatura-dezembro.csv · Cartão Horizonte · fatura de dezembro');

    componente.importar({ escolhas: [{ linha: 2, categoriaId: null, descricao: null }, { linha: 3, categoriaId: null, descricao: null }],
      aMao: [], porRegras: 0 });
    const pedido = httpTesting.expectOne({ method: 'POST', url: '/api/importacoes' });
    expect(JSON.parse(await ((pedido.request.body as FormData).get('pedido') as Blob).text())).toEqual({ cartaoId: 5, fatura: '2026-12',
      hash: 'def', linhas: [{ linha: 2, categoriaId: null, descricao: null }, { linha: 3, categoriaId: null, descricao: null }] });
    const resultado: Resultado = { importacaoId: 14, contaId: null, contaNome: null, importadas: 2, desmarcadas: 1, jaImportadas: 0, deFora: 1,
      entraramPorOutra: 0, saldoDeHoje: null, mesDaUltimaLinha: '2026-12', pares: [], cartaoId: 5, cartaoNome: 'Cartão Horizonte',
      fatura: '2026-12', lancamentos: 9, parceladas: [{ descricao: 'Curso Online Ficticio', parcelaInicial: 3, parcelas: 10 }] };
    pedido.flush(resultado);
    httpTesting.expectOne({ method: 'GET', url: '/api/importacoes' }).flush(historico);
    fixture.detectChanges();

    expect(elemento.querySelector('.importacao__titulo-pronto')?.textContent).toBe('2 lançamentos importados');
    expect(Array.from(elemento.querySelectorAll('.importacao__texto-pronto')).map((texto) => texto.textContent)).toEqual([
      'em Cartão Horizonte, fatura de dezembro · a compra em 10x criou as parcelas 3 a 10 (9 lançamentos no total)', '1 ficou desmarcado e 1 ficou de fora.']);
    botao(elemento, 'Ver fatura').click();
    expect(navegar).toHaveBeenCalledWith(['/cartoes', 5, 'faturas', '2026-12']);
  });

  it('no histórico, a importação de fatura diz o cartão e a fatura; desfazer com o cartão arquivado só avisa, e a confirmação diz '
    + 'que as parcelas saem (Fatia 05c)', async () => {
    const { componente } = criar();
    const daFatura: ItemDoHistorico = { ...historico[0], id: 14, nomeArquivo: 'fatura-dezembro.csv', contaId: null, contaNome: null,
      cartaoId: 5, cartaoNome: 'Cartão Horizonte', cartaoArquivado: false, faturaMes: '2026-12', importadas: 4, excluidos: 1, editados: 1 };
    componente.historico.set([daFatura]);
    TestBed.tick();
    const item = document.querySelector('.importacao__item') as HTMLElement;
    expect(item.querySelector('.importacao__textos span')?.textContent?.trim()).toMatch(/^Cartão Horizonte · fatura de dezembro · 04\/11\/2026/);

    componente.desfazer({ ...daFatura, cartaoArquivado: true });
    TestBed.tick();
    const aviso = document.querySelector('app-confirmacao-dialogo') as HTMLElement;
    expect(aviso.querySelector('.confirmacao__texto')?.textContent).toBe('Cartão Horizonte está arquivado. Reative o cartão para desfazer '
      + 'esta importação.');
    expect(Array.from(aviso.querySelectorAll('button')).map((botaoDoAviso) => botaoDoAviso.textContent?.trim())).toEqual(['Fechar', 'Ir para Cartões']);
    TestBed.inject(MatDialog).closeAll();
    httpTesting.expectNone({ method: 'POST', url: '/api/importacoes/14/desfazer' });

    componente.desfazer(daFatura);
    TestBed.tick();
    const confirmacao = Array.from(document.querySelectorAll('app-confirmacao-dialogo')).at(-1) as HTMLElement;
    expect(confirmacao.querySelector('.confirmacao__texto')?.textContent?.replace(/\s/g, ' ')).toBe('Os 3 lançamentos que ela trouxe saem de '
      + 'Cartão Horizonte, inclusive 1 que você editou, com as parcelas seguintes das compras parceladas. O total das faturas muda na hora.');
    TestBed.inject(MatDialog).closeAll();

    componente.desfazer({ ...historico[0], importadas: 1, excluidos: 0 });
    TestBed.tick();
    expect((Array.from(document.querySelectorAll('app-confirmacao-dialogo')).at(-1) as HTMLElement).querySelector('.confirmacao__texto')?.textContent)
      .toBe('O 1 lançamento que ela trouxe sai de Banco Horizonte, inclusive 1 que você editou. O saldo muda na hora.');
    TestBed.inject(MatDialog).closeAll();

    componente.desfazer({ ...daFatura, importadas: 1, excluidos: 0, editados: 0 });
    TestBed.tick();
    expect((Array.from(document.querySelectorAll('app-confirmacao-dialogo')).at(-1) as HTMLElement).querySelector('.confirmacao__texto')?.textContent)
      .toBe('O 1 lançamento que ela trouxe sai de Cartão Horizonte, com as parcelas seguintes das compras parceladas. O total das faturas muda na hora.');
  });

  it('não mostra grupo vazio no destino da importação', () => {
    const apenasCartoes: ListaDeContas = { ...contas, ativas: [] };
    const { elemento } = criar(null, {}, { contas: apenasCartoes, cartoes });
    const seletor = elemento.querySelector('#importacao-destino') as HTMLSelectElement;
    expect(Array.from(seletor.querySelectorAll('optgroup')).map((grupo) => grupo.label)).toEqual(['Cartões']);
  });
});
