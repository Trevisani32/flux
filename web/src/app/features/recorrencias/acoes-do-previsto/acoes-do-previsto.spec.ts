import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { describe, expect, it, vi } from 'vitest';

import { Ocorrencia } from '../recorrencias.service';
import { AcoesDoPrevisto } from './acoes-do-previsto';

const ocorrencia = {
  recorrenciaId: 7,
  periodo: '2026-11-01',
  data: '2026-11-27',
  dataNaLista: '2026-11-27',
  descricao: 'Conta de luz Fictícia',
  tipo: 'DESPESA',
  valor: -189.9,
  valorPrevisto: 189.9,
  valorVaria: false,
  contaId: 1,
  contaNome: 'Banco Horizonte',
  cartaoId: null,
  cartaoNome: null,
  cartaoCor: null,
  categoriaId: 12,
  categoriaNome: 'Energia',
  categoriaMaeNome: 'Casa',
  atrasada: false,
  visivelAte: null,
} satisfies Ocorrencia;

describe('AcoesDoPrevisto', () => {
  it('mostra as quatro ações e fecha com a escolhida', () => {
    const fechar = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { ocorrencia, hoje: '2026-11-25' } },
        { provide: MatDialogRef, useValue: { close: fechar } },
      ],
    });
    const fixture = TestBed.createComponent(AcoesDoPrevisto);
    fixture.detectChanges();
    const elemento = fixture.nativeElement as HTMLElement;
    expect(elemento.querySelector('h2')?.textContent).toBe('Conta de luz Fictícia');
    expect(elemento.querySelector('.flux-pilula')?.textContent).toBe('Previsto');
    expect(elemento.querySelector('.acoes-previsto__conteudo p')?.textContent).toContain('sexta, 27/11');
    expect(elemento.querySelector('.valor__texto')?.textContent.replace(/\u00a0/g, ' ')).toBe('−R$ 189,90');
    expect(elemento.querySelector('.acoes-previsto__conteudo p')?.textContent).toContain('Banco Horizonte');
    const botoes = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('button'));
    expect(botoes.map((botao) => botao.textContent?.trim()))
      .toEqual(['Confirmar', 'Pular esta', 'Já lancei', 'Editar a recorrência', 'Fechar']);
    fixture.componentInstance.escolher('editar');
    expect(fechar).toHaveBeenCalledWith('editar');
  });
});
