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

import { formatarMoeda, partesDaMoeda } from '../moeda/moeda';
import { escalaVertical, faixas, larguraDaColuna, marcasDoEixo, textoDoEixo } from './escalas';
import { ColunaDoGrafico } from './mes-do-grafico';

interface ColunaDesenhada extends ColunaDoGrafico {
  readonly cx: number;
  readonly receitaX: number;
  readonly despesaX: number;
  readonly receitaY: number;
  readonly despesaY: number;
  readonly receitaAltura: number;
  readonly despesaAltura: number;
  readonly receitaRaio: number;
  readonly despesaRaio: number;
}

@Component({
  selector: 'app-grafico-colunas',
  templateUrl: './grafico-colunas.html',
  styles: `
    :host {
      position: relative;
      display: block;
    }
  `,
})
export class GraficoColunas {
  private readonly destroyRef = inject(DestroyRef);
  private readonly host = inject(ElementRef<HTMLElement>);
  private ponteiro: string | null = null;

  readonly itens = input.required<readonly ColunaDoGrafico[]>();
  readonly selecionado = input.required<string>();
  readonly altura = input(236);
  readonly escolher = output<string>();
  readonly largura = signal(420);
  readonly ativo = signal<string | null>(null);
  readonly porToque = signal(false);
  readonly textoDoEixo = textoDoEixo;
  readonly formatarMoeda = formatarMoeda;
  readonly marcas = computed(() => {
    return marcasDoEixo(this.itens().flatMap((item) => [item.receitas, item.despesas]));
  });
  readonly y = computed(() => escalaVertical(this.marcas(), 8, this.altura() - 42));
  readonly faixa = computed(() => faixas(this.itens().length, 40, this.largura() - 44, 64).largura);
  readonly inicio = computed(() => faixas(this.itens().length, 40, this.largura() - 44, 64).inicio);
  readonly larguraColuna = computed(() => larguraDaColuna(this.faixa()));
  readonly colunas = computed<readonly ColunaDesenhada[]>(() => {
    const y = this.y();
    const zero = y(0);
    const faixa = this.faixa();
    const inicio = this.inicio();
    const largura = this.larguraColuna();
    return this.itens().map((item, indice) => {
      const cx = inicio + faixa * indice + faixa / 2;
      const receita = y(item.receitas);
      const despesa = y(item.despesas);
      return {
        ...item,
        cx,
        receitaX: cx - largura - 1,
        despesaX: cx + 1,
        receitaY: receita,
        despesaY: despesa,
        receitaAltura: Math.abs(zero - receita),
        despesaAltura: Math.abs(zero - despesa),
        receitaRaio: Math.min(4, Math.abs(zero - receita)),
        despesaRaio: Math.min(4, Math.abs(zero - despesa)),
      };
    });
  });
  readonly ativoItem = computed(() => {
    return this.colunas().find((item) => item.mes === this.ativo()) ?? null;
  });
  readonly rotuloSvg = computed(() => {
    const quantidade = this.itens().length;
    return `Receitas e despesas, ${quantidade} ${quantidade === 1 ? 'mês' : 'meses'}`;
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

  resultado(valor: number): string {
    const partes = partesDaMoeda(valor);
    if (partes !== null && !partes.negativo && !partes.zero) {
      return `+${partes.absoluto}`;
    }
    return formatarMoeda(valor);
  }

  esquerdaBalao(cx: number): number | null {
    return cx < this.largura() / 2 ? cx + 14 : null;
  }

  direitaBalao(cx: number): number | null {
    return cx >= this.largura() / 2 ? this.largura() - cx + 14 : null;
  }
}
