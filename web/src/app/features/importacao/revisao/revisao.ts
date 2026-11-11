import { Component, computed, inject, input, linkedSignal, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';

import { Alerta } from '../../../shared/alerta/alerta';
import { dataCurta } from '../../../shared/datas/data-curta';
import { dataHoraCurta } from '../../../shared/datas/data-hora';
import { EstadoVazio } from '../../../shared/estado-vazio/estado-vazio';
import { Icone } from '../../../shared/icone/icone';
import { Valor } from '../../../shared/valor/valor';
import { ListaDeCategorias } from '../../categorias/categorias.service';
import { rotuloDoDia } from '../../lancamentos/apresentacao';
import { chaveDaDescricao, contagens, fraseDasOrigens, fraseDoParecido, rotuloDaSituacao, textoDoBotaoImportar } from '../apresentacao';
import { Escolha, LinhaDaPrevia, Previa } from '../importacao.service';
import { abrirSeletorDeCategoria, opcoesDoSeletor } from '../seletor-de-categoria/seletor-de-categoria';

type Grupo = 'NOVA' | 'PROVAVEL';
/** A pílula de grupo do celular (decisão 22): só o grupo escolhido aparece. */
type GrupoNoCelular = 'importar' | 'ja' | 'fora';

/** As linhas de um grupo, com o rótulo do dia antes da primeira linha de cada data (o celular mostra). */
interface LinhaDoGrupo {
  readonly linha: LinhaDaPrevia;
  readonly dia: string | null;
}

/**
 * A revisão (decisões 14 a 22): "Para importar" com caixas e categoria em cada linha, "Já
 * importadas" (as 3 mais recentes, com a origem) e "Ficam de fora" recolhidas, e a barra fixa que
 * só conta as marcadas (o front não soma dinheiro). No celular, os grupos viram pílulas. Emite as
 * escolhas; quem chama envia o arquivo de novo.
 */
@Component({
  selector: 'app-revisao',
  imports: [MatButtonModule, Alerta, EstadoVazio, Icone, Valor],
  templateUrl: './revisao.html',
  styleUrl: './revisao.scss',
})
export class Revisao {
  private readonly dialogo = inject(MatDialog);

  readonly previa = input.required<Previa>();
  readonly categorias = input.required<ListaDeCategorias>();
  /** Hoje no fuso do negócio ("2026-11-10"). */
  readonly hoje = input.required<string>();
  readonly enviando = input(false);
  /** O 422 da confirmação (limite, categoria, arquivo diferente). */
  readonly erro = input<string | null>(null);
  readonly importar = output<Escolha[]>();
  readonly cancelar = output<void>();
  readonly trocarConta = output<number>();
  readonly editarPerfil = output<void>();

  readonly dataHoraCurta = dataHoraCurta;
  readonly fraseDoParecido = fraseDoParecido;
  readonly fraseDasOrigens = fraseDasOrigens;
  readonly contagens = computed(() => contagens(this.previa().linhas));
  readonly novas = computed(() => this.doGrupo('NOVA'));
  readonly provaveis = computed(() => this.doGrupo('PROVAVEL'));
  readonly notaParaImportar = computed(() => {
    const { novas, provaveis } = this.contagens();
    const notaDasNovas = novas === 1 ? '1 nova' : `${novas} novas`;
    const notaDasProvaveis = provaveis === 1 ? '1 provável duplicada' : `${provaveis} prováveis duplicadas`;
    return `${notaDasNovas} e ${notaDasProvaveis}`;
  });
  readonly temNovaFutura = computed(() => this.novas().some(({ linha }) => linha.futuro));
  readonly jaImportadas = computed(() => this.previa().linhas.filter((linha) => linha.situacao === 'JA_IMPORTADA'));
  readonly deFora = computed(() => this.previa().linhas
    .filter((linha) => linha.situacao === 'FORA' || linha.situacao === 'ANTES_DO_SALDO_INICIAL'));
  /** A API manda a mais recente primeiro: as 3 do mockup. */
  readonly jaMaisRecentes = computed(() => this.jaImportadas().slice(0, 3));
  readonly temAntesDoSaldoInicial = computed(() => this.deFora().some((linha) => linha.situacao === 'ANTES_DO_SALDO_INICIAL'));
  /** "Continuar em Poupança" esconde o aviso de outra conta, até a próxima revisão. */
  readonly continuarAqui = linkedSignal<Previa, boolean>({ source: this.previa, computation: () => false });
  readonly grupoNoCelular = linkedSignal<Previa, GrupoNoCelular>({ source: this.previa, computation: () => 'importar' });
  /** As linhas marcadas, a começar pelo que a API sugeriu (nova sim; provável e futura não). */
  readonly marcadas = linkedSignal<ReadonlySet<number>>(() =>
    new Set(this.previa().linhas.filter((linha) => linha.marcada).map((linha) => linha.linha)));
  /**
   * A categoria escolhida em cada linha; sem entrada, "Sem categoria". Outra revisão começa do zero;
   * a lista de categorias nova (depois do CATEGORIA_INVALIDA) tira os ids que não estão mais ativos.
   */
  readonly categoriaDaLinha = linkedSignal<{ previa: Previa; ativas: ReadonlyMap<number, string> }, ReadonlyMap<number, number>>({
    source: () => ({ previa: this.previa(), ativas: this.nomes() }),
    computation: (fonte, anterior) => anterior === undefined || anterior.source.previa !== fonte.previa ? new Map()
      : new Map([...anterior.value].filter(([, id]) => fonte.ativas.has(id))),
  });
  readonly quantidadeMarcadas = computed(() => this.marcadas().size);
  readonly textoDoBotao = computed(() => this.enviando() ? 'Importando…' : textoDoBotaoImportar(this.quantidadeMarcadas()));
  readonly todasMarcadas = computed(() => this.quantidadeMarcadas() === this.contagens().paraImportar);
  readonly algumaMarcada = computed(() => this.quantidadeMarcadas() > 0);
  readonly jaAbertas = signal(false);
  /** O texto aprovado (decisão 14) recolhe as duas; o quadro 949 mostrava "Ficam de fora" aberto, e vale o texto. */
  readonly foraAbertas = signal(false);
  private readonly nomes = computed(() => {
    const nomes = new Map<number, string>();
    const lista = this.categorias();
    for (const principal of [...lista.despesas.ativas, ...lista.receitas.ativas]) {
      nomes.set(principal.id, principal.nome);
      principal.subcategorias.forEach((sub) => nomes.set(sub.id, `${principal.nome} › ${sub.nome}`));
    }
    return nomes;
  });

  /** A pílula do celular; o grupo escolhido abre. */
  mostrarNoCelular(grupo: GrupoNoCelular): void {
    this.grupoNoCelular.set(grupo);
    if (grupo === 'ja') {
      this.jaAbertas.set(true);
    } else if (grupo === 'fora') {
      this.foraAbertas.set(true);
    }
  }

  rotulo(linha: LinhaDaPrevia): string {
    return rotuloDaSituacao(linha, this.previa().dataSaldoInicial, this.hoje());
  }

  dataDaLinha(linha: LinhaDaPrevia): string {
    return linha.data === null ? '—' : dataCurta(linha.data).slice(0, 5);
  }

  marcada(linha: LinhaDaPrevia): boolean {
    return this.marcadas().has(linha.linha);
  }

  nomeDaCategoria(linha: LinhaDaPrevia): string | null {
    const id = this.categoriaDaLinha().get(linha.linha);
    return id === undefined ? null : (this.nomes().get(id) ?? null);
  }

  alternar(linha: LinhaDaPrevia): void {
    this.marcadas.update((atuais) => {
      const novas = new Set(atuais);
      if (!novas.delete(linha.linha)) {
        novas.add(linha.linha);
      }
      return novas;
    });
  }

  /** "Marcar todas" ou "Desmarcar todas" de um grupo. */
  grupoInteiroMarcado(grupo: Grupo): boolean {
    const linhas = grupo === 'NOVA' ? this.novas() : this.provaveis();
    return linhas.every(({ linha }) => this.marcadas().has(linha.linha));
  }

  alternarGrupo(grupo: Grupo): void {
    const linhas = (grupo === 'NOVA' ? this.novas() : this.provaveis()).map(({ linha }) => linha.linha);
    const marcar = !this.grupoInteiroMarcado(grupo);
    this.marcadas.update((atuais) => {
      const novas = new Set(atuais);
      linhas.forEach((numero) => (marcar ? novas.add(numero) : novas.delete(numero)));
      return novas;
    });
  }

  /** A caixa do cabeçalho: todas as linhas "para importar". */
  alternarTodas(): void {
    const todas = [...this.novas(), ...this.provaveis()].map(({ linha }) => linha.linha);
    this.marcadas.set(this.todasMarcadas() ? new Set() : new Set(todas));
  }

  /** O seletor da decisão 20; "Usar também…" aplica às outras importáveis com a mesma descrição. */
  escolherCategoria(linha: LinhaDaPrevia): void {
    const chave = chaveDaDescricao(linha.descricao ?? '');
    const iguais = [...this.novas(), ...this.provaveis()]
      .map((item) => item.linha)
      .filter((outra) => outra.linha !== linha.linha && chaveDaDescricao(outra.descricao ?? '') === chave
        && Math.sign(outra.valor ?? 0) === Math.sign(linha.valor ?? 0));
    const receita = (linha.valor ?? 0) > 0;
    abrirSeletorDeCategoria(this.dialogo, {
      descricao: linha.descricao ?? '',
      valor: linha.valor ?? 0,
      data: linha.data ?? '',
      receita,
      opcoes: opcoesDoSeletor(receita ? this.categorias().receitas : this.categorias().despesas),
      atual: this.categoriaDaLinha().get(linha.linha) ?? null,
      iguais: iguais.length,
    }).subscribe((escolha) => {
      if (escolha === undefined) {
        return;
      }
      const alvos = [linha.linha, ...(escolha.usarNasIguais ? iguais.map((outra) => outra.linha) : [])];
      this.categoriaDaLinha.update((atuais) => {
        const novas = new Map(atuais);
        alvos.forEach((numero) => (escolha.categoriaId === null ? novas.delete(numero) : novas.set(numero, escolha.categoriaId)));
        return novas;
      });
    });
  }

  confirmar(): void {
    if (this.enviando() || !this.algumaMarcada()) {
      return;
    }
    const escolhas: Escolha[] = this.previa().linhas
      .filter((linha) => this.marcadas().has(linha.linha))
      .map((linha) => ({ linha: linha.linha, categoriaId: this.categoriaDaLinha().get(linha.linha) ?? null }));
    this.importar.emit(escolhas);
  }

  private doGrupo(situacao: Grupo): LinhaDoGrupo[] {
    let anterior: string | null = null;
    return this.previa().linhas.filter((linha) => linha.situacao === situacao).map((linha) => {
      const dia = linha.data !== anterior && linha.data !== null ? rotuloDoDia(linha.data, true) : null;
      anterior = linha.data;
      return { linha, dia };
    });
  }
}
