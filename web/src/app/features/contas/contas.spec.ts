import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { AGORA } from '../../core/tempo/agora';
import { ConfirmacaoDialogo, DadosDaConfirmacao } from '../../shared/confirmacao/confirmacao-dialogo';
import { ContaDialogo } from './conta-dialogo/conta-dialogo';
import { Conta, ContasService, ListaDeContas } from './contas.service';
import { Contas } from './contas';

function conta(parcial: Partial<Conta>): Conta {
  return { id: 1, nome: 'Conta', tipo: 'CORRENTE', saldoInicial: 0, dataSaldoInicial: '2026-10-01', cor: null, saldoAtual: 0, arquivadaEm: null, ...parcial };
}

const lista: ListaDeContas = {
  ativas: [
    conta({ id: 1, nome: 'Banco Horizonte', cor: 'ROXO', saldoInicial: 4218.4, saldoAtual: 4218.4 }),
    conta({ id: 2, nome: 'Carteira', tipo: 'CARTEIRA', dataSaldoInicial: '2026-11-01', saldoInicial: 180, saldoAtual: 180 }),
    conta({ id: 3, nome: 'Conta conjunta', cor: 'ROSA', dataSaldoInicial: '2026-09-01', saldoInicial: -120, saldoAtual: -120 }),
    conta({ id: 4, nome: 'Reserva de emergência', tipo: 'INVESTIMENTO', saldoInicial: 15000, saldoAtual: 15000 }),
  ],
  arquivadas: [conta({ id: 5, nome: 'Conta salário', arquivadaEm: '2026-11-03T15:00:00Z' })],
  totais: { geral: 19278.4, disponivel: 4278.4, investimentos: 15000 },
};

describe('Contas', () => {
  function criar(resposta: () => Observable<ListaDeContas> = () => of(lista), nova: string | null = null) {
    const servico = {
      listar: vi.fn(resposta),
      arquivar: vi.fn(() => of(conta({}))),
      reativar: vi.fn((): Observable<Conta> => of(conta({}))),
      excluir: vi.fn(() => of(undefined)),
    };
    // Um Subject por diálogo aberto: fechamentos[n] fecha o n-ésimo.
    const fechamentos: Subject<unknown>[] = [];
    const dialogo = { open: vi.fn((): { afterClosed: () => Observable<unknown> } => {
      const fechamento = new Subject<unknown>();
      fechamentos.push(fechamento);
      return { afterClosed: () => fechamento.asObservable() };
    }) };
    const acaoDoAviso = new Subject<void>();
    const snackBar = { open: vi.fn(() => ({ onAction: () => acaoDoAviso.asObservable() })) };
    TestBed.configureTestingModule({
      imports: [Contas],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(nova === null ? {} : { nova }) } } },
        { provide: ContasService, useValue: servico },
        { provide: MatDialog, useValue: dialogo },
        { provide: MatSnackBar, useValue: snackBar },
        { provide: AGORA, useValue: () => new Date('2026-11-09T15:00:00Z') },
      ],
    });
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(Contas);
    fixture.detectChanges();
    const elemento = fixture.nativeElement as HTMLElement;
    return { componente: fixture.componentInstance, elemento, atualizar: () => fixture.detectChanges(), servico, dialogo, fechamentos, snackBar, acaoDoAviso, navegar };
  }

  function textos(elemento: HTMLElement, seletor: string): string[] {
    return Array.from(elemento.querySelectorAll(seletor)).map((item) => item.textContent?.trim() ?? '');
  }

  it('mostra o saldo total, as duas partes e as contas na ordem da API', () => {
    const { elemento } = criar();
    expect(elemento.querySelector('h1')?.textContent).toBe('Contas');
    expect(elemento.querySelector('app-saldo-em-destaque .flux-cartao-destaque__valor .valor__texto')?.textContent).toBe('R$\u00a019.278,40');
    expect(elemento.querySelector('app-saldo-em-destaque .flux-cartao-destaque__rodape')?.textContent).toContain('4 contas ativas');
    expect(elemento.querySelector('app-saldo-em-destaque .flux-cartao-destaque__pilula .flux-so-desktop')?.textContent).toBe('09/11/2026');
    expect(elemento.querySelector('app-saldo-em-destaque .flux-cartao-destaque__pilula .flux-so-celular')?.textContent).toBe('09/11');
    expect(textos(elemento, '.contas-resumo__rotulo')).toEqual(['Disponível', 'Investimentos']);
    expect(textos(elemento, '.contas-resumo__valor')).toEqual(['R$\u00a04.278,40', 'R$\u00a015.000,00']);
    expect(textos(elemento, '.flux-lista-card .conta__nome')).toEqual(['Banco Horizonte', 'Carteira', 'Conta conjunta', 'Reserva de emergência']);
    expect(textos(elemento, '.flux-lista-card .conta__meta')[0]).toBe('Conta corrente · desde 01/10/2026');
    expect(textos(elemento, '.flux-lista-card .conta__meta')[3]).toBe('Investimento · desde 01/10/2026');
  });

  it('mostra saldo negativo em vermelho com "−" e a cor da conta no selo', () => {
    const { elemento } = criar();
    const conjunta = elemento.querySelectorAll('.flux-lista-card .conta')[2];
    expect(conjunta.querySelector('app-valor')?.classList).toContain('flux-valor--negativo');
    expect(conjunta.querySelector('app-valor')?.textContent).toContain('−R$\u00a0120,00');
    expect(conjunta.querySelector<HTMLElement>('.conta__selo')?.style.getPropertyValue('--flux-cor')).toBe('var(--flux-cor-conta-rosa)');
    const carteira = elemento.querySelectorAll('.flux-lista-card .conta')[1];
    expect(carteira.querySelector<HTMLElement>('.conta__selo')?.style.getPropertyValue('--flux-cor')).toBe('');
  });

  it('as arquivadas começam recolhidas e abrem no botão', () => {
    const { elemento, atualizar } = criar();
    const alternar = elemento.querySelector<HTMLButtonElement>('.flux-arquivadas__alternar');
    expect(alternar?.getAttribute('aria-expanded')).toBe('false');
    expect(alternar?.textContent).toContain('1');
    expect(elemento.querySelector<HTMLElement>('#contas-arquivadas')?.hidden).toBe(true);
    alternar?.click();
    atualizar();
    expect(alternar?.getAttribute('aria-expanded')).toBe('true');
    expect(elemento.querySelector<HTMLElement>('#contas-arquivadas')?.hidden).toBe(false);
    expect(elemento.querySelector('.conta--arquivada .conta__meta')?.textContent).toContain('Conta corrente · arquivada em 03/11/2026');
  });

  it('sem conta ativa mostra o estado vazio, sem o resumo', () => {
    const { elemento, dialogo } = criar(() => of({ ativas: [], arquivadas: [], totais: { geral: 0, disponivel: 0, investimentos: 0 } }));
    expect(elemento.querySelector('app-estado-vazio h2')?.textContent).toBe('Nenhuma conta ainda');
    expect(elemento.querySelector('app-saldo-em-destaque')).toBeNull();
    expect(elemento.querySelector('.flux-arquivadas')).toBeNull();
    Array.from(elemento.querySelectorAll<HTMLButtonElement>('app-estado-vazio button')).find((b) => b.textContent?.trim() === 'Cadastrar conta')?.click();
    expect(dialogo.open).toHaveBeenCalledWith(ContaDialogo, expect.objectContaining({ data: { conta: null, hoje: '2026-11-09' }, panelClass: 'flux-dialogo', autoFocus: '#conta-nome' }));
  });

  it('mostra o erro de carregamento e tenta de novo', () => {
    const { elemento, atualizar, servico } = criar(() => throwError(() => new HttpErrorResponse({ status: 500 })));
    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('Não foi possível carregar as contas.');
    servico.listar.mockReturnValue(of(lista));
    Array.from(elemento.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent?.trim() === 'Tentar de novo')?.click();
    atualizar();
    expect(elemento.querySelector('[role="alert"]')).toBeNull();
    expect(elemento.querySelectorAll('.flux-lista-card .conta')).toHaveLength(4);
  });

  it('arquiva direto a conta com saldo zero e oferece Desfazer', () => {
    const { componente, servico, snackBar, acaoDoAviso, dialogo } = criar();
    const zerada = conta({ id: 8, nome: 'Conta salário', saldoAtual: 0 });
    componente.arquivar(zerada);
    expect(dialogo.open).not.toHaveBeenCalled();
    expect(servico.arquivar).toHaveBeenCalledWith(8);
    expect(servico.listar).toHaveBeenCalledTimes(2);
    expect(snackBar.open).toHaveBeenCalledWith('Conta salário arquivada.', 'Desfazer', { duration: 8000 });
    acaoDoAviso.next();
    expect(servico.reativar).toHaveBeenCalledWith(8);
  });

  it('pede confirmação, com o saldo, para arquivar a conta com saldo', () => {
    const { componente, dialogo, servico, fechamentos } = criar();
    componente.arquivar(lista.ativas[2]);
    expect(servico.arquivar).not.toHaveBeenCalled();
    const [componenteDoDialogo, configuracao] = dialogo.open.mock.calls[0] as unknown as [unknown, { data: DadosDaConfirmacao; panelClass: string }];
    expect(componenteDoDialogo).toBe(ConfirmacaoDialogo);
    expect(configuracao.panelClass).toBe('flux-dialogo');
    expect(configuracao.data.titulo).toBe('Arquivar Conta conjunta?');
    expect(configuracao.data.alerta).toEqual({ titulo: 'O saldo desta conta é', valor: -120, texto: 'Ele deixa de contar no saldo total.' });
    expect(configuracao.data.confirmar).toBe('Arquivar conta');
    configuracao.data.acao().subscribe();
    expect(servico.arquivar).toHaveBeenCalledWith(3);
    fechamentos[0].next(true);
    expect(servico.listar).toHaveBeenCalledTimes(2);
  });

  it('confirma a exclusão com o diálogo de perigo', () => {
    const { componente, dialogo, servico } = criar();
    componente.excluir(lista.ativas[0]);
    const configuracao = (dialogo.open.mock.calls[0] as unknown as [unknown, { data: DadosDaConfirmacao; panelClass: string }])[1];
    expect(configuracao.panelClass).toBe('flux-dialogo-perigo');
    expect(configuracao.data.titulo).toBe('Excluir Banco Horizonte?');
    expect(configuracao.data.paragrafos).toEqual([['A conta sai do Flux de vez. ', { forte: 'Não dá para desfazer.' }]]);
    configuracao.data.acao().subscribe();
    expect(servico.excluir).toHaveBeenCalledWith(1);
  });

  it('Ver lançamentos abre a lista do mês filtrada pela conta', () => {
    const { componente, navegar } = criar();
    componente.verLancamentos(lista.ativas[2]);
    expect(navegar).toHaveBeenCalledWith(['/lancamentos'], { queryParams: { conta: 3 } });
  });

  it('Importar extrato abre a importação já com a conta (Fatia 04a)', () => {
    const { componente, navegar } = criar();
    componente.importarExtrato(lista.ativas[2]);
    expect(navegar).toHaveBeenCalledWith(['/importacao'], { queryParams: { conta: 3 } });
  });

  it('o bloqueio da exclusão leva a arquivar a conta; na arquivada, só fecha', () => {
    const { componente, dialogo, fechamentos } = criar();
    componente.excluir(lista.ativas[0]);
    const bloqueio = (dialogo.open.mock.calls[0] as unknown as [unknown, { data: DadosDaConfirmacao }])[1].data.bloqueio;
    expect(bloqueio?.codigo).toBe('CONTA_EM_USO');
    expect(bloqueio?.titulo).toBe('Esta conta não pode ser excluída');
    expect(bloqueio?.alternativa).toBe('Arquivar conta');
    expect(bloqueio?.paragrafo('34 lançamentos e 3 transferências')).toEqual([{ forte: 'Banco Horizonte' },
      ' tem 34 lançamentos e 3 transferências. Para tirá-la do saldo total e dos seletores sem perder o histórico, arquive a conta.']);
    fechamentos[0].next('alternativa');
    expect((dialogo.open.mock.calls[1] as unknown as [unknown, { data: DadosDaConfirmacao }])[1].data.titulo).toBe('Arquivar Banco Horizonte?');

    componente.excluir(lista.arquivadas[0]);
    const daArquivada = (dialogo.open.mock.calls[2] as unknown as [unknown, { data: DadosDaConfirmacao }])[1].data.bloqueio;
    expect(daArquivada?.alternativa).toBeUndefined();
    expect(daArquivada?.paragrafo('1 lançamento')).toEqual([{ forte: 'Conta salário' }, ' tem 1 lançamento. Ela continua arquivada, com o histórico nos relatórios.']);
  });

  it('mostra o erro de reativar dentro das arquivadas e as abre', () => {
    const { componente, elemento, atualizar, servico } = criar();
    servico.reativar.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 422,
      error: { codigo: 'NOME_EM_USO', detail: 'Já existe uma conta ativa com este nome. Renomeie a conta ativa e tente de novo.' },
    })));
    componente.reativar(lista.arquivadas[0]);
    atualizar();
    expect(elemento.querySelector<HTMLElement>('#contas-arquivadas')?.hidden).toBe(false);
    const alerta = elemento.querySelector('#contas-arquivadas [role="alert"]');
    expect(alerta?.textContent).toContain('Não foi possível reativar');
    expect(alerta?.textContent).toContain('Renomeie a conta ativa e tente de novo.');
  });

  it('abre a Nova conta quando chega do Início com ?nova=conta e tira o parâmetro', () => {
    const { dialogo, navegar } = criar(() => of(lista), 'conta');
    expect(dialogo.open).toHaveBeenCalledOnce();
    expect(navegar).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { nova: null }, replaceUrl: true }));
  });

  it('recarrega a lista depois de salvar no diálogo, e não depois de cancelar', () => {
    const { componente, servico, fechamentos } = criar();
    componente.editar(lista.ativas[0]);
    fechamentos[0].next(undefined);
    expect(servico.listar).toHaveBeenCalledTimes(1);
    componente.novaConta();
    fechamentos[1].next(lista.ativas[0]);
    expect(servico.listar).toHaveBeenCalledTimes(2);
  });
});
