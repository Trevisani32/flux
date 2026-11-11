import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { FluxoDeRegras } from './fluxo-de-regras';
import { Regra } from './regras.service';
import { Regras } from './regras';

function regra(parcial: Partial<Regra>): Regra {
  return { id: 30, posicao: 1, condicao: 'CONTEM', texto: 'uber', contaId: null, contaNome: null, contaArquivada: false, natureza: 'DESPESA',
    categoriaId: 5, categoriaNome: 'App de transporte', categoriaMaeNome: 'Transporte', categoriaArquivada: false, descricao: null,
    pausada: false, ...parcial };
}

const eats = regra({ id: 1, posicao: 1, texto: 'uber eats', categoriaId: 3, categoriaNome: 'Delivery', categoriaMaeNome: 'Alimentação' });
const uber = regra({ id: 2, posicao: 2, descricao: 'Uber' });
const rend = regra({ id: 3, posicao: 3, condicao: 'COMECA_COM', texto: 'rend pago', natureza: 'RECEITA', contaId: 9, contaNome: 'Poupança antiga',
  contaArquivada: true, categoriaId: 8, categoriaNome: 'Rendimentos', categoriaMaeNome: null, pausada: true });

describe('Regras', () => {
  let httpTesting: HttpTestingController;

  afterEach(() => {
    httpTesting.verify();
    TestBed.inject(MatDialog).closeAll();
  });

  function criar() {
    const fluxo = {
      novaRegra: vi.fn(() => of(regra({ id: 40 }))),
      editar: vi.fn(() => of({ tipo: 'salva' as const, regra: uber })),
      aplicar: vi.fn(() => of(3)),
    };
    TestBed.configureTestingModule({
      imports: [Regras],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: FluxoDeRegras, useValue: fluxo }],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(Regras);
    fixture.detectChanges();
    const elemento = fixture.nativeElement as HTMLElement;
    const atualizar = () => fixture.detectChanges();
    return { fixture, elemento, atualizar, fluxo };
  }

  function textos(elemento: ParentNode, seletor: string): string[] {
    return Array.from(elemento.querySelectorAll(seletor)).map((no) => no.textContent?.trim().replace(/\s+/g, ' ') ?? '');
  }

  function botao(elemento: ParentNode, nome: string): HTMLButtonElement {
    return Array.from(elemento.querySelectorAll<HTMLButtonElement>('button'))
      .find((item) => (item.getAttribute('aria-label') ?? item.textContent?.trim()) === nome) as HTMLButtonElement;
  }

  function abrirMenu(elemento: HTMLElement, atualizar: () => void, nome: string): HTMLButtonElement[] {
    botao(elemento, nome).click();
    atualizar();
    return Array.from(document.querySelectorAll<HTMLButtonElement>('.mat-mdc-menu-panel button'));
  }

  it('lista as regras em ordem, com a condição, a categoria, a meta e a pausada; sem nenhuma, o vazio leva a "Nova regra"', () => {
    const { elemento, atualizar, fluxo } = criar();
    httpTesting.expectOne({ method: 'GET', url: '/api/regras' }).flush([eats, uber, rend]);
    atualizar();
    expect(elemento.querySelector('.cabecalho__subtitulo')?.textContent?.trim())
      .toBe('A primeira que casar com a descrição escolhe a categoria. 3 regras.');
    expect(textos(elemento, '.regras__posicao')).toEqual(['1', '2', '3']);
    expect(textos(elemento, '.regras__condicao')).toEqual(['1ª: Contém uber eats, categoria Alimentação › Delivery',
      '2ª: Contém uber, categoria Transporte › App de transporte', '3ª: Começa com rend pago, categoria Rendimentos']);
    expect(textos(elemento, '.regras__meta')).toEqual(['Despesas · todas as contas', 'Despesas · todas as contas · descrição vira "Uber"',
      'Pausada: Poupança antiga está arquivada Receitas · só Poupança antiga']);
    expect(elemento.querySelectorAll('.regras__item--pausada')).toHaveLength(1);
    expect(botao(elemento, 'Subir a regra contém "uber eats"').disabled).toBe(true);
    expect(botao(elemento, 'Descer a regra começa com "rend pago"').disabled).toBe(true);
    expect(botao(elemento, 'Descer a regra contém "uber eats"').disabled).toBe(false);
    TestBed.resetTestingModule();

    const vazio = criar();
    httpTesting.expectOne({ method: 'GET', url: '/api/regras' }).flush([]);
    vazio.atualizar();
    expect(vazio.elemento.querySelector('.estado-vazio__titulo')?.textContent).toBe('Nenhuma regra ainda');
    expect(botao(vazio.elemento, 'Aplicar regras')).toBeUndefined();
    vazio.elemento.querySelector<HTMLButtonElement>('app-estado-vazio button')?.click();
    expect(vazio.fluxo.novaRegra).toHaveBeenCalledWith();
    httpTesting.expectOne({ method: 'GET', url: '/api/regras' }).flush([regra({ id: 40 })]);
    expect(fluxo.novaRegra).not.toHaveBeenCalled();
  });

  it('Subir e Descer mandam a lista inteira e travam até a resposta; duas mudanças seguidas usam a ordem nova (decisão 9)', () => {
    const { elemento, atualizar } = criar();
    httpTesting.expectOne({ method: 'GET', url: '/api/regras' }).flush([eats, uber, rend]);
    atualizar();

    botao(elemento, 'Descer a regra contém "uber eats"').click();
    atualizar();
    const primeira = httpTesting.expectOne({ method: 'PUT', url: '/api/regras/ordem' });
    expect(primeira.request.body).toEqual({ ids: [2, 1, 3] });
    expect(Array.from(elemento.querySelectorAll<HTMLButtonElement>('.regras__mover')).every((item) => item.disabled)).toBe(true);
    botao(elemento, 'Subir a regra começa com "rend pago"').click();
    httpTesting.expectNone({ method: 'PUT', url: '/api/regras/ordem' });
    primeira.flush([{ ...uber, posicao: 1 }, { ...eats, posicao: 2 }, rend]);
    atualizar();
    expect(textos(elemento, '.regras__condicao')[0]).toBe('1ª: Contém uber, categoria Transporte › App de transporte');

    botao(elemento, 'Descer a regra contém "uber eats"').click();
    atualizar();
    const segunda = httpTesting.expectOne({ method: 'PUT', url: '/api/regras/ordem' });
    expect(segunda.request.body).toEqual({ ids: [2, 3, 1] });
    segunda.flush([{ ...uber, posicao: 1 }, { ...rend, posicao: 2 }, { ...eats, posicao: 3 }]);
    atualizar();
    expect(textos(elemento, '.regras__posicao')).toEqual(['1', '2', '3']);
    expect(textos(elemento, '.regras__condicao')[2]).toBe('3ª: Contém uber eats, categoria Alimentação › Delivery');
  });

  it('a ordem recusada porque a lista mudou avisa e recarrega; outro erro só avisa', () => {
    const { elemento, atualizar } = criar();
    httpTesting.expectOne({ method: 'GET', url: '/api/regras' }).flush([eats, uber]);
    atualizar();
    botao(elemento, 'Subir a regra contém "uber"').click();
    httpTesting.expectOne({ method: 'PUT', url: '/api/regras/ordem' }).flush({ codigo: 'ORDEM_DESATUALIZADA',
      detail: 'A lista de regras mudou. Recarregue a página e tente de novo.' }, { status: 422, statusText: 'Unprocessable Entity' });
    httpTesting.expectOne({ method: 'GET', url: '/api/regras' }).flush([eats, uber]);
    atualizar();
    expect(textos(elemento, '[role="alert"] .flux-alerta__texto')).toEqual(['A lista de regras mudou. Recarregue a página e tente de novo.']);

    botao(elemento, 'Subir a regra contém "uber"').click();
    httpTesting.expectOne({ method: 'PUT', url: '/api/regras/ordem' }).flush(null, { status: 500, statusText: 'Erro' });
    atualizar();
    expect(textos(elemento, '[role="alert"] .flux-alerta__texto')).toEqual(['Não foi possível mudar a ordem. Tente novamente.']);
    expect(botao(elemento, 'Subir a regra contém "uber"').disabled).toBe(false);
  });

  it('o erro ao carregar tem "Tentar de novo"; o menu edita, aplica a regra e exclui com confirmação', async () => {
    const { elemento, atualizar, fluxo } = criar();
    httpTesting.expectOne({ method: 'GET', url: '/api/regras' }).flush(null, { status: 500, statusText: 'Erro' });
    atualizar();
    expect(textos(elemento, '.flux-alerta__titulo')).toEqual(['Não foi possível carregar as regras.']);
    botao(elemento, 'Tentar de novo').click();
    httpTesting.expectOne({ method: 'GET', url: '/api/regras' }).flush([eats, uber, rend]);
    atualizar();

    const itens = abrirMenu(elemento, atualizar, 'Ações da regra contém "uber"');
    expect(itens.map((item) => item.textContent?.trim())).toEqual(['Editar', 'Subir para 1ª', 'Descer para 3ª', 'Aplicar esta regra', 'Excluir']);
    itens[3].click();
    expect(fluxo.aplicar).toHaveBeenCalledWith(2);

    abrirMenu(elemento, atualizar, 'Ações da regra contém "uber"')[0].click();
    expect(fluxo.editar).toHaveBeenCalledWith(uber);
    httpTesting.expectOne({ method: 'GET', url: '/api/regras' }).flush([eats, uber, rend]);
    atualizar();

    abrirMenu(elemento, atualizar, 'Ações da regra contém "uber"')[4].click();
    TestBed.tick();
    const confirmacao = document.querySelector('app-confirmacao-dialogo') as HTMLElement;
    expect(confirmacao.querySelector('h2')?.textContent?.trim()).toBe('Excluir a regra contém "uber"?');
    Array.from(confirmacao.querySelectorAll<HTMLButtonElement>('button')).find((item) => item.textContent?.trim() === 'Excluir regra')?.click();
    TestBed.tick();
    httpTesting.expectOne({ method: 'DELETE', url: '/api/regras/2' }).flush(null);
    TestBed.tick();
    await vi.waitFor(() => httpTesting.expectOne({ method: 'GET', url: '/api/regras' }).flush([eats, { ...rend, posicao: 2 }]));
    atualizar();
    // A pausada não oferece "Aplicar esta regra", e a última não desce.
    expect(abrirMenu(elemento, atualizar, 'Ações da regra começa com "rend pago"').map((item) => item.textContent?.trim()))
      .toEqual(['Editar', 'Subir para 1ª', 'Excluir']);
  });
});
