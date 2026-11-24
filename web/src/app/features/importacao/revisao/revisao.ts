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
import { fraseDaCondicao } from '../../regras/apresentacao';
import { EscolhaAMao } from '../../regras/regras.service';
import {
  chaveDaDescricao, contagens, fraseDaCorrecao, fraseDaJaImportadaNaFatura, fraseDasOrigens, fraseDoParecido, fraseDoParecidoNaFatura,
  parcelasParaCorrigir, rotuloDaSituacao, textoDaLinhaDaFatura, textoDasParcelas, textoDoBotaoImportar, tituloDasCorrecoes,
} from '../apresentacao';
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
 * O que a revisão emite ao importar: as escolhas das linhas marcadas (com a descrição que ela mostrou)
 * e, para "Criar regras com as suas escolhas" (Fatia 04b, decisão 20), as linhas marcadas que ganharam
 * categoria à mão no seletor. `porRegras`: quantas marcadas vão com a categoria da regra, para o
 * resultado dizer "6 categorizados por regras".
 */
export interface ConfirmacaoDaRevisao {
  readonly escolhas: Escolha[];
  readonly aMao: EscolhaAMao[];
  readonly porRegras: number;
}

/** A categoria que as regras sugeriram para cada linha (Fatia 04b, decisão 14). */
function sugeridas(previa: Previa): Map<number, number> {
  return new Map(previa.linhas.filter((linha) => linha.sugestao !== null)
    .map((linha) => [linha.linha, (linha.sugestao as NonNullable<LinhaDaPrevia['sugestao']>).categoriaId]));
}

/**
 * A revisão (decisões 14 a 22): "Para importar" com caixas e categoria em cada linha, "Já
 * importadas" (as 3 mais recentes, com a origem) e "Ficam de fora" recolhidas, e a barra fixa que
 * só conta as marcadas (o front não soma dinheiro). No celular, os grupos viram pílulas. Emite as
 * escolhas; quem chama envia o arquivo de novo. Na Fatia 04b, a linha que uma regra pega já vem com
 * a categoria e a descrição legível da regra; o seletor troca a categoria, e nada a muda de volta. Na Fatia 05c, a
 * revisão da fatura diz o que cada linha é (o estorno, a compra à vista e as parcelas que "Parcela k/N" cria), a
 * provável duplicada do cartão e a parcela já importada, e a barra conta as parcelas que as compras parceladas criam.
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
  readonly importar = output<ConfirmacaoDaRevisao>();
  readonly cancelar = output<void>();
  readonly trocarConta = output<number>();
  readonly editarPerfil = output<void>();

  readonly dataHoraCurta = dataHoraCurta;
  readonly fraseDoParecido = fraseDoParecido;
  readonly fraseDoParecidoNaFatura = fraseDoParecidoNaFatura;
  readonly fraseDaJaImportadaNaFatura = fraseDaJaImportadaNaFatura;
  readonly fraseDasOrigens = fraseDasOrigens;
  readonly fraseDaCorrecao = fraseDaCorrecao;
  readonly tituloDasCorrecoes = tituloDasCorrecoes;
  /** Fatia 05c: a revisão é de uma fatura de cartão. */
  readonly naFatura = computed(() => this.previa().cartaoId != null);
  readonly contagens = computed(() => contagens(this.previa().linhas));
  readonly novas = computed(() => this.doGrupo('NOVA'));
  readonly provaveis = computed(() => this.doGrupo('PROVAVEL'));
  readonly notaParaImportar = computed(() => {
    const { novas, provaveis } = this.contagens();
    const notaDasNovas = novas === 1 ? '1 nova' : `${novas} novas`;
    const notaDasProvaveis = provaveis === 1 ? '1 provável duplicada' : `${provaveis} prováveis duplicadas`;
    return `${notaDasNovas} e ${notaDasProvaveis}`;
  });
  /** A coluna "Categorizadas por regras" da faixa (decisão 14 da 04b): as linhas para importar com a categoria da regra. */
  readonly porRegras = computed(() => [...this.novas(), ...this.provaveis()].filter(({ linha }) => this.porRegra(linha)).length);
  readonly temNovaFutura = computed(() => this.novas().some(({ linha }) => linha.futuro));
  readonly jaImportadas = computed(() => this.previa().linhas.filter((linha) => linha.situacao === 'JA_IMPORTADA'));
  readonly deFora = computed(() => this.previa().linhas
    .filter((linha) => linha.situacao === 'FORA' || linha.situacao === 'ANTES_DO_SALDO_INICIAL'));
  /**
   * Fatia 05c ("Corrigir ao importar"): as parcelas já importadas com outros centavos. Vão sempre na confirmação, sem caixa;
   * sem nenhuma linha marcada, o botão só corrige.
   */
  readonly correcoes = computed(() => this.previa().linhas.filter((linha) => linha.valorNoFlux != null));
  readonly parcelasParaCorrigir = computed(() => parcelasParaCorrigir(this.correcoes().length));
  /** A API manda a mais recente primeiro: ficam as 3 mais recentes. */
  readonly jaMaisRecentes = computed(() => this.jaImportadas().slice(0, 3));
  readonly temAntesDoSaldoInicial = computed(() => this.deFora().some((linha) => linha.situacao === 'ANTES_DO_SALDO_INICIAL'));
  /** "Continuar em Poupança" esconde o aviso de outra conta, até a próxima revisão. */
  readonly continuarAqui = linkedSignal<Previa, boolean>({ source: this.previa, computation: () => false });
  readonly grupoNoCelular = linkedSignal<Previa, GrupoNoCelular>({ source: this.previa, computation: () => 'importar' });
  /** As linhas marcadas, a começar pelo que a API sugeriu (nova sim; provável e futura não). */
  readonly marcadas = linkedSignal<ReadonlySet<number>>(() =>
    new Set(this.previa().linhas.filter((linha) => linha.marcada).map((linha) => linha.linha)));
  /**
   * A categoria de cada linha; sem entrada, "Sem categoria". Outra revisão começa da sugestão das
   * regras (Fatia 04b); a lista de categorias nova (depois do CATEGORIA_INVALIDA) tira os ids que não
   * estão mais ativos.
   */
  readonly categoriaDaLinha = linkedSignal<{ previa: Previa; ativas: ReadonlyMap<number, string> }, ReadonlyMap<number, number>>({
    source: () => ({ previa: this.previa(), ativas: this.nomes() }),
    computation: (fonte, anterior) => new Map([...(anterior === undefined || anterior.source.previa !== fonte.previa
      ? sugeridas(fonte.previa) : anterior.value)].filter(([, id]) => fonte.ativas.has(id))),
  });
  /** As linhas que ganharam categoria no seletor (decisão 15 da 04b): a escolha do usuário vence a regra. */
  readonly escolhidas = linkedSignal<Previa, ReadonlySet<number>>({ source: this.previa, computation: () => new Set() });
  readonly quantidadeMarcadas = computed(() => this.marcadas().size);
  /** Fatia 05c: "a compra em 10x cria 8 parcelas", com as parceladas marcadas. */
  readonly textoDasParcelas = computed(() => textoDasParcelas(this.previa().linhas.filter((linha) => this.marcadas().has(linha.linha))));
  readonly textoDoBotao = computed(() => {
    if (this.enviando()) {
      return this.algumaMarcada() ? 'Importando…' : 'Corrigindo…';
    }
    return !this.algumaMarcada() && this.correcoes().length > 0 ? `Corrigir ${this.parcelasParaCorrigir()}`
      : textoDoBotaoImportar(this.quantidadeMarcadas());
  });
  readonly todasMarcadas = computed(() => this.quantidadeMarcadas() === this.contagens().paraImportar);
  readonly algumaMarcada = computed(() => this.quantidadeMarcadas() > 0);
  readonly podeConfirmar = computed(() => this.algumaMarcada() || this.correcoes().length > 0);
  readonly jaAbertas = signal(false);
  /**
   * Na fatura, o pagamento que veio no arquivo precisa ficar à vista antes da confirmação: ele não é um estorno e deve ser
   * registrado pela conta. Na importação de extrato, a lista continua recolhida até a pessoa abri-la.
   */
  readonly foraAbertas = linkedSignal<Previa, boolean>({ source: this.previa, computation: (previa) => previa.cartaoId != null });
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

  /** Fatia 05c: o que a linha da fatura é ("À vista", "Cria a compra com as parcelas 3 a 10 (até julho de 2027)"). */
  textoDaFatura(linha: LinhaDaPrevia): { readonly longo: string; readonly curto: string } | null {
    const fatura = this.previa().fatura;
    return fatura == null ? null : textoDaLinhaDaFatura(linha, fatura.mes, this.hoje());
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

  /** A categoria da linha veio da regra e o usuário não a trocou no seletor (decisões 14 e 15 da 04b). */
  porRegra(linha: LinhaDaPrevia): boolean {
    return linha.sugestao !== null && !this.escolhidas().has(linha.linha)
      && this.categoriaDaLinha().get(linha.linha) === linha.sugestao.categoriaId;
  }

  /** A descrição que a linha vai gravar: a legível da regra ou a do extrato. */
  descricaoDaLinha(linha: LinhaDaPrevia): string {
    return linha.sugestao?.descricao ?? linha.descricao ?? '';
  }

  /** "Sugerida pela 2ª regra: contém "uber"", para o seletor e para o leitor de tela. */
  fraseDaRegra(linha: LinhaDaPrevia): string | null {
    const sugestao = linha.sugestao;
    return sugestao === null ? null : `Sugerida pela ${sugestao.posicao}ª regra: ${this.condicaoDaRegra(linha)}`;
  }

  /** "contém "uber"", depois de "pela 2ª regra" na linha do desktop. */
  condicaoDaRegra(linha: LinhaDaPrevia): string {
    return linha.sugestao === null ? '' : fraseDaCondicao(linha.sugestao.condicao, linha.sugestao.texto);
  }

  /** O nome acessível do botão da categoria. */
  rotuloDaCategoria(linha: LinhaDaPrevia): string {
    const base = `Categoria de ${this.descricaoDaLinha(linha)}: ${this.nomeDaCategoria(linha) ?? 'Sem categoria'}`;
    return this.porRegra(linha) && linha.sugestao !== null ? `${base}, sugerida pela ${linha.sugestao.posicao}ª regra` : base;
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
      descricao: this.descricaoDaLinha(linha),
      valor: linha.valor ?? 0,
      data: linha.data ?? '',
      receita,
      opcoes: opcoesDoSeletor(receita ? this.categorias().receitas : this.categorias().despesas),
      atual: this.categoriaDaLinha().get(linha.linha) ?? null,
      iguais: iguais.length,
      regra: this.porRegra(linha) ? this.fraseDaRegra(linha) : null,
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
      this.escolhidas.update((atuais) => new Set([...atuais, ...alvos]));
    });
  }

  /**
   * As escolhas das marcadas, com a descrição que a revisão mostrou (decisão 15 da 04b), e as que
   * ganharam categoria à mão, para as sugestões de regra do resultado (decisão 20 da 04b). Na fatura (Fatia 05c), as
   * parcelas para corrigir vão junto, sem categoria.
   */
  confirmar(): void {
    if (this.enviando() || !this.podeConfirmar()) {
      return;
    }
    const marcadas = this.previa().linhas.filter((linha) => this.marcadas().has(linha.linha));
    const escolhas: Escolha[] = [...marcadas.map((linha) => ({
      linha: linha.linha,
      categoriaId: this.categoriaDaLinha().get(linha.linha) ?? null,
      descricao: linha.sugestao?.descricao ?? null,
    })), ...this.correcoes().map((linha) => ({ linha: linha.linha, categoriaId: null, descricao: null }))];
    const aMao: EscolhaAMao[] = marcadas.flatMap((linha) => {
      const categoriaId = this.categoriaDaLinha().get(linha.linha);
      return this.escolhidas().has(linha.linha) && categoriaId !== undefined && linha.descricao !== null
        ? [{ ...this.lugarDaEscolha(), descricaoBanco: linha.descricaoDaCompra ?? linha.descricao, descricao: linha.sugestao?.descricao ?? null,
          categoriaId }]
        : [];
    });
    this.importar.emit({ escolhas, aMao, porRegras: marcadas.filter((linha) => this.porRegra(linha)).length });
  }

  /** A conta da revisão ou, na fatura (Fatia 05c), o cartão: a sugestão de regra olha as regras dele. */
  private lugarDaEscolha(): { readonly contaId: number | null; readonly cartaoId?: number | null } {
    const previa = this.previa();
    return previa.cartaoId != null ? { contaId: null, cartaoId: previa.cartaoId } : { contaId: previa.contaId };
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
