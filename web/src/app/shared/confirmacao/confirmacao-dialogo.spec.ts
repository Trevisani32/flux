import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { Subject, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { abrirConfirmacao, DadosDaConfirmacao, ResultadoDaConfirmacao } from './confirmacao-dialogo';

describe('ConfirmacaoDialogo', () => {
  afterEach(() => TestBed.inject(MatDialog).closeAll());

  function painel(): HTMLElement {
    return document.querySelector('.mat-mdc-dialog-container') as HTMLElement;
  }

  function botao(texto: string): HTMLButtonElement | undefined {
    return Array.from(painel().querySelectorAll<HTMLButtonElement>('button')).find((item) => item.textContent?.trim() === texto);
  }

  function apertarEsc(): void {
    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', keyCode: 27, bubbles: true }));
  }

  function abrir(dados: Partial<DadosDaConfirmacao> = {}) {
    const resposta = new Subject<void>();
    const acao = vi.fn(() => resposta.asObservable());
    let resultado: ResultadoDaConfirmacao | null = null;
    abrirConfirmacao(TestBed.inject(MatDialog), {
      titulo: 'Arquivar Poupança?',
      icone: 'arquivar',
      perigo: false,
      alerta: { titulo: 'O saldo desta conta é', valor: -120, texto: 'Ele deixa de contar no saldo total.' },
      paragrafos: [['A conta sai do Flux de vez. ', { forte: 'Não dá para desfazer.' }]],
      confirmar: 'Arquivar conta',
      falha: 'Não foi possível arquivar a conta. Tente novamente.',
      acao,
      ...dados,
    }).subscribe((valor) => (resultado = valor));
    TestBed.tick();
    return { acao, resposta, resultado: () => resultado };
  }

  it('mostra título, alerta com o saldo e os trechos em negrito', () => {
    abrir();
    expect(painel().querySelector('h2')?.textContent).toBe('Arquivar Poupança?');
    const alerta = painel().querySelector('.flux-alerta--info');
    expect(alerta?.getAttribute('role')).toBe('status');
    expect(alerta?.textContent).toContain('O saldo desta conta é');
    expect(alerta?.querySelector('app-valor')?.classList).toContain('flux-valor--negativo');
    expect(alerta?.textContent).toContain('−R$\u00a0120,00');
    expect(painel().querySelector('.confirmacao__texto')?.textContent).toBe('A conta sai do Flux de vez. Não dá para desfazer.');
    expect(painel().querySelector('.confirmacao__texto b')?.textContent).toBe('Não dá para desfazer.');
    expect(painel().querySelector('.confirmacao__selo--perigo')).toBeNull();
    expect(document.querySelector('.cdk-overlay-pane')?.classList).toContain('flux-dialogo');
  });

  it('usa o painel e o botão de perigo para excluir', () => {
    abrir({ perigo: true, icone: 'lixeira', confirmar: 'Excluir conta', alerta: undefined });
    expect(document.querySelector('.cdk-overlay-pane')?.classList).toContain('flux-dialogo-perigo');
    expect(painel().querySelector('.confirmacao__selo--perigo')).not.toBeNull();
    expect(botao('Excluir conta')?.classList).toContain('flux-botao-perigo-cheio');
    expect(painel().querySelector('.flux-alerta--info')).toBeNull();
  });

  it('chama a ação, desabilita o botão e fecha com true quando ela termina', async () => {
    const { acao, resposta, resultado } = abrir();
    botao('Arquivar conta')?.click();
    TestBed.tick();
    expect(acao).toHaveBeenCalledOnce();
    expect(botao('Arquivar conta')?.disabled).toBe(true);
    botao('Arquivar conta')?.click();
    expect(acao).toHaveBeenCalledOnce();
    resposta.next();
    resposta.complete();
    TestBed.tick();
    await vi.waitFor(() => expect(resultado()).toBe(true));
  });

  it('mostra o detail de um 422 e continua aberto', () => {
    const { acao, resultado } = abrir();
    acao.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 422, error: { detail: 'Esta conta não pode ser excluída porque tem 3 lançamentos.' } })));
    botao('Arquivar conta')?.click();
    TestBed.tick();
    expect(painel().querySelector('[role="alert"]')?.textContent).toContain('Esta conta não pode ser excluída porque tem 3 lançamentos.');
    expect(botao('Arquivar conta')?.disabled).toBe(false);
    expect(resultado()).toBeNull();
  });

  it('mostra a mensagem de falha num erro sem detail útil', () => {
    const { acao } = abrir();
    acao.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500, error: { detail: 'Ocorreu um erro inesperado.' } })));
    botao('Arquivar conta')?.click();
    TestBed.tick();
    expect(painel().querySelector('[role="alert"]')?.textContent).toContain('Não foi possível arquivar a conta. Tente novamente.');
  });

  it('durante o envio, Esc e Cancelar não fecham; depois de um erro, voltam a fechar', async () => {
    const { resposta, resultado } = abrir();
    const dialogo = TestBed.inject(MatDialog);
    botao('Arquivar conta')?.click();
    TestBed.tick();
    expect(dialogo.openDialogs[0].disableClose).toBe(true);
    expect(botao('Cancelar')?.disabled).toBe(true);
    apertarEsc();
    TestBed.tick();
    expect(dialogo.openDialogs).toHaveLength(1);
    resposta.error(new HttpErrorResponse({ status: 500 }));
    TestBed.tick();
    expect(dialogo.openDialogs[0].disableClose).toBe(false);
    expect(botao('Cancelar')?.disabled).toBe(false);
    apertarEsc();
    TestBed.tick();
    await vi.waitFor(() => expect(resultado()).toBeUndefined());
  });

  it('com o código do bloqueio, troca para o bloqueio com o uso e fecha com a alternativa', async () => {
    const { acao, resultado } = abrir({
      perigo: true, icone: 'lixeira', alerta: undefined, titulo: 'Excluir Banco Horizonte?', confirmar: 'Excluir conta',
      bloqueio: {
        codigo: 'CONTA_EM_USO',
        titulo: 'Esta conta não pode ser excluída',
        paragrafo: (uso) => [{ forte: 'Banco Horizonte' }, ` tem ${uso}. Para tirá-la do saldo total e dos seletores sem perder o histórico, arquive a conta.`],
        alternativa: 'Arquivar conta',
      },
    });
    acao.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 422, error: { codigo: 'CONTA_EM_USO', uso: '34 lançamentos e 3 transferências', detail: 'Esta conta não pode ser excluída porque…' } })));
    botao('Excluir conta')?.click();
    TestBed.tick();
    expect(painel().querySelector('h2')?.textContent).toBe('Esta conta não pode ser excluída');
    expect(painel().querySelector('.confirmacao__texto')?.textContent)
      .toBe('Banco Horizonte tem 34 lançamentos e 3 transferências. Para tirá-la do saldo total e dos seletores sem perder o histórico, arquive a conta.');
    expect(painel().querySelector('.confirmacao__texto b')?.textContent).toBe('Banco Horizonte');
    expect(painel().querySelector('.confirmacao__selo--perigo')).toBeNull();
    expect(Array.from(painel().querySelectorAll('button')).map((item) => item.textContent?.trim())).toEqual(['Fechar', 'Arquivar conta']);
    botao('Arquivar conta')?.click();
    TestBed.tick();
    await vi.waitFor(() => expect(resultado()).toBe('alternativa'));
  });

  it('sem alternativa, o bloqueio só fecha; outro código continua no alerta', async () => {
    const bloqueio = { codigo: 'CONTA_EM_USO', titulo: 'Esta conta não pode ser excluída', paragrafo: (uso: string) => [`Conta salário tem ${uso}.`] };
    const { acao } = abrir({ perigo: true, alerta: undefined, confirmar: 'Excluir conta', bloqueio });
    acao.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 422, error: { codigo: 'OUTRO', detail: 'Outra recusa.' } })));
    botao('Excluir conta')?.click();
    TestBed.tick();
    expect(painel().querySelector('[role="alert"]')?.textContent).toContain('Outra recusa.');
    acao.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 422, error: { codigo: 'CONTA_EM_USO', uso: '1 lançamento' } })));
    botao('Excluir conta')?.click();
    TestBed.tick();
    expect(painel().querySelector('.confirmacao__texto')?.textContent).toBe('Conta salário tem 1 lançamento.');
    expect(Array.from(painel().querySelectorAll('button')).map((item) => item.textContent?.trim())).toEqual(['Fechar']);
  });

  it('Cancelar fecha sem chamar a ação', async () => {
    const { acao, resultado } = abrir();
    botao('Cancelar')?.click();
    TestBed.tick();
    expect(acao).not.toHaveBeenCalled();
    await vi.waitFor(() => expect(resultado()).toBeUndefined());
  });
});
