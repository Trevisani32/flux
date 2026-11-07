import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { Alerta } from './alerta';

@Component({ imports: [Alerta], template: '<app-alerta tipo="erro" icone="alerta" titulo="Falhou"><p>texto</p></app-alerta>' })
class HospedeiroErro {}

@Component({ imports: [Alerta], template: '<app-alerta tipo="info" icone="info" titulo="Info" [papel]="papel" />' })
class HospedeiroInfo {
  papel: 'alert' | 'status' | null = null;
}

@Component({ imports: [Alerta], template: '<app-alerta tipo="sucesso" icone="check" titulo="Feito" />' })
class HospedeiroSucesso {}

describe('Alerta', () => {
  it('renderiza alerta de erro com conteúdo projetado e ícone oculto', () => {
    const fixture = TestBed.createComponent(HospedeiroErro);
    fixture.detectChanges();
    const elemento = fixture.nativeElement as HTMLElement;
    const alerta = elemento.querySelector('.flux-alerta');
    expect(alerta?.getAttribute('role')).toBe('alert');
    expect(alerta?.classList.contains('flux-alerta--erro')).toBe(true);
    expect(alerta?.querySelector('strong')?.textContent).toBe('Falhou');
    expect(alerta?.querySelector('p')?.textContent).toBe('texto');
    expect(alerta?.querySelector('app-icone')?.getAttribute('aria-hidden')).toBe('true');
  });

  it('usa status para informação', () => {
    const fixture = TestBed.createComponent(HospedeiroInfo);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.flux-alerta')?.getAttribute('role')).toBe('status');
  });

  it('usa status para sucesso', () => {
    const fixture = TestBed.createComponent(HospedeiroSucesso);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.flux-alerta')?.getAttribute('role')).toBe('status');
  });

  it('permite sobrescrever o papel para alert', () => {
    const fixture = TestBed.createComponent(HospedeiroInfo);
    fixture.componentInstance.papel = 'alert';
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.flux-alerta')?.getAttribute('role')).toBe('alert');
  });
});
