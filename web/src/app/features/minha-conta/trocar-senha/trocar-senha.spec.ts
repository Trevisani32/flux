import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { authInterceptor } from '../../../core/auth/auth.interceptor';
import { AuthService } from '../../../core/auth/auth.service';
import { TrocarSenha } from './trocar-senha';

describe('TrocarSenha', () => {
  const auth = {
    tokenAcesso: () => 'token-falso',
    renovar: vi.fn(() => of(undefined)),
    limparSessao: vi.fn(),
  };
  const snackBar = { open: vi.fn() };

  let componente: TrocarSenha;
  let elemento: HTMLElement;
  let atualizar: () => void;
  let httpTesting: HttpTestingController;
  let navegar: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    auth.renovar.mockClear();
    auth.limparSessao.mockClear();
    snackBar.open.mockClear();
    TestBed.configureTestingModule({
      imports: [TrocarSenha],
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(TrocarSenha);
    componente = fixture.componentInstance;
    elemento = fixture.nativeElement as HTMLElement;
    atualizar = () => fixture.detectChanges();
    atualizar();
  });

  afterEach(() => httpTesting.verify());

  function preencherFormulario(confirmarNovaSenha = 'nova-senha-123'): void {
    componente.formulario.setValue({
      senhaAtual: 'senha-atual',
      novaSenha: 'nova-senha-123',
      confirmarNovaSenha,
    });
  }

  it('mostra erro e não envia a troca quando as senhas são diferentes', () => {
    preencherFormulario('outra-senha-123');

    componente.salvar();
    atualizar();

    httpTesting.expectNone('/api/usuarios/me/senha');
    expect(elemento.textContent).toContain('As senhas não conferem.');
  });

  it('envia a troca autenticada e encerra a sessão após resposta 204', () => {
    preencherFormulario();

    componente.salvar();

    const requisicao = httpTesting.expectOne({ method: 'PUT', url: '/api/usuarios/me/senha' });
    expect(requisicao.request.headers.get('Authorization')).toBe('Bearer token-falso');
    expect(requisicao.request.body).toEqual({ senhaAtual: 'senha-atual', novaSenha: 'nova-senha-123' });
    requisicao.flush(null, { status: 204, statusText: 'Sem conteúdo' });

    expect(auth.limparSessao).toHaveBeenCalledOnce();
    expect(snackBar.open).toHaveBeenCalledWith('Senha alterada. Entre novamente.', 'Fechar', { duration: 5000 });
    expect(navegar).toHaveBeenCalledWith('/entrar');
  });

  it('mostra o detalhe de 422 sem limpar, navegar ou renovar', () => {
    preencherFormulario();

    componente.salvar();
    httpTesting.expectOne('/api/usuarios/me/senha').flush(
      { detail: 'Senha atual incorreta.' },
      { status: 422, statusText: 'Entidade não processável' },
    );
    atualizar();

    expect(elemento.querySelector('[role="alert"]')?.textContent?.trim()).toBe('Senha atual incorreta.');
    expect(auth.limparSessao).not.toHaveBeenCalled();
    expect(navegar).not.toHaveBeenCalled();
    expect(auth.renovar).not.toHaveBeenCalled();
  });
});
