import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter, Router } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { AuthService } from '../../core/auth/auth.service';
import { ICONES } from '../../shared/icone/icones';
import { ExcluirContaDialogo } from './excluir-conta-dialogo/excluir-conta-dialogo';
import { Conta, MinhaContaService } from './minha-conta.service';
import { MinhaConta } from './minha-conta';

const conta: Conta = {
  id: 1,
  nome: 'Ana',
  email: 'ana@teste.local',
  trocaDeEmailPendente: { email: 'novo@teste.local', expiraEm: '2026-11-08T01:40:00Z' },
  termos: { versao: '2026-11-06', aceitosEm: '2026-11-06T12:00:00Z' },
};

describe('MinhaConta', () => {
  function criar(resposta: () => Observable<Conta> = () => of(conta)) {
    const servico = { carregar: vi.fn(resposta), renomear: vi.fn(() => of(conta)), pedirTrocaDeEmail: vi.fn(() => of(conta)), excluir: vi.fn() };
    const auth = { atualizarUsuario: vi.fn(), limparSessao: vi.fn() };
    const dialogo = { open: vi.fn((): { afterClosed: () => Observable<unknown> } => ({ afterClosed: () => of(false) })) };
    const snackBar = { open: vi.fn() };
    TestBed.configureTestingModule({
      imports: [MinhaConta],
      providers: [provideRouter([]), { provide: MinhaContaService, useValue: servico }, { provide: AuthService, useValue: auth }, { provide: MatDialog, useValue: dialogo }, { provide: MatSnackBar, useValue: snackBar }],
    });
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(MinhaConta); fixture.detectChanges();
    return { componente: fixture.componentInstance, elemento: fixture.nativeElement as HTMLElement, atualizar: () => fixture.detectChanges(), servico, auth, dialogo, snackBar, navegar };
  }

  it('mostra conta, pendência, termos e atualiza usuário autenticado', () => {
    const { elemento, auth } = criar();
    expect(elemento.querySelector<HTMLInputElement>('#minha-conta-nome')?.value).toBe('Ana');
    expect(elemento.textContent).toContain('ana@teste.local');
    expect(elemento.textContent).toContain('Confirmado');
    expect(elemento.textContent).toContain('Troca pendente para novo@teste.local');
    expect(elemento.textContent).toContain('07/11/2026 às 22:40');
    expect(elemento.textContent).toContain('Você aceitou a versão de 6 de novembro de 2026.');
    expect(auth.atualizarUsuario).toHaveBeenCalledWith({ id: 1, nome: 'Ana', email: 'ana@teste.local' });
  });

  it('omite a nota de termos inexistentes', () => {
    const { elemento } = criar(() => of({ ...conta, termos: null }));
    expect(elemento.textContent).not.toContain('Você aceitou a versão');
  });

  it('salva nome e mostra confirmação transitória', () => {
    const { componente, servico, snackBar } = criar();
    componente.formularioNome.setValue({ nome: 'Ana Nova' }); componente.salvarNome();
    expect(servico.renomear).toHaveBeenCalledWith('Ana Nova');
    expect(snackBar.open).toHaveBeenCalledWith('Nome salvo.', 'Fechar', { duration: 5000 });
  });

  it('pede troca de email, atualiza pendência e limpa formulário', () => {
    const atualizada = { ...conta, trocaDeEmailPendente: { email: 'outro@teste.local', expiraEm: '2026-11-08T01:40:00Z' } };
    const { componente, elemento, servico, atualizar } = criar();
    servico.pedirTrocaDeEmail.mockReturnValue(of(atualizada));
    componente.formularioEmail.setValue({ novoEmail: 'outro@teste.local', senhaAtual: 'senha' }); componente.pedirTrocaDeEmail(); atualizar();
    expect(servico.pedirTrocaDeEmail).toHaveBeenCalledWith('outro@teste.local', 'senha');
    expect(elemento.textContent).toContain('Troca pendente para outro@teste.local');
    expect(componente.formularioEmail.getRawValue()).toEqual({ novoEmail: '', senhaAtual: '' });
  });

  it('mostra detalhe de 422 ao pedir troca', () => {
    const { componente, elemento, servico, atualizar } = criar();
    servico.pedirTrocaDeEmail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 422, error: { detail: 'Senha atual incorreta.' } })));
    componente.formularioEmail.setValue({ novoEmail: 'novo@teste.local', senhaAtual: 'senha' }); componente.pedirTrocaDeEmail(); atualizar();
    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('Senha atual incorreta.');
  });

  it('mostra limite, mantém email e habilita botão após 429', () => {
    const { componente, elemento, servico, atualizar } = criar();
    servico.pedirTrocaDeEmail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 429 })));
    componente.formularioEmail.setValue({ novoEmail: 'novo@teste.local', senhaAtual: 'senha' }); componente.pedirTrocaDeEmail(); atualizar();
    const alerta = elemento.querySelector('[role="alert"]');
    expect(alerta?.classList.contains('flux-alerta--erro')).toBe(true);
    expect(alerta?.textContent).toContain('Muitas tentativas. Tente de novo em alguns minutos.');
    const caminhos = Array.from(alerta?.querySelector('app-icone')?.querySelectorAll('path') ?? []).map((caminho) => caminho.getAttribute('d'));
    expect(caminhos).toEqual([...ICONES.relogio]);
    expect(componente.formularioEmail.controls.novoEmail.value).toBe('novo@teste.local');
    const botao = Array.from(elemento.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent?.trim() === 'Enviar link de confirmação');
    expect(botao).toBeDefined();
    expect(botao?.disabled).toBe(false);
  });

  it('abre diálogo de exclusão com os dados e navega quando confirmado', () => {
    const resposta = new Subject<boolean>();
    const { componente, dialogo, auth, navegar } = criar();
    dialogo.open.mockReturnValue({ afterClosed: () => resposta });
    componente.abrirExclusao();
    expect(dialogo.open).toHaveBeenCalledWith(ExcluirContaDialogo, expect.objectContaining({ data: { email: 'ana@teste.local' }, panelClass: 'flux-dialogo-perigo', autoFocus: '#excluir-conta-senha' }));
    resposta.next(true);
    expect(auth.limparSessao).toHaveBeenCalled();
    expect(navegar).toHaveBeenCalledWith(['/entrar'], { state: { aviso: 'conta-excluida' } });
  });

  it('não faz nada quando o diálogo não confirma exclusão', () => {
    const { componente, auth, dialogo, navegar } = criar();
    dialogo.open.mockReturnValue({ afterClosed: () => of('') });
    componente.abrirExclusao();
    expect(auth.limparSessao).not.toHaveBeenCalled(); expect(navegar).not.toHaveBeenCalled();
  });

  it('oferece nova tentativa quando carregar falha', () => {
    const { elemento, servico, atualizar } = criar(() => throwError(() => new Error('falha')));
    expect(elemento.textContent).toContain('Não foi possível carregar a sua conta.');
    const botao = Array.from(elemento.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent?.trim() === 'Tentar de novo');
    expect(botao).toBeDefined();
    botao?.click(); atualizar();
    expect(servico.carregar).toHaveBeenCalledTimes(2);
  });
});
