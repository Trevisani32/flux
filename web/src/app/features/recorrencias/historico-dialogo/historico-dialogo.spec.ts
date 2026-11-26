import { provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { FluxoDaOcorrencia } from '../fluxo-da-ocorrencia';
import { Historico, RecorrenciasService } from '../recorrencias.service';
import { HistoricoDialogo } from './historico-dialogo';

const historico: Historico = {
  recorrenciaId: 7,
  descricao: 'Conta de luz Fictícia',
  frequencia: 'MENSAL',
  desde: '2026-11-25',
  itens: [
    {
      periodo: '2026-11-01', dataPrevista: '2026-11-30', situacao: 'PREVISTA', valor: -189.9, data: null,
      lancamentoId: null, compraId: null, mesNaLista: '2026-11', contaId: 1, cartaoId: null,
    },
    {
      periodo: '2026-10-01', dataPrevista: '2026-10-30', situacao: 'EFETIVADA', valor: -189.9,
      data: '2026-10-30', lancamentoId: 17, compraId: null, mesNaLista: '2026-10', contaId: 1, cartaoId: null,
    },
    {
      periodo: '2026-09-01', dataPrevista: '2026-09-30', situacao: 'PULADA', valor: null, data: null,
      lancamentoId: null, compraId: null, mesNaLista: '2026-09', contaId: 1, cartaoId: null,
    },
  ],
};

describe('HistoricoDialogo', () => {
  const servico = { historico: vi.fn(), voltarAPrever: vi.fn() };
  const fluxo = { notificarMudanca: vi.fn() };
  const snackBar = { open: vi.fn() };

  beforeEach(() => {
    servico.historico.mockReset();
    servico.voltarAPrever.mockReset();
    fluxo.notificarMudanca.mockReset();
    snackBar.open.mockReset();
    servico.historico.mockReturnValue(of(historico));
    servico.voltarAPrever.mockReturnValue(of(undefined));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: MAT_DIALOG_DATA, useValue: { recorrenciaId: 7, hoje: '2026-11-25' } },
        { provide: MatDialogRef, useValue: { close: vi.fn() } },
        { provide: RecorrenciasService, useValue: servico },
        { provide: FluxoDaOcorrencia, useValue: fluxo },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    });
  });

  it('lista prevista, confirmada com o link e pulada', () => {
    const fixture = TestBed.createComponent(HistoricoDialogo);
    fixture.detectChanges();
    const elemento = fixture.nativeElement as HTMLElement;
    const linhas = Array.from(elemento.querySelectorAll<HTMLLIElement>('.historico-dialogo__lista li'));
    expect(elemento.querySelector('h2')?.textContent).toBe('Histórico de Conta de luz Fictícia');
    expect(linhas[0].querySelector('b')?.textContent).toBe('Novembro · 30/11');
    expect(linhas[0].querySelector('small')?.textContent).toBe('Prevista');
    expect(linhas[0].querySelector('.valor__texto')?.textContent.replace(/\u00a0/g, ' ')).toBe('−R$ 189,90');
    expect(linhas[1].querySelector('b')?.textContent).toBe('Outubro · 30/10');
    expect(linhas[1].querySelector('small')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('Confirmada em 30/10 · ver lançamento');
    expect(linhas[1].querySelector('a')?.getAttribute('href')).toBe('/lancamentos?mes=2026-10&conta=1');
    expect(linhas[2].querySelector('small')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('Pulada · Voltar a prever');
    expect(linhas[2].lastElementChild?.textContent).toBe('—');
  });

  it('Voltar a prever chama a API, avisa e recarrega', () => {
    const fixture = TestBed.createComponent(HistoricoDialogo);
    fixture.detectChanges();
    fixture.componentInstance.voltarAPrever('2026-09-01');
    expect(servico.voltarAPrever).toHaveBeenCalledWith(7, '2026-09-01');
    expect(snackBar.open).toHaveBeenCalledWith('Voltou a ser prevista.', 'Fechar', { duration: 5000 });
    expect(fluxo.notificarMudanca).toHaveBeenCalledTimes(1);
    expect(servico.historico).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.textContent.replace(/\s+/g, ' ').trim()).toContain('Pulada · Voltar a prever');
  });
});
