import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { Categoria, ListaDeCategorias } from '../../categorias/categorias.service';
import { Escolha, LinhaDaPrevia, Previa } from '../importacao.service';
import { DadosDoSeletor, EscolhaDoSeletor } from '../seletor-de-categoria/seletor-de-categoria';
import { Revisao } from './revisao';

function categoria(id: number, nome: string, natureza: 'DESPESA' | 'RECEITA', subcategorias: Categoria[] = []): Categoria {
  return { id, nome, natureza, categoriaMaeId: null, nomeDaMae: null, arquivadaEm: null, subcategorias };
}

const categorias: ListaDeCategorias = {
  despesas: { ativas: [categoria(1, 'Alimentação', 'DESPESA', [categoria(5, 'Restaurante', 'DESPESA')])], arquivadas: [] },
  receitas: { ativas: [categoria(7, 'Salário', 'RECEITA')], arquivadas: [] },
};

function linha(parcial: Partial<LinhaDaPrevia>): LinhaDaPrevia {
  return { linha: 1, data: '2026-11-05', descricao: 'CAFE DA ESQUINA', valor: -6, situacao: 'NOVA', motivo: null, marcada: true,
    futuro: false, erro: null, parecido: null, ...parcial };
}

/** A ordem da API: data mais recente primeiro; no mesmo dia, a linha de baixo do arquivo em cima. */
const previa: Previa = {
  hash: 'abc', formato: 'OFX', nomeArquivo: 'extrato.ofx', contaId: 1, contaNome: 'Banco Horizonte', dataSaldoInicial: '2026-10-01',
  dataInicio: '2026-09-29', dataFim: '2026-11-16', arquivoRepetido: null, outraConta: null, perfil: null,
  origens: [{ nomeArquivo: 'extrato-outubro.ofx', importadaEm: '2026-11-04T23:41:00Z' }],
  linhas: [
    linha({ linha: 9, data: '2026-11-16', descricao: 'DEB AUTOMATICO SEGURO', valor: -89.9, marcada: false, futuro: true }),
    linha({ linha: 4, data: '2026-11-06', descricao: 'UBER TRIP', valor: -23.9, situacao: 'PROVAVEL', marcada: false,
      parecido: { descricao: 'Uber', data: '2026-11-06', manual: true, excluido: false, criadoEm: '2026-11-06T13:00:00Z', excluidoEm: null } }),
    linha({ linha: 7 }),
    linha({ linha: 6, descricao: 'CAFÉ DA ESQUINA' }),
    linha({ linha: 3, data: '2026-09-29', descricao: 'TARIFA', valor: -29.9, situacao: 'ANTES_DO_SALDO_INICIAL', marcada: false }),
    linha({ linha: 2, data: '2026-11-01', descricao: 'SAQUE', valor: -300, situacao: 'JA_IMPORTADA', marcada: false,
      parecido: { descricao: 'SAQUE', data: '2026-11-01', manual: false, excluido: true, criadoEm: '2026-11-04T23:41:00Z',
        excluidoEm: '2026-11-06T12:00:00Z' } }),
    linha({ linha: 1, data: '2026-11-03', descricao: 'IOF ADICIONAL', valor: 0, situacao: 'FORA', motivo: 'VALOR_ZERO', marcada: false }),
  ],
};

describe('Revisao', () => {
  function criar(atual: Previa = previa, escolha: EscolhaDoSeletor | undefined = undefined) {
    const dialogo = { open: vi.fn(() => ({ afterClosed: () => of(escolha) })) };
    TestBed.configureTestingModule({ imports: [Revisao], providers: [{ provide: MatDialog, useValue: dialogo }] });
    const fixture = TestBed.createComponent(Revisao);
    fixture.componentRef.setInput('previa', atual);
    fixture.componentRef.setInput('categorias', categorias);
    fixture.componentRef.setInput('hoje', '2026-11-10');
    const emitidas: Escolha[][] = [];
    const contas: number[] = [];
    fixture.componentInstance.importar.subscribe((escolhas) => emitidas.push(escolhas));
    fixture.componentInstance.trocarConta.subscribe((conta) => contas.push(conta));
    fixture.detectChanges();
    const elemento = fixture.nativeElement as HTMLElement;
    const atualizar = () => fixture.detectChanges();
    return { fixture, elemento, atualizar, dialogo, emitidas, contas };
  }

  function textos(elemento: HTMLElement, seletor: string): string[] {
    return Array.from(elemento.querySelectorAll(seletor)).map((no) => no.textContent?.trim().replace(/\s+/g, ' ') ?? '');
  }

  /** A caixa pelo texto do <label> dela (AGENTS.md: todo campo tem label). */
  function caixa(elemento: HTMLElement, rotulo: string): HTMLInputElement {
    const label = Array.from(elemento.querySelectorAll('label.revisao__caixa')).find((item) => item.textContent?.trim() === rotulo);
    return label?.querySelector('input') as HTMLInputElement;
  }

  function botao(elemento: HTMLElement, texto: string): HTMLButtonElement {
    return Array.from(elemento.querySelectorAll<HTMLButtonElement>('button')).find((item) => item.textContent?.trim() === texto) as HTMLButtonElement;
  }

  it('começa com as novas marcadas, menos a futura, e a barra só conta (decisões 18 e 21)', () => {
    const { elemento, atualizar } = criar();
    expect(textos(elemento, '.revisao__numero')).toEqual(['4', '1', '2']);
    expect(textos(elemento, '.revisao__nota')[0]).toBe('3 novas e 1 provável duplicada');
    expect(textos(elemento, '.revisao__nome-do-grupo')).toEqual(['Novas · 3', 'Prováveis duplicadas · 1']);
    expect(textos(elemento, '.revisao__nota-do-grupo')).toEqual([
      'a futura vem desmarcada', 'vêm desmarcadas: marque só se não for o mesmo lançamento']);
    expect(caixa(elemento, 'Importar DEB AUTOMATICO SEGURO de 16/11').checked).toBe(false);
    expect(caixa(elemento, 'Importar CAFE DA ESQUINA de 05/11').checked).toBe(true);
    expect(caixa(elemento, 'Importar UBER TRIP de 06/11').checked).toBe(false);
    expect(textos(elemento, '.revisao__resumo')[0]).toBe('2 de 4 marcadas. As desmarcadas, as já importadas e as de fora não entram.');
    expect(botao(elemento, 'Importar 2 lançamentos')).toBeDefined();
    expect(elemento.textContent).toContain('Parece com "Uber", lançado à mão em 06/11.');
    expect(elemento.textContent).toContain('Agendado no banco: marque se quiser lançar.');
    expect(elemento.querySelector('#revisao-fora')).toBeNull();
    (Array.from(elemento.querySelectorAll<HTMLButtonElement>('.revisao__alternar')).find((item) => item.textContent?.startsWith('Ficam de fora')))?.click();
    atualizar();
    expect(textos(elemento, '#revisao-fora .revisao__pilula')).toEqual(['Antes do saldo inicial (01/10/2026)', 'Valor zero']);
    expect(textos(elemento, '#revisao-fora .revisao__explica-grupo')).toEqual(['Não entram e não dá para marcar. A data do saldo inicial '
      + 'muda em Contas, se o extrato estiver certo e o saldo inicial, não.']);
    expect(elemento.querySelector('#revisao-ja')).toBeNull();
    expect(Array.from(elemento.querySelectorAll('.revisao__linha input[type="checkbox"]')).every((item) => item.closest('label') !== null))
      .toBe(true);
  });

  it('marca e desmarca a linha, o grupo e todas; sem nenhuma, o botão trava', () => {
    const { elemento, atualizar } = criar();
    caixa(elemento, 'Importar UBER TRIP de 06/11').click();
    atualizar();
    expect(textos(elemento, '.revisao__resumo b')).toEqual(['3 de 4']);
    botao(elemento, 'Marcar todas').click();
    atualizar();
    expect(textos(elemento, '.revisao__resumo b')).toEqual(['4 de 4']);
    expect((elemento.querySelector('.revisao__todas input') as HTMLInputElement).checked).toBe(true);
    (elemento.querySelector('.revisao__todas input') as HTMLInputElement).click();
    atualizar();
    expect(textos(elemento, '.revisao__resumo b')).toEqual(['0 de 4']);
    const importar = botao(elemento, 'Marque ao menos uma linha.');
    expect(importar.disabled).toBe(true);
  });

  it('a categoria vem do seletor e, com "usar também", vai para as iguais; as escolhas saem com ela (decisão 20)', () => {
    const { fixture, elemento, atualizar, dialogo, emitidas } = criar(previa, { categoriaId: 5, usarNasIguais: true });
    (elemento.querySelector('button[aria-label="Categoria de CAFE DA ESQUINA: Sem categoria"]') as HTMLButtonElement).click();
    atualizar();

    const dados = (dialogo.open.mock.calls[0] as unknown as [unknown, { data: DadosDoSeletor }])[1].data;
    expect(dados.iguais).toBe(1);
    expect(dados.receita).toBe(false);
    expect(dados.opcoes).toEqual([{ id: 1, nome: 'Alimentação', sub: false }, { id: 5, nome: 'Restaurante', sub: true }]);
    expect(textos(elemento, '.revisao__categoria')).toEqual(['Sem categoria', 'Alimentação › Restaurante', 'Alimentação › Restaurante',
      'Sem categoria']);
    botao(elemento, 'Importar 2 lançamentos').click();
    expect(emitidas).toEqual([[{ linha: 7, categoriaId: 5 }, { linha: 6, categoriaId: 5 }]]);

    // Depois do CATEGORIA_INVALIDA, a página manda a lista nova: a categoria que saiu some das linhas.
    fixture.componentRef.setInput('categorias', { ...categorias, despesas: { ativas: [categoria(1, 'Alimentação', 'DESPESA')], arquivadas: [] } });
    atualizar();
    expect(textos(elemento, '.revisao__categoria')).toEqual(['Sem categoria', 'Sem categoria', 'Sem categoria', 'Sem categoria']);
    botao(elemento, 'Importar 2 lançamentos').click();
    expect(emitidas[1]).toEqual([{ linha: 7, categoriaId: null }, { linha: 6, categoriaId: null }]);
  });

  it('sem linha para importar, mostra "Nada novo" e os avisos de outra conta e de arquivo repetido', () => {
    const { elemento, contas } = criar({
      ...previa,
      linhas: previa.linhas.filter((item) => item.situacao === 'JA_IMPORTADA' || item.situacao === 'FORA'),
      outraConta: { contaId: 1, contaNome: 'Banco Horizonte', finalDaConta: '5678' },
      arquivoRepetido: { importadoEm: '2026-11-04T23:41:00Z', contaId: 1, contaNome: 'Banco Horizonte', mesmaConta: false },
    });
    expect(textos(elemento, '.flux-alerta__titulo')).toEqual([
      'Este extrato é da conta final 5678, que você já importou em Banco Horizonte',
      'Este arquivo já foi importado em 04/11/2026 às 20:41, em Banco Horizonte']);
    expect(elemento.querySelectorAll('.flux-alerta--atencao')).toHaveLength(2);
    expect(elemento.querySelector('.estado-vazio__titulo')?.textContent).toBe('Nada novo neste arquivo');
    expect(elemento.querySelector('.estado-vazio__texto')?.textContent).toBe('As 2 linhas já foram importadas ou ficam de fora.');
    expect(elemento.querySelector('.revisao__barra')).toBeNull();
    botao(elemento, 'Importar em Banco Horizonte').click();
    expect(contas).toEqual([1]);
  });

  it('"Continuar em" fecha o aviso de outra conta; as já importadas dizem de onde vieram (as 3 mais recentes); no celular, os grupos '
    + 'viram pílulas (decisão 22)', () => {
    const ja = (numero: number, data: string) => linha({ linha: numero, data, descricao: `LINHA ${numero}`, situacao: 'JA_IMPORTADA',
      marcada: false, parecido: { descricao: `LINHA ${numero}`, data, manual: false, excluido: false, criadoEm: '2026-11-04T23:41:00Z',
        excluidoEm: null } });
    const { elemento, atualizar } = criar({
      ...previa,
      contaNome: 'Poupança',
      outraConta: { contaId: 1, contaNome: 'Banco Horizonte', finalDaConta: '5678' },
      linhas: [...previa.linhas.filter((item) => item.situacao !== 'JA_IMPORTADA'), ja(20, '2026-11-03'), ja(21, '2026-11-02'),
        ja(22, '2026-11-01'), ja(23, '2026-10-31')],
    });
    botao(elemento, 'Continuar em Poupança').click();
    atualizar();
    expect(elemento.querySelector('.flux-alerta--atencao')).toBeNull();

    expect(textos(elemento, '.revisao__chip')).toEqual(['Para importar 4', 'Já importadas 4', 'Ficam de fora 2']);
    expect(elemento.querySelector('.revisao__chip')?.getAttribute('aria-pressed')).toBe('true');
    (elemento.querySelectorAll<HTMLButtonElement>('.revisao__chip')[1]).click();
    atualizar();
    expect(elemento.querySelector('.revisao__importar')?.classList).toContain('revisao__oculto-no-celular');
    expect(textos(elemento, '#revisao-ja .revisao__explica-grupo')).toEqual(['Vieram de extrato-outubro.ofx, importado em 04/11/2026, e não '
      + 'entram de novo. As 3 mais recentes:']);
    expect(textos(elemento, '#revisao-ja .revisao__curta-descricao')).toEqual(['LINHA 20', 'LINHA 21', 'LINHA 22']);
  });

  it('no celular, a pílula "Ficam de fora" aparece sempre; sem nenhuma linha de fora, diz isso (mockup, linha 1288)', () => {
    const { elemento, atualizar } = criar({ ...previa, linhas: [linha({ linha: 7 })] });
    expect(textos(elemento, '.revisao__nota')[0]).toBe('1 nova e 0 prováveis duplicadas');
    expect(textos(elemento, '.revisao__nota-do-grupo')).toEqual([]);
    expect(textos(elemento, '.revisao__chip')).toEqual(['Para importar 1', 'Ficam de fora 0']);
    (elemento.querySelectorAll<HTMLButtonElement>('.revisao__chip')[1]).click();
    atualizar();
    expect(elemento.querySelector('.revisao__importar')?.classList).toContain('revisao__oculto-no-celular');
    expect(elemento.querySelector('.revisao__nenhuma')?.textContent).toBe('Nenhuma linha fica de fora.');
  });
});
