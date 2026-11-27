import { Component, computed, DestroyRef, ElementRef, inject, input, signal } from '@angular/core';

import { formatarMoeda } from '../moeda/moeda';
import { escalaVertical, marcasDoEixo, textoDoEixo } from './escalas';
import { DiaDoDegrau } from './mes-do-grafico';

let proximoGrafico = 0;

interface DiaDesenhado extends DiaDoDegrau {
  readonly indice: number;
  readonly x: number;
  readonly y: number;
}

@Component({
  selector: 'app-grafico-degraus',
  templateUrl: './grafico-degraus.html',
  styles: `
    :host {
      position: relative;
      display: block;
    }
  `,
})
export class GraficoDegraus {
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly destroyRef = inject(DestroyRef);
  private readonly identificador = proximoGrafico++;

  readonly dias = input.required<readonly DiaDoDegrau[]>();
  readonly menor = input.required<{ readonly data: string; readonly rotulo: string } | null>();
  readonly rotulo = input.required<string>();
  readonly altura = input(270);
  readonly largura = signal(600);
  readonly ativo = signal(0);
  readonly mostrarBalao = signal(false);
  readonly textoDoEixo = textoDoEixo;
  readonly formatarMoeda = formatarMoeda;
  readonly marcas = computed(() => marcasDoEixo(this.dias().map((dia) => dia.saldo)));
  readonly y = computed(() => escalaVertical(this.marcas(), 22, this.altura() - 52));
  readonly larguraDoDia = computed(() => (this.largura() - 60) / Math.max(this.dias().length, 1));
  readonly zero = computed(() => this.y()(0));
  readonly diasDesenhados = computed<readonly DiaDesenhado[]>(() => {
    const y = this.y();
    const largura = this.larguraDoDia();
    return this.dias().map((dia, indice) => ({ ...dia, indice, x: 46 + indice * largura, y: y(dia.saldo) }));
  });
  readonly linha = computed(() => {
    const dias = this.diasDesenhados();
    if (dias.length === 0) {
      return '';
    }
    let caminho = `M ${dias[0].x} ${dias[0].y}`;
    let saldo = dias[0].saldo;
    for (const dia of dias.slice(1)) {
      if (dia.saldo !== saldo) {
        caminho += ` H ${dia.x} V ${dia.y}`;
        saldo = dia.saldo;
      }
    }
    return `${caminho} H ${dias.at(-1)!.x + this.larguraDoDia()}`;
  });
  readonly area = computed(() => {
    const dias = this.diasDesenhados();
    return dias.length === 0 ? '' : `${this.linha()} V ${this.zero()} H ${dias[0].x} Z`;
  });
  readonly acima = computed(() => `flux-degraus-${this.identificador}-acima`);
  readonly abaixo = computed(() => `flux-degraus-${this.identificador}-abaixo`);
  readonly menorDesenhado = computed(() => {
    const menor = this.menor();
    return menor === null ? null : this.diasDesenhados().find((dia) => dia.data === menor.data) ?? null;
  });
  readonly rotulos = computed(() => {
    const aceitos: DiaDesenhado[] = [];
    for (const dia of this.diasDesenhados()) {
      if (dia.rotuloDoEixo === null) {
        continue;
      }
      const anterior = aceitos.at(-1);
      if (anterior === undefined || dia.x + this.larguraDoDia() / 2 - (anterior.x + this.larguraDoDia() / 2) >= 44) {
        aceitos.push(dia);
      } else if (dia.indice === this.diasDesenhados().length - 1) {
        aceitos[aceitos.length - 1] = dia;
      }
    }
    return aceitos;
  });
  readonly diaAtivo = computed(() => this.diasDesenhados()[this.ativo()] ?? null);

  constructor() {
    if (typeof ResizeObserver === 'undefined') {
      return;
    }
    const observer = new ResizeObserver((entradas) => {
      const largura = entradas[0]?.contentRect.width ?? 0;
      // Abaixo das margens (46 + 14), o dia teria largura negativa e o SVG reclama no console.
      if (largura > 60) {
        this.largura.set(largura);
      }
    });
    queueMicrotask(() => observer.observe(this.host.nativeElement));
    this.destroyRef.onDestroy(() => observer.disconnect());
  }

  focar(indice: number): void {
    this.ativo.set(indice);
    this.mostrarBalao.set(true);
  }

  desfocar(): void {
    this.mostrarBalao.set(false);
  }

  entrar(indice: number): void {
    this.ativo.set(indice);
    this.mostrarBalao.set(true);
  }

  sair(): void {
    this.mostrarBalao.set(false);
  }

  tecla(evento: KeyboardEvent, indice: number): void {
    const ultimo = this.diasDesenhados().length - 1;
    const destino = evento.key === 'ArrowRight' ? Math.min(ultimo, indice + 1)
      : evento.key === 'ArrowLeft' ? Math.max(0, indice - 1)
        : evento.key === 'Home' ? 0 : evento.key === 'End' ? ultimo : null;
    if (destino === null) {
      return;
    }
    evento.preventDefault();
    this.ativo.set(destino);
    this.mostrarBalao.set(true);
    const alvo = this.host.nativeElement.querySelector(`[data-indice="${destino}"]`) as HTMLElement | null;
    alvo?.focus();
  }

  esquerdaBalao(dia: DiaDesenhado): number | null {
    return dia.x + this.larguraDoDia() / 2 < this.largura() / 2 ? dia.x + this.larguraDoDia() + 8 : null;
  }

  direitaBalao(dia: DiaDesenhado): number | null {
    return dia.x + this.larguraDoDia() / 2 >= this.largura() / 2 ? this.largura() - dia.x + 8 : null;
  }
}
