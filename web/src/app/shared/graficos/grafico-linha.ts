import {
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  input,
  output,
  signal,
} from '@angular/core';

import { formatarMoeda } from '../moeda/moeda';
import { escalaVertical, marcasDoEixo, textoDoEixo } from './escalas';
import { PontoDoGrafico } from './mes-do-grafico';

interface PontoDesenhado extends PontoDoGrafico {
  readonly x: number;
  readonly y: number;
}

@Component({
  selector: 'app-grafico-linha',
  templateUrl: './grafico-linha.html',
  styles: `
    :host {
      position: relative;
      display: block;
    }
  `,
})
export class GraficoLinha {
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly destroyRef = inject(DestroyRef);
  private ponteiro: string | null = null;

  readonly itens = input.required<readonly PontoDoGrafico[]>();
  readonly selecionado = input.required<string>();
  readonly altura = input(236);
  readonly escolher = output<string>();
  readonly largura = signal(420);
  readonly ativo = signal<string | null>(null);
  readonly porToque = signal(false);
  readonly textoDoEixo = textoDoEixo;
  readonly formatarMoeda = formatarMoeda;
  readonly marcas = computed(() => marcasDoEixo(this.itens().map((item) => item.saldo)));
  readonly y = computed(() => escalaVertical(this.marcas(), 22, this.altura() - 56));
  readonly faixa = computed(() => (this.largura() - 52) / Math.max(this.itens().length, 1));
  readonly pontos = computed<readonly PontoDesenhado[]>(() => {
    return this.itens().map((item, indice) => ({
      ...item,
      x: 40 + this.faixa() * indice + this.faixa() / 2,
      y: this.y()(item.saldo),
    }));
  });
  readonly linha = computed(() => {
    return this.pontos()
      .map((item, indice) => `${indice === 0 ? 'M' : 'L'} ${item.x} ${item.y}`)
      .join(' ');
  });
  readonly area = computed(() => {
    const pontos = this.pontos();
    const zero = this.y()(0);
    if (pontos.length === 0) {
      return '';
    }
    const primeiro = pontos[0];
    const ultimo = pontos[pontos.length - 1];
    return `${this.linha()} L ${ultimo.x} ${zero} L ${primeiro.x} ${zero} Z`;
  });
  readonly ultimo = computed(() => this.pontos().at(-1) ?? null);
  readonly ativoItem = computed(() => {
    return this.pontos().find((item) => item.mes === this.ativo()) ?? null;
  });
  readonly rotuloSvg = computed(() => {
    const quantidade = this.itens().length;
    return `Saldo total no fim de cada mês, ${quantidade} ${quantidade === 1 ? 'mês' : 'meses'}`;
  });

  constructor() {
    if (typeof ResizeObserver === 'undefined') {
      return;
    }
    const observer = new ResizeObserver((entradas) => {
      const largura = entradas[0]?.contentRect.width ?? 0;
      if (largura > 0) {
        this.largura.set(largura);
      }
    });
    queueMicrotask(() => observer.observe(this.host.nativeElement));
    this.destroyRef.onDestroy(() => observer.disconnect());
  }

  aoPointerDown(evento: PointerEvent): void {
    this.ponteiro = evento.pointerType;
  }

  entrar(mes: string, evento: PointerEvent): void {
    if (evento.pointerType !== 'touch') {
      this.ativo.set(mes);
      this.porToque.set(false);
    }
  }

  sair(mes: string, evento: PointerEvent): void {
    if (evento.pointerType !== 'touch' && this.ativo() === mes) {
      this.ativo.set(null);
      this.porToque.set(false);
    }
  }

  focar(mes: string): void {
    if (this.ponteiro === null) {
      this.ativo.set(mes);
      this.porToque.set(false);
    }
  }

  desfocar(mes: string): void {
    if (this.ativo() === mes) {
      this.ativo.set(null);
      this.porToque.set(false);
    }
  }

  clicar(mes: string): void {
    const toque = this.ponteiro === 'touch';
    if (toque && this.ativo() !== mes) {
      this.ativo.set(mes);
      this.porToque.set(true);
    } else {
      this.escolher.emit(mes);
    }
    this.ponteiro = null;
  }

  teclaEspaco(evento: Event, mes: string): void {
    evento.preventDefault();
    this.escolher.emit(mes);
  }

  esquerdaBalao(x: number): number | null {
    return x < this.largura() / 2 ? x + 14 : null;
  }

  direitaBalao(x: number): number | null {
    return x >= this.largura() / 2 ? this.largura() - x + 14 : null;
  }
}
