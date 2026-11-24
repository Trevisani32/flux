import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';

import { AGORA } from '../../core/tempo/agora';
import { Alerta } from '../../shared/alerta/alerta';
import { CabecalhoPagina } from '../../shared/cabecalho-pagina/cabecalho-pagina';
import { abrirConfirmacao, TrechoDeTexto } from '../../shared/confirmacao/confirmacao-dialogo';
import { dataCurta, dataIsoNoFuso } from '../../shared/datas/data-curta';
import { dataHoraCurta } from '../../shared/datas/data-hora';
import { EstadoVazio } from '../../shared/estado-vazio/estado-vazio';
import { Icone } from '../../shared/icone/icone';
import { Valor } from '../../shared/valor/valor';
import { detalheDoErro, propriedadeDoErro, statusDoErro } from '../auth/erros-http';
import { nomeDaFatura } from '../cartoes/apresentacao';
import { CartoesService, ListaDeCartoes } from '../cartoes/cartoes.service';
import { CategoriasService, ListaDeCategorias } from '../categorias/categorias.service';
import { ContasService, ListaDeContas } from '../contas/contas.service';
import { diaEMes } from '../lancamentos/conversao';
import { abrirConversao } from '../lancamentos/conversao-dialogo/conversao-dialogo';
import { mesDe, parametrosDaUrl } from '../lancamentos/filtros';
import { nomeDaCategoria } from '../regras/apresentacao';
import { FluxoDeRegras } from '../regras/fluxo-de-regras';
import { EscolhaAMao, RegrasService, SugestaoDeRegra } from '../regras/regras.service';
import {
  dicaDaFatura, erroDeTamanho, frasesDoResultado, linhaDoHistoricoDaFatura, mensagemDaCategoria, mensagemDoArquivo, notaDoResultadoDaFatura,
  PassoDaImportacao, passosDaImportacao, pedePerfil, perfilNaoLe, periodoDoArquivo, resumoDaFatura, resumoDoHistorico, resumoDoPerfil, rotuloDaFatura,
  subtituloCurtoDaRevisaoDaFatura, subtituloDaRevisaoDaFatura, tamanhoDoArquivo,
} from './apresentacao';
import { abrirExcluidos } from './excluidos-dialogo/excluidos-dialogo';
import {
  Amostra, Destino, Importacao as ItemDoHistorico, ImportacaoService, PagamentoDaImportacao, ParSugerido, Previa, Resultado,
  TransferenciaDaImportacao,
} from './importacao.service';
import { PerfilCsv } from './perfil-csv/perfil-csv';
import { ConfirmacaoDaRevisao, Revisao } from './revisao/revisao';

/**
 * A página de Importação (decisões 1 a 4 e 27 a 33): "Nova importação" e o histórico; escolhido
 * o arquivo, a página segue para o perfil do CSV (só na primeira vez da conta), a revisão e o
 * resultado, com o indicador de passos. O CSV com perfil salvo mostra o resumo do perfil
 * e "Revisar" (decisão 12); o OFX vai direto para a revisão. O arquivo só existe aqui, no
 * navegador: recarregar volta ao começo. Na Fatia 04b, o resultado oferece criar regras com as
 * categorias escolhidas à mão na revisão (decisão 20). Na Fatia 04c, o resultado sugere os pares de
 * transferência (decisão 35), e desfazer uma importação com lançamento convertido (em transferência ou, desde a Fatia
 * 05b, em pagamento de fatura) diz o que acontece com o outro lado (decisão 34). Desde a Fatia 05c, "Importar em" escolhe
 * uma conta ou um cartão: no cartão, lido o arquivo, o passo do arquivo mostra a fatura sugerida, que pode ser trocada
 * pela anterior ou pela seguinte (decisão 40), antes de "Revisar"; o resultado leva à fatura.
 */
@Component({
  selector: 'app-importacao',
  imports: [NgTemplateOutlet, MatButtonModule, MatMenuModule, RouterLink, Alerta, CabecalhoPagina, EstadoVazio, Icone, PerfilCsv, Revisao,
    Valor],
  templateUrl: './importacao.html',
  styleUrl: './importacao.scss',
})
export class Importacao {
  private readonly servico = inject(ImportacaoService);
  private readonly contasService = inject(ContasService);
  private readonly cartoesService = inject(CartoesService);
  private readonly categoriasService = inject(CategoriasService);
  private readonly dialogo = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);
  private readonly regras = inject(RegrasService);
  private readonly fluxoDeRegras = inject(FluxoDeRegras);
  private readonly parametros = inject(ActivatedRoute).snapshot.queryParamMap;
  private readonly contaDaUrl = Number(this.parametros.get('conta')) || null;
  /** Fatia 05c: o atalho do cartão ("Importar fatura", em Cartões). */
  private readonly cartaoDaUrl = Number(this.parametros.get('cartao')) || null;
  /** As linhas que ganharam categoria à mão na última confirmação, para "Tentar de novo" nas sugestões. */
  private aMao: readonly EscolhaAMao[] = [];

  readonly hoje = dataIsoNoFuso(inject(AGORA)());
  readonly contas = signal<ListaDeContas | null>(null);
  /** Fatia 05c: os cartões, para "Importar em"; sem eles (a lista falhou), só as contas. */
  readonly cartoes = signal<ListaDeCartoes | null>(null);
  readonly categorias = signal<ListaDeCategorias | null>(null);
  readonly historico = signal<readonly ItemDoHistorico[] | null>(null);
  readonly falha = signal(false);
  readonly passo = signal<PassoDaImportacao>('arquivo');
  /** De onde o "Editar perfil" abriu o passo do perfil; nulo quando foi a leitura que pediu o perfil. */
  private readonly passoAntesDoPerfil = signal<'arquivo' | 'revisao' | null>(null);
  /**
   * A revisão fica montada, e escondida, enquanto o perfil que ela abriu está na tela: o "Voltar"
   * devolve as mesmas marcações e categorias. Uma prévia nova recomeça as escolhas (linkedSignal).
   */
  protected readonly revisaoMontada = computed(() => this.passo() === 'revisao'
    || (this.passo() === 'perfil' && this.passoAntesDoPerfil() === 'revisao'));
  /** Onde a importação acontece: uma conta ou, desde a Fatia 05c, um cartão. */
  readonly destino = signal<Destino | null>(null);
  /** Fatia 05c (decisão 40): a fatura escolhida no seletor ("2026-12"); nula = a sugerida pela API. */
  readonly faturaEscolhida = signal<string | null>(null);
  readonly arquivo = signal<File | null>(null);
  readonly erroDoArquivo = signal<string | null>(null);
  readonly lendo = signal(false);
  readonly previa = signal<Previa | null>(null);
  readonly amostra = signal<Amostra | null>(null);
  readonly resultado = signal<Resultado | null>(null);
  readonly enviando = signal(false);
  readonly erroDaConfirmacao = signal<string | null>(null);
  readonly sugestoesDeRegra = signal<readonly SugestaoDeRegra[]>([]);
  readonly falhaNasSugestoes = signal(false);
  /** A mensagem do 422 das sugestões (categoria ou conta que não serve mais), embaixo do título do alerta. */
  readonly detalheDasSugestoes = signal<string | null>(null);
  /** Quantas linhas a revisão mandou com a categoria da regra (Fatia 04b): o texto do resultado conta. */
  private readonly porRegras = signal(0);
  /** Fatia 04c (decisão 35): "Parecem transferências entre suas contas"; sai a que foi convertida. */
  readonly paresSugeridos = signal<readonly ParSugerido[]>([]);
  readonly diaEMes = diaEMes;
  readonly nomeDaCategoria = nomeDaCategoria;
  readonly temOndeImportar = computed(() => (this.contas()?.ativas.length ?? 0) + (this.cartoes()?.ativos.length ?? 0) > 0);
  /** O nome da conta ou do cartão escolhido. */
  readonly nomeDoDestino = computed(() => {
    const destino = this.destino();
    if (destino === null) {
      return '';
    }
    return destino.tipo === 'conta' ? this.contas()?.ativas.find((conta) => conta.id === destino.id)?.nome ?? ''
      : this.cartoes()?.ativos.find((cartao) => cartao.id === destino.id)?.nome ?? '';
  });
  /** O valor do seletor "Importar em": "conta:4" ou "cartao:2". */
  readonly valorDoDestino = computed(() => {
    const destino = this.destino();
    return destino === null ? '' : `${destino.tipo}:${destino.id}`;
  });
  readonly passos = computed(() => passosDaImportacao(this.passo(), this.previa()?.formato ?? null));
  /** Enquanto a API lê um OFX, a página explica que ele não pede perfil. */
  readonly lendoOfx = computed(() => this.lendo() && (this.arquivo()?.name.toLowerCase().endsWith('.ofx') ?? false));
  /** O subtítulo do cabeçalho: o arquivo e a conta (ou o cartão e a fatura) na revisão, e o arquivo e o lugar no perfil. */
  readonly subtitulo = computed(() => {
    const previa = this.previa();
    if (this.passo() === 'revisao' && previa !== null) {
      if (previa.cartaoNome != null) {
        return subtituloDaRevisaoDaFatura(previa, this.hoje);
      }
      const periodo = previa.dataInicio !== null && previa.dataFim !== null
        ? ` · ${periodoDoArquivo(previa.dataInicio, previa.dataFim)}` : '';
      return `${previa.nomeArquivo} · ${previa.contaNome}${periodo}`;
    }
    if (this.passo() === 'perfil') {
      return `${this.arquivo()?.name ?? ''} · ${this.nomeDoDestino()}`;
    }
    return 'Traga o extrato do banco em OFX ou CSV. Nada é gravado antes da sua revisão.';
  });
  /** Fatia 05c: na revisão da fatura, o subtítulo curto do celular; nulo nos outros passos e na conta. */
  readonly subtituloCurto = computed(() => {
    const previa = this.previa();
    return this.passo() === 'revisao' && previa !== null && previa.cartaoNome != null
      ? subtituloCurtoDaRevisaoDaFatura(previa, this.hoje) : null;
  });
  readonly frases = computed(() => {
    const resultado = this.resultado();
    return resultado === null ? null : frasesDoResultado(resultado, this.porRegras());
  });
  /** Fatia 05c: a nota do resultado da fatura ("em Cartão Horizonte, fatura de dezembro · a compra em 10x criou…"). */
  readonly notaDaFatura = computed(() => {
    const resultado = this.resultado();
    return resultado === null ? null : notaDoResultadoDaFatura(resultado, this.hoje);
  });
  readonly rotuloDaFatura = rotuloDaFatura;
  readonly dicaDaFatura = dicaDaFatura;
  readonly resumoDaFatura = resumoDaFatura;
  readonly periodoDoArquivo = periodoDoArquivo;
  readonly linhaDoHistoricoDaFatura = linhaDoHistoricoDaFatura;
  readonly tamanhoDoArquivo = tamanhoDoArquivo;
  readonly resumoDoPerfil = resumoDoPerfil;
  readonly resumoDoHistorico = resumoDoHistorico;
  readonly dataCurta = dataCurta;
  readonly dataHoraCurta = dataHoraCurta;

  constructor() {
    // Fatia 05c: os cartões entram em "Importar em"; se a lista deles falhar, a página continua com as contas.
    forkJoin({ contas: this.contasService.listar(), cartoes: this.cartoesService.listar().pipe(catchError(() => of(null))) }).subscribe({
      next: ({ contas, cartoes }) => {
        this.contas.set(contas);
        this.cartoes.set(cartoes);
        this.destino.set(destinoPadrao(contas, cartoes, this.contaDaUrl, this.cartaoDaUrl, this.servico.ultimoDestino));
      },
      error: () => this.falha.set(true),
    });
    this.categoriasService.listar().subscribe({ next: (lista) => this.categorias.set(lista), error: () => this.falha.set(true) });
    this.carregarHistorico();
  }

  /**
   * "Importar em" ("conta:4" ou "cartao:2"). Com um arquivo já escolhido, a troca lê de novo (o perfil do CSV é da conta
   * ou do cartão, e a fatura é do cartão).
   */
  escolherDestino(valor: string): void {
    const [tipo, id] = valor.split(':');
    this.destino.set(valor === '' ? null : { tipo: tipo === 'cartao' ? 'cartao' : 'conta', id: Number(id) });
    this.faturaEscolhida.set(null);
    this.erroDoArquivo.set(null);
    this.previa.set(null);
    const arquivo = this.arquivo();
    if (arquivo !== null && erroDeTamanho(arquivo.size) === null) {
      this.lerArquivo();
    }
  }

  /** Fatia 05c (decisão 40): outra fatura no seletor lê de novo, com ela (a classificação depende da fatura). */
  escolherFatura(mes: string): void {
    this.faturaEscolhida.set(mes);
    this.lerArquivo();
  }

  /** O tamanho é conferido aqui, antes de enviar (decisão 35); o resto, na API. */
  escolherArquivo(arquivos: FileList | null): void {
    const arquivo = arquivos?.item(0) ?? null;
    if (arquivo === null) {
      return;
    }
    this.arquivo.set(arquivo);
    this.previa.set(null);
    this.faturaEscolhida.set(null);
    this.erroDoArquivo.set(erroDeTamanho(arquivo.size));
    if (this.erroDoArquivo() === null) {
      this.lerArquivo();
    }
  }

  soltar(evento: DragEvent): void {
    evento.preventDefault();
    this.escolherArquivo(evento.dataTransfer?.files ?? null);
  }

  /**
   * Revisão do arquivo; CSV sem perfil leva ao passo do perfil, com a amostra. Vinda do passo do
   * arquivo, a revisão de um CSV espera o "Revisar" do resumo do perfil (decisão 12). Os erros
   * aparecem no passo em que a leitura começou: no arquivo, embaixo da área; no perfil e na
   * revisão, no alerta da página. O perfil salvo que não lê o arquivo abre o passo do perfil, com o
   * alerta: sem isso, a conta não teria outro caminho até o perfil. Com
   * `contaDeVolta`, um erro devolve a conta anterior (o "Importar em X" que não deu certo). No cartão (Fatia 05c), a
   * leitura sempre volta ao passo do arquivo, com a fatura sugerida (ou a escolhida) no seletor, antes de "Revisar".
   */
  lerArquivo(contaDeVolta: number | null = null): void {
    const arquivo = this.arquivo();
    const destino = this.destino();
    if (arquivo === null) {
      return;
    }
    if (destino === null) {
      this.erroDoArquivo.set('Escolha a conta ou o cartão.');
      return;
    }
    const doArquivo = this.passo() === 'arquivo';
    const doPerfil = this.passo() === 'perfil';
    this.lendo.set(true);
    this.erroDoArquivo.set(null);
    this.erroDaConfirmacao.set(null);
    this.servico.previa(arquivo, destino, destino.tipo === 'cartao' ? this.faturaEscolhida() : null).subscribe({
      next: (previa) => {
        this.servico.ultimoDestino = destino;
        this.lendo.set(false);
        this.previa.set(previa);
        this.passo.set(destino.tipo === 'cartao' || (doArquivo && previa.formato === 'CSV') ? 'arquivo' : 'revisao');
      },
      error: (erro: unknown) => {
        if (pedePerfil(erro)) {
          this.passoAntesDoPerfil.set(null);
          this.abrirPerfil(null);
        } else if (perfilNaoLe(erro) && !doPerfil) {
          this.passoAntesDoPerfil.set(null);
          this.erroDoArquivo.set(mensagemDoArquivo(erro));
          this.servico.perfil(destino).subscribe({ next: (formato) => this.abrirPerfil(formato), error: () => this.abrirPerfil(null) });
        } else {
          this.lendo.set(false);
          this.erroDoArquivo.set(mensagemDoArquivo(erro));
          if (contaDeVolta !== null) {
            this.destino.set({ tipo: 'conta', id: contaDeVolta });
          }
        }
      },
    });
  }

  /** "Editar perfil" (resumo) ou "Editar perfil do CSV" (revisão): a amostra com o perfil salvo. */
  editarPerfil(): void {
    const destino = this.destino();
    if (destino !== null) {
      this.passoAntesDoPerfil.set(this.passo() === 'revisao' ? 'revisao' : 'arquivo');
      this.lendo.set(true);
      this.erroDoArquivo.set(null);
      this.servico.perfil(destino).subscribe({ next: (formato) => this.abrirPerfil(formato), error: () => this.abrirPerfil(null) });
    }
  }

  /**
   * "Salvar perfil e revisar": o arquivo é lido de novo com o perfil salvo. A prévia velha sai antes,
   * porque foi lida com o perfil antigo: se a leitura nova falhar, o "Voltar" recomeça, em vez de
   * voltar a uma revisão que a confirmação leria de outro jeito.
   */
  perfilSalvo(): void {
    this.passoAntesDoPerfil.set(null);
    this.previa.set(null);
    this.lerArquivo();
  }

  /**
   * "Voltar" do passo do perfil. Aberto pelo "Editar perfil", volta para o resumo ou para a revisão,
   * com o mesmo arquivo e a mesma prévia, sem ler de novo; a revisão, que ficou montada, volta com as
   * mesmas marcações e categorias e com o erro da última confirmação, se havia (ele ainda vale para
   * essas escolhas). Aberto pela leitura (CSV sem perfil, ou um perfil que não lê), ou depois de
   * salvar o perfil, não há prévia: recomeça.
   */
  voltarDoPerfil(): void {
    const antes = this.passoAntesDoPerfil();
    if (antes === null || this.previa() === null) {
      this.recomecar();
      return;
    }
    this.amostra.set(null);
    this.erroDoArquivo.set(null);
    this.passo.set(antes);
  }

  /** "Importar em X" (decisão 11): se a leitura falhar, a conta volta a ser a anterior. */
  trocarConta(contaId: number): void {
    const anterior = this.destino();
    this.destino.set({ tipo: 'conta', id: contaId });
    this.lerArquivo(anterior !== null && anterior.tipo === 'conta' ? anterior.id : null);
  }

  importar(confirmacao: ConfirmacaoDaRevisao): void {
    const arquivo = this.arquivo();
    const previa = this.previa();
    if (arquivo === null || previa === null || this.enviando()) {
      return;
    }
    const destino: Destino = previa.cartaoId != null ? { tipo: 'cartao', id: previa.cartaoId } : { tipo: 'conta', id: previa.contaId ?? 0 };
    this.enviando.set(true);
    this.erroDaConfirmacao.set(null);
    this.servico.confirmar(arquivo, destino, previa.hash, confirmacao.escolhas, previa.fatura?.mes ?? null).subscribe({
      next: (resultado) => {
        this.enviando.set(false);
        this.porRegras.set(confirmacao.porRegras);
        this.resultado.set(resultado);
        this.paresSugeridos.set(resultado.pares ?? []);
        this.passo.set('pronto');
        this.carregarHistorico();
        this.aMao = confirmacao.aMao;
        this.carregarSugestoes();
      },
      error: (erro: unknown) => {
        this.enviando.set(false);
        const daCategoria = mensagemDaCategoria(erro, previa.linhas);
        this.erroDaConfirmacao.set(daCategoria ?? mensagemDoArquivo(erro));
        if (daCategoria !== null) {
          // A categoria saiu do seletor e das linhas: com a lista nova, a revisão limpa os ids que não estão mais ativos.
          this.categoriasService.listar().subscribe({ next: (lista) => this.categorias.set(lista), error: () => undefined });
        }
      },
    });
  }

  /** "Ver lançamentos": a lista da conta no mês da linha mais recente (decisão 27). */
  verLancamentos(contaId: number, mes: string | null): void {
    void this.router.navigate(['/lancamentos'], {
      queryParams: parametrosDaUrl({ mes: mes ?? mesDe(this.hoje), conta: contaId, categoria: null, tipo: null, busca: '' }, this.hoje),
    });
  }

  /** Fatia 05c: "Ver fatura", a fatura escolhida na importação (no resultado e no histórico da fatura). */
  verFatura(cartaoId: number | null | undefined, mes: string | null | undefined): void {
    if (cartaoId != null && mes != null) {
      void this.router.navigate(['/cartoes', cartaoId, 'faturas', mes]);
    }
  }

  recomecar(): void {
    this.passo.set('arquivo');
    this.arquivo.set(null);
    this.faturaEscolhida.set(null);
    this.previa.set(null);
    this.amostra.set(null);
    this.resultado.set(null);
    this.erroDoArquivo.set(null);
    this.erroDaConfirmacao.set(null);
    this.lendo.set(false);
    this.aMao = [];
    this.porRegras.set(0);
    this.sugestoesDeRegra.set([]);
    this.falhaNasSugestoes.set(false);
    this.detalheDasSugestoes.set(null);
    this.paresSugeridos.set([]);
  }

  /**
   * "Criar regras com as suas escolhas" (decisões 20 a 22 da 04b): a API devolve até 5, só para o que
   * nenhuma regra pega. Sem escolha à mão, nem pergunta. Se falhar, o cartão diz (com a mensagem do 422,
   * quando a categoria ou a conta não serve mais) e oferece tentar de novo.
   */
  carregarSugestoes(): void {
    this.falhaNasSugestoes.set(false);
    this.detalheDasSugestoes.set(null);
    if (this.aMao.length === 0) {
      this.sugestoesDeRegra.set([]);
      return;
    }
    this.regras.sugestoes(this.aMao).subscribe({
      next: (sugestoes) => this.sugestoesDeRegra.set(sugestoes),
      error: (erro: unknown) => {
        this.sugestoesDeRegra.set([]);
        this.detalheDasSugestoes.set(statusDoErro(erro) === 422 ? propriedadeDoErro(erro, 'detail') : null);
        this.falhaNasSugestoes.set(true);
      },
    });
  }

  /** "Criar regra": o diálogo já preenchido; criada, a sugestão sai do cartão. */
  criarRegra(sugestao: SugestaoDeRegra): void {
    this.fluxoDeRegras.novaRegra(sugestao).subscribe((regra) => {
      if (regra !== undefined) {
        this.sugestoesDeRegra.update((atuais) => atuais.filter((outra) => outra !== sugestao));
      }
    });
  }

  /**
   * "Converter" (decisão 35): o diálogo da conversão com o par sugerido escolhido. Convertido (com esse
   * par ou com outro lado), a linha sai do cartão, e o histórico recarrega (as transferências dele mudaram).
   */
  converterPar(sugestao: ParSugerido): void {
    const contas = this.contas();
    if (contas === null) {
      return;
    }
    abrirConversao(this.dialogo, { lancamentoId: sugestao.lancamento.id, contas, parSugerido: sugestao.par.id }).subscribe((transferencia) => {
      if (transferencia !== undefined) {
        this.paresSugeridos.update((atuais) => atuais.filter((outra) => outra !== sugestao));
        this.carregarHistorico();
      }
    });
  }

  /** O histórico recarrega em qualquer fechamento: o Esc e o clique fora também fecham depois de restaurar. */
  verExcluidos(item: ItemDoHistorico): void {
    abrirExcluidos(this.dialogo, item).subscribe(() => this.carregarHistorico());
  }

  /**
   * Decisão 32: confirmação com o que sai; com a conta arquivada, só o aviso e o caminho para Contas. Na
   * Fatia 04c (decisão 34), a confirmação diz quantos lançamentos estão em transferências e o que acontece
   * com o outro lado; com o outro lado numa conta arquivada, também só o aviso. Na Fatia 05b, o mesmo com os
   * pagamentos de fatura convertidos: a confirmação diz que eles saem e que a fatura volta a ter o pendente; com o
   * cartão de um deles arquivado, só o aviso e o caminho para Cartões. Na Fatia 05c, a importação de fatura tem a
   * confirmação dela e, com o cartão arquivado, só o aviso e o caminho para Cartões.
   */
  desfazer(item: ItemDoHistorico): void {
    if (item.cartaoId != null) {
      this.desfazerDaFatura(item);
      return;
    }
    const transferencias = item.transferencias ?? [];
    const pagamentos = item.pagamentos ?? [];
    const presa = transferencias.find((transferencia) => transferencia.contaArquivada);
    const pagamentoPreso = pagamentos.find((pagamento) => pagamento.cartaoArquivado);
    if (item.contaArquivada || presa !== undefined || pagamentoPreso !== undefined) {
      const doCartao = presa === undefined && !item.contaArquivada;
      const paragrafo: TrechoDeTexto[] = presa !== undefined ? [`A transferência de ${diaEMes(presa.data)} liga esta importação a `,
        { forte: presa.contaNome }, ', que está arquivada. Reative a conta para desfazer esta importação.']
        : item.contaArquivada || pagamentoPreso === undefined ? [{ forte: item.contaNome ?? '' }, ' está arquivada. Reative a conta para desfazer esta importação.']
          : [`O pagamento de ${diaEMes(pagamentoPreso.data)} liga esta importação a `, { forte: pagamentoPreso.cartaoNome },
            ', que está arquivado. Reative o cartão para desfazer esta importação.'];
      abrirConfirmacao(this.dialogo, {
        titulo: 'Esta importação não pode ser desfeita agora',
        icone: 'arquivar',
        perigo: false,
        paragrafos: [paragrafo],
        confirmar: doCartao ? 'Ir para Cartões' : 'Ir para Contas',
        cancelar: 'Fechar',
        falha: doCartao ? 'Não foi possível abrir Cartões.' : 'Não foi possível abrir Contas.',
        acao: () => of(true),
      }).subscribe((resultado) => {
        if (resultado === true) {
          void this.router.navigate([doCartao ? '/cartoes' : '/contas']);
        }
      });
      return;
    }
    const extras: string[] = [];
    if (item.editados > 0) {
      extras.push(item.editados === 1 ? '1 que você editou' : `${item.editados} que você editou`);
    }
    if (item.excluidos > 0) {
      extras.push(item.excluidos === 1 ? '1 que você excluiu' : `${item.excluidos} que você excluiu`);
    }
    const singular = item.importadas === 1;
    const paragrafo: TrechoDeTexto[] = [singular ? 'O ' : 'Os ', { forte: singular ? '1 lançamento' : `${item.importadas} lançamentos` },
      ` que ela trouxe ${singular ? 'sai' : 'saem'} de ${item.contaNome ?? ''}${extras.length > 0 ? `, inclusive ${extras.join(' e ')}` : ''}. O saldo muda na hora.`];
    abrirConfirmacao(this.dialogo, {
      titulo: `Desfazer a importação de ${item.nomeArquivo}?`,
      icone: 'reativar',
      perigo: true,
      paragrafos: [paragrafo, ['O registro fica no histórico. Para trazer as linhas de volta, importe o arquivo de novo.']],
      aviso: avisoDoDesfazer(transferencias, pagamentos, this.hoje),
      confirmar: 'Desfazer importação',
      falha: 'Não foi possível desfazer. Tente novamente.',
      acao: () => this.servico.desfazer(item.id),
    }).subscribe((resultado) => {
      if (resultado === true) {
        this.snackBar.open('Importação desfeita.', 'Fechar', { duration: 4000 });
        this.carregarHistorico();
      }
    });
  }

  carregarHistorico(): void {
    this.servico.listar().subscribe({ next: (lista) => this.historico.set(lista), error: () => this.historico.set(null) });
  }

  /**
   * Fatia 05c: desfazer a importação de fatura. Com o cartão arquivado, só o aviso e o caminho para Cartões; senão, a
   * confirmação diz o que sai (as compras parceladas levam as parcelas seguintes). Com uma parcela numa fatura paga, a
   * API recusa, e a confirmação mostra a frase dela.
   */
  private desfazerDaFatura(item: ItemDoHistorico): void {
    const cartao = item.cartaoNome ?? '';
    if (item.cartaoArquivado === true) {
      abrirConfirmacao(this.dialogo, {
        titulo: 'Esta importação não pode ser desfeita agora',
        icone: 'arquivar',
        perigo: false,
        paragrafos: [[{ forte: cartao }, ' está arquivado. Reative o cartão para desfazer esta importação.']],
        confirmar: 'Ir para Cartões',
        cancelar: 'Fechar',
        falha: 'Não foi possível abrir Cartões.',
        acao: () => of(true),
      }).subscribe((resultado) => {
        if (resultado === true) {
          void this.router.navigate(['/cartoes']);
        }
      });
      return;
    }
    const editados = item.editados > 0 ? `, inclusive ${item.editados === 1 ? '1 que você editou' : `${item.editados} que você editou`}` : '';
    const quantos = item.importadas - item.excluidos;
    const singular = quantos === 1;
    const paragrafo: TrechoDeTexto[] = [singular ? 'O ' : 'Os ', { forte: singular ? '1 lançamento' : `${quantos} lançamentos` },
      ` que ela trouxe ${singular ? 'sai' : 'saem'} de ${cartao}${editados}, com as parcelas seguintes das compras parceladas. O total das faturas muda na hora.`];
    abrirConfirmacao(this.dialogo, {
      titulo: `Desfazer a importação de ${item.nomeArquivo}?`,
      icone: 'reativar',
      perigo: true,
      paragrafos: [paragrafo, ['O registro fica no histórico. Para trazer as linhas de volta, importe o arquivo de novo.']],
      confirmar: 'Desfazer importação',
      falha: 'Não foi possível desfazer. Tente novamente.',
      acao: () => this.servico.desfazer(item.id),
    }).subscribe((resultado) => {
      if (resultado === true) {
        this.snackBar.open('Importação desfeita.', 'Fechar', { duration: 4000 });
        this.carregarHistorico();
      }
    });
  }

  private abrirPerfil(formato: Parameters<ImportacaoService['amostra']>[1]): void {
    const arquivo = this.arquivo();
    if (arquivo === null) {
      return;
    }
    this.servico.amostra(arquivo, formato, this.destino()?.tipo === 'cartao').subscribe({
      next: (amostra) => {
        this.lendo.set(false);
        this.amostra.set(amostra);
        this.passo.set('perfil');
      },
      error: (erro: unknown) => {
        this.lendo.set(false);
        this.erroDoArquivo.set(detalheDoErro(erro, mensagemDoArquivo(erro)));
      },
    });
  }
}

/**
 * Fatia 05c: o lugar que "Importar em" começa escolhendo: o do atalho (`?conta=` ou `?cartao=`), o último usado na sessão,
 * a única conta ativa (sem cartão ativo) ou nenhum. Só valem a conta e o cartão ativos.
 */
export function destinoPadrao(contas: ListaDeContas, cartoes: ListaDeCartoes | null, contaDaUrl: number | null, cartaoDaUrl: number | null,
  ultimo: Destino | null): Destino | null {
  const contaAtiva = (id: number | null) => id !== null && contas.ativas.some((conta) => conta.id === id);
  const cartaoAtivo = (id: number | null) => id !== null && (cartoes?.ativos ?? []).some((cartao) => cartao.id === id);
  if (contaAtiva(contaDaUrl)) {
    return { tipo: 'conta', id: contaDaUrl as number };
  }
  if (cartaoAtivo(cartaoDaUrl)) {
    return { tipo: 'cartao', id: cartaoDaUrl as number };
  }
  if (ultimo !== null && (ultimo.tipo === 'conta' ? contaAtiva(ultimo.id) : cartaoAtivo(ultimo.id))) {
    return ultimo;
  }
  return contas.ativas.length === 1 && (cartoes?.ativos.length ?? 0) === 0 ? { tipo: 'conta', id: contas.ativas[0].id } : null;
}

/**
 * O alerta da confirmação do desfazer: os lançamentos da importação em transferências (decisão 34 da 04c) e os que
 * viraram pagamento de fatura (Fatia 05b); com os dois, um alerta só, com os dois textos.
 */
function avisoDoDesfazer(transferencias: readonly TransferenciaDaImportacao[], pagamentos: readonly PagamentoDaImportacao[],
  hoje: string): { readonly titulo: string; readonly texto: string } | undefined {
  if (pagamentos.length === 0) {
    return transferencias.length === 0 ? undefined : avisoDasTransferencias(transferencias);
  }
  const dosPagamentos = avisoDosPagamentos(pagamentos, hoje);
  if (transferencias.length === 0) {
    return dosPagamentos;
  }
  return {
    titulo: `${transferencias.length + pagamentos.length} deles estão em transferências ou são pagamentos de fatura`,
    texto: `${avisoDasTransferencias(transferencias).texto} ${dosPagamentos.texto}`,
  };
}

/** Fatia 05b: o pagamento convertido sai com a importação, e a fatura volta a ter o pendente. */
function avisoDosPagamentos(pagamentos: readonly PagamentoDaImportacao[], hoje: string): { readonly titulo: string; readonly texto: string } {
  if (pagamentos.length === 1) {
    const [pagamento] = pagamentos;
    return {
      titulo: '1 deles é pagamento de fatura',
      texto: `O pagamento de ${diaEMes(pagamento.data)} sai, e a fatura de ${nomeDaFatura(pagamento.faturaMes, hoje)} de `
        + `${pagamento.cartaoNome} volta a ter esse valor pendente.`,
    };
  }
  const nomes = [...new Set(pagamentos.map((pagamento) => pagamento.cartaoNome))];
  const cartoes = nomes.length === 1 ? nomes[0] : `${nomes.slice(0, -1).join(', ')} e ${nomes[nomes.length - 1]}`;
  return {
    titulo: `${pagamentos.length} deles são pagamentos de fatura`,
    texto: `Os pagamentos saem, e as faturas de ${cartoes} voltam a ter esses valores pendentes.`,
  };
}

/** Decisão 34 da 04c: o alerta da confirmação do desfazer quando lançamentos da importação estão em transferências. */
function avisoDasTransferencias(transferencias: readonly TransferenciaDaImportacao[]): { readonly titulo: string; readonly texto: string } {
  if (transferencias.length === 1) {
    const [transferencia] = transferencias;
    return {
      titulo: '1 deles está numa transferência',
      texto: `A transferência de ${diaEMes(transferencia.data)} com ${transferencia.contaNome} é desfeita, e o lançamento de `
        + `${transferencia.contaNome} volta a ser uma ${transferencia.tipo === 'RECEITA' ? 'receita' : 'despesa'}, sem categoria.`,
    };
  }
  const nomes = [...new Set(transferencias.map((transferencia) => transferencia.contaNome))];
  const contas = nomes.length === 1 ? nomes[0] : `${nomes.slice(0, -1).join(', ')} e ${nomes[nomes.length - 1]}`;
  return {
    titulo: `${transferencias.length} deles estão em transferências`,
    texto: `As transferências são desfeitas, e os lançamentos de ${contas} voltam a ser receitas ou despesas, sem categoria.`,
  };
}
