import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AGORA } from '../../core/tempo/agora';
import { ListaDeCategorias } from '../categorias/categorias.service';
import { Conta, ListaDeContas } from '../contas/contas.service';
import { NUBANK_CONTA } from './apresentacao';
import { Importacao } from './importacao';
import { Importacao as ItemDoHistorico, Previa, Resultado } from './importacao.service';

function conta(id: number, nome: string): Conta {
  return { id, nome, tipo: 'CORRENTE', saldoInicial: 0, dataSaldoInicial: '2026-10-01', cor: null, saldoAtual: 0, arquivadaEm: null };
}

const contas: ListaDeContas = { ativas: [conta(1, 'Banco Horizonte'), conta(2, 'Conta conjunta')], arquivadas: [],
  totais: { geral: 0, disponivel: 0, investimentos: 0 } };
const categorias: ListaDeCategorias = { despesas: { ativas: [], arquivadas: [] }, receitas: { ativas: [], arquivadas: [] } };

const previa: Previa = {
  hash: 'abc', formato: 'OFX', nomeArquivo: 'extrato.ofx', contaId: 1, contaNome: 'Banco Horizonte', dataSaldoInicial: '2026-10-01',
  dataInicio: '2026-11-05', dataFim: '2026-11-06', arquivoRepetido: null, outraConta: null, perfil: null, origens: [],
  linhas: [
    { linha: 2, data: '2026-11-06', descricao: 'PADARIA', valor: -12.5, situacao: 'NOVA', motivo: null, marcada: true, futuro: false,
      erro: null, parecido: null },
    { linha: 1, data: '2026-11-05', descricao: 'CAFE', valor: -6, situacao: 'NOVA', motivo: null, marcada: true, futuro: false, erro: null,
      parecido: null },
  ],
};

const historico: ItemDoHistorico[] = [{
  id: 12, nomeArquivo: 'extrato-outubro.ofx', contaId: 1, contaNome: 'Banco Horizonte', contaArquivada: false,
  criadaEm: '2026-11-04T23:41:00Z', dataInicio: '2026-10-01', dataFim: '2026-11-03', importadas: 3, jaImportadas: 0, desmarcadas: 0,
  deFora: 0, editados: 1, excluidos: 1, desfeitaEm: null,
}];

describe('Importacao', () => {
  let httpTesting: HttpTestingController;

  afterEach(() => {
    httpTesting.verify();
    TestBed.inject(MatDialog).closeAll();
  });

  function criar(conta: string | null = null) {
    TestBed.configureTestingModule({
      imports: [Importacao],
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: AGORA, useValue: () => new Date('2026-11-10T15:00:00Z') },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(conta === null ? {} : { conta }) } } },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(Importacao);
    httpTesting.expectOne('/api/contas').flush(contas);
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
    expect(componente.contaId()).toBe(2);
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
    expect(componente.contaId()).toBe(1);
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

  it('o perfil salvo que não lê o arquivo abre o passo do perfil, com o perfil e o alerta (mockup, linha 623)', () => {
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
    componente.importar([{ linha: 2, categoriaId: 9 }]);
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

  it('o 422 da confirmação aparece na revisão, que continua aberta (decisão 24)', () => {
    const { fixture, componente, elemento } = criar('1');
    componente.escolherArquivo(lista(new File(['OFXHEADER:100'], 'extrato.ofx')));
    httpTesting.expectOne('/api/importacoes/previa').flush(previa);
    componente.importar([{ linha: 2, categoriaId: null }]);
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
});
