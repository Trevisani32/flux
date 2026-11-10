import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { SaldoEmDestaque } from './saldo-em-destaque';

@Component({
  selector: 'app-teste-saldo-em-destaque',
  imports: [SaldoEmDestaque],
  template: '<app-saldo-em-destaque rotulo="Saldo total" [valor]="valor()"><span>5 contas ativas</span></app-saldo-em-destaque>',
})
class TesteSaldoEmDestaque {
  readonly valor = signal(23180.45);
}

describe('SaldoEmDestaque', () => {
  function criar(valor: number): HTMLElement {
    const fixture = TestBed.createComponent(TesteSaldoEmDestaque);
    fixture.componentInstance.valor.set(valor);
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).querySelector('app-saldo-em-destaque') as HTMLElement;
  }

  it('mostra o saldo pelo app-valor, com os centavos menores, e o rodapé', () => {
    const cartao = criar(23180.45);
    expect(cartao.classList).toContain('flux-cartao-destaque');
    expect(cartao.querySelector('.flux-cartao-destaque__rotulo')?.textContent).toBe('Saldo total');
    const valor = cartao.querySelector('.flux-cartao-destaque__valor app-valor');
    expect(valor?.querySelector('.valor__texto')?.textContent).toBe('R$ 23.180,45');
    expect(valor?.querySelector('.valor__texto small')?.textContent).toBe(',45');
    expect(valor?.querySelector('.valor__texto')?.getAttribute('aria-hidden')).toBeNull();
    expect(valor?.classList).not.toContain('flux-valor--negativo');
    expect(cartao.querySelector('.flux-cartao-destaque__rodape')?.textContent).toBe('5 contas ativas');
  });

  it('o total negativo fica vermelho, com "−", e o leitor de tela ouve "menos"', () => {
    const valor = criar(-120).querySelector('.flux-cartao-destaque__valor app-valor');
    expect(valor?.classList).toContain('flux-valor--negativo');
    expect(valor?.querySelector('.valor__texto')?.textContent).toBe('−R$ 120,00');
    expect(valor?.querySelector('.valor__texto small')?.textContent).toBe(',00');
    expect(valor?.querySelector('.valor__texto')?.getAttribute('aria-hidden')).toBe('true');
    expect(valor?.querySelector('.flux-visualmente-oculto')?.textContent).toBe('menos R$ 120,00');
  });
});
