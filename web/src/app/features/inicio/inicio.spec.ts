import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { describe, expect, it } from 'vitest';

import { AuthService } from '../../core/auth/auth.service';
import { Usuario } from '../../core/auth/sessao';
import { AGORA } from '../../core/tempo/agora';
import { ContasService, ListaDeContas } from '../contas/contas.service';
import { Inicio } from './inicio';

const comContas: ListaDeContas = {
  ativas: [
    { id: 1, nome: 'Banco Horizonte', tipo: 'CORRENTE', saldoInicial: 4218.4, dataSaldoInicial: '2026-10-01', cor: 'ROXO', saldoAtual: 4218.4, arquivadaEm: null },
    { id: 2, nome: 'Reserva de emergência', tipo: 'INVESTIMENTO', saldoInicial: 15000, dataSaldoInicial: '2026-08-15', cor: null, saldoAtual: 15000, arquivadaEm: null },
  ],
  arquivadas: [],
  totais: { geral: 19218.4, disponivel: 4218.4, investimentos: 15000 },
};

describe('Inicio', () => {
  function criar(resposta: () => Observable<ListaDeContas>) {
    const auth = { usuario: signal<Usuario | null>({ id: 1, nome: 'Dev', email: 'dev@flux.local' }) };
    TestBed.configureTestingModule({
      imports: [Inicio],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: AGORA, useValue: () => new Date('2026-11-07T02:30:00Z') },
        { provide: ContasService, useValue: { listar: resposta } },
      ],
    });
    const fixture = TestBed.createComponent(Inicio);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('mostra a saudação, a data e os itens em breve', () => {
    const elemento = criar(() => of(comContas));
    expect(elemento.querySelector('h1')?.textContent).toBe('Olá, Dev');
    expect(elemento.querySelector('p.cabecalho__subtitulo')?.textContent).toBe('Sexta-feira, 6 de novembro de 2026');
    expect([...elemento.querySelectorAll('.em-breve h3')].map((titulo) => titulo.textContent)).toEqual([
      'Lançamentos e transferências',
      'Importação de extratos',
      'Cartões de crédito',
    ]);
    expect([...elemento.querySelectorAll('.em-breve .flux-pilula')].map((pilula) => pilula.textContent)).toEqual(['Em breve', 'Em breve', 'Em breve']);
  });

  it('com contas, mostra o saldo total, os investimentos e a lista com "Ver todas"', () => {
    const elemento = criar(() => of(comContas));
    expect(elemento.querySelector('app-saldo-em-destaque .flux-cartao-destaque__valor .valor__texto')?.textContent).toBe('R$ 19.218,40');
    expect(elemento.querySelector('app-saldo-em-destaque .flux-cartao-destaque__rodape')?.textContent).toContain('2 contas ativas');
    expect(elemento.querySelector('.flux-cartao-destaque__pilula')?.textContent).toBe('Investimentos: R$ 15.000,00');
    expect([...elemento.querySelectorAll('.inicio-conta__nome')].map((nome) => nome.textContent)).toEqual(['Banco Horizonte', 'Reserva de emergência']);
    expect(elemento.querySelector('a[href="/contas"]')?.textContent).toBe('Ver todas');
    expect(elemento.querySelector('app-estado-vazio')).toBeNull();
  });

  it('total e investimentos negativos passam pelo app-valor, em vermelho', () => {
    const elemento = criar(() => of({ ...comContas, totais: { geral: -320, disponivel: -200, investimentos: -120 } }));
    const total = elemento.querySelector('app-saldo-em-destaque .flux-cartao-destaque__valor app-valor');
    expect(total?.classList).toContain('flux-valor--negativo');
    expect(total?.querySelector('.valor__texto')?.textContent).toBe('−R$ 320,00');
    const investimentos = elemento.querySelector('.flux-cartao-destaque__pilula app-valor');
    expect(investimentos?.classList).toContain('flux-valor--negativo');
    expect(investimentos?.querySelector('.valor__texto')?.textContent).toBe('−R$ 120,00');
    expect(investimentos?.querySelector('.flux-visualmente-oculto')?.textContent).toBe('menos R$ 120,00');
  });

  it('sem investimento, não mostra a pílula de investimentos', () => {
    const elemento = criar(() => of({ ...comContas, ativas: [comContas.ativas[0]] }));
    expect(elemento.querySelector('.flux-cartao-destaque__pilula')).toBeNull();
    expect(elemento.querySelector('app-saldo-em-destaque .flux-cartao-destaque__rodape')?.textContent).toContain('1 conta ativa');
  });

  it('sem contas ativas, convida a cadastrar a primeira', () => {
    const elemento = criar(() => of({ ativas: [], arquivadas: [], totais: { geral: 0, disponivel: 0, investimentos: 0 } }));
    expect(elemento.querySelector('app-estado-vazio h2')?.textContent).toBe('Comece pelas suas contas');
    expect(elemento.querySelector('app-estado-vazio p')?.textContent).toContain('As categorias padrão já estão prontas para usar.');
    expect(elemento.querySelector('app-estado-vazio a')?.getAttribute('href')).toBe('/contas?nova=conta');
    expect(elemento.querySelector('app-saldo-em-destaque')).toBeNull();
  });

  it('se a lista falhar, avisa e continua mostrando o resto', () => {
    const elemento = criar(() => throwError(() => new HttpErrorResponse({ status: 500 })));
    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('Não foi possível carregar o saldo das contas.');
    expect(elemento.querySelectorAll('.em-breve h3')).toHaveLength(3);
  });
});
