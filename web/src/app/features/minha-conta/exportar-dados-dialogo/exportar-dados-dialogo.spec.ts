import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AGORA } from '../../../core/tempo/agora';
import { ExportarDadosDialogo } from './exportar-dados-dialogo';

describe('ExportarDadosDialogo', () => {
  let http: HttpTestingController;
  let referencia: MatDialogRef<ExportarDadosDialogo, string>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AGORA, useValue: () => new Date('2026-11-24T15:00:00Z') },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    referencia = TestBed.inject(MatDialog).open<ExportarDadosDialogo, { formato: 'JSON' }, string>(ExportarDadosDialogo, {
      data: { formato: 'JSON' },
    });
    TestBed.tick();
  });

  afterEach(() => {
    http.verify();
    referencia.close();
  });

  function painel(): HTMLElement {
    return document.querySelector('.mat-mdc-dialog-container') as HTMLElement;
  }

  it('mostra o nome do arquivo de hoje e o botão desligado sem senha', () => {
    expect(painel().textContent).toContain('flux-dados-2026-11-24.json');
    expect(painel().querySelector<HTMLButtonElement>('button[type="submit"]')?.disabled).toBe(true);
    referencia.close();
    const csv = TestBed.inject(MatDialog).open<ExportarDadosDialogo, { formato: 'CSV' }, string>(ExportarDadosDialogo, {
      data: { formato: 'CSV' },
    });
    TestBed.tick();
    expect(csv.componentInstance.nome).toBe('flux-lancamentos-2026-11-24.csv');
    csv.close();
  });

  it('senha certa salva o arquivo e fecha com o nome', () => {
    const criarUrl = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:arquivo');
    const clique = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    const fechar = vi.spyOn(referencia, 'close');
    const arquivo = new Blob(['{}']);
    referencia.componentInstance.senha.setValue('senha-certa-123');
    referencia.componentInstance.baixar();
    http.expectOne('/api/minha-conta/exportacao').flush(arquivo, {
      headers: { 'Content-Disposition': 'attachment; filename="flux-dados-2026-11-24.json"' },
    });
    expect(criarUrl).toHaveBeenCalledOnce();
    expect(criarUrl.mock.calls[0][0]).toBe(arquivo);
    expect(clique).toHaveBeenCalledOnce();
    expect((clique.mock.instances[0] as HTMLAnchorElement).download).toBe('flux-dados-2026-11-24.json');
    expect(fechar).toHaveBeenCalledWith('flux-dados-2026-11-24.json');
    criarUrl.mockRestore();
    clique.mockRestore();
  });

  it('senha errada mostra o erro no campo e 429 o aviso de tentativas', async () => {
    referencia.componentInstance.senha.setValue('errada');
    referencia.componentInstance.baixar();
    http.expectOne('/api/minha-conta/exportacao').flush(new Blob([JSON.stringify({ detail: 'Senha atual incorreta.' })]), { status: 422, statusText: 'Erro' });
    await new Promise<void>((resolve) => setTimeout(resolve, 0));
    TestBed.tick();
    expect(painel().querySelector('#exportar-dados-senha-erro')?.textContent).toBe('Senha atual incorreta.');
    referencia.componentInstance.baixar();
    http.expectOne('/api/minha-conta/exportacao').flush(new Blob(['{}']), { status: 429, statusText: 'Erro' });
    await new Promise<void>((resolve) => setTimeout(resolve, 0));
    TestBed.tick();
    expect(painel().textContent).toContain('Muitas tentativas. Tente de novo em alguns minutos.');
  });

  it('falha sem resposta mostra "Não foi possível baixar o arquivo. Tente novamente."', async () => {
    referencia.componentInstance.senha.setValue('senha-certa-123');
    referencia.componentInstance.baixar();
    http.expectOne('/api/minha-conta/exportacao').error(new ProgressEvent('error'));
    await new Promise<void>((resolve) => setTimeout(resolve, 0));
    TestBed.tick();
    expect(painel().textContent).toContain('Não foi possível baixar o arquivo. Tente novamente.');
  });

  it('enquanto baixa, o botão diz Baixando… e fica desligado', () => {
    referencia.componentInstance.senha.setValue('senha-certa-123');
    referencia.componentInstance.baixar();
    TestBed.tick();
    const botao = painel().querySelector<HTMLButtonElement>('button[type="submit"]');
    expect(botao?.textContent).toContain('Baixando…');
    expect(botao?.disabled).toBe(true);
    http.expectOne('/api/minha-conta/exportacao').flush(new Blob(['{}']));
  });
});
