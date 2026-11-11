import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { of } from 'rxjs';

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
import { CategoriasService, ListaDeCategorias } from '../categorias/categorias.service';
import { ContasService, ListaDeContas } from '../contas/contas.service';
import { mesDe, parametrosDaUrl } from '../lancamentos/filtros';
import { contaPadrao } from '../lancamentos/lancamento-dialogo/lancamento-dialogo';
import { nomeDaCategoria } from '../regras/apresentacao';
import { FluxoDeRegras } from '../regras/fluxo-de-regras';
import { EscolhaAMao, RegrasService, SugestaoDeRegra } from '../regras/regras.service';
import {
  erroDeTamanho, frasesDoResultado, mensagemDaCategoria, mensagemDoArquivo, PassoDaImportacao, passosDaImportacao, pedePerfil, perfilNaoLe,
  resumoDoHistorico, resumoDoPerfil, tamanhoDoArquivo,
} from './apresentacao';
import { abrirExcluidos } from './excluidos-dialogo/excluidos-dialogo';
import { Amostra, Importacao as ItemDoHistorico, ImportacaoService, Previa, Resultado } from './importacao.service';
import { PerfilCsv } from './perfil-csv/perfil-csv';
import { ConfirmacaoDaRevisao, Revisao } from './revisao/revisao';

/**
 * A página de Importação (decisões 1 a 4 e 27 a 33): "Nova importação" e o histórico; escolhido
 * o arquivo, a página segue para o perfil do CSV (só na primeira vez da conta), a revisão e o
 * resultado, com o indicador de passos do mockup. O CSV com perfil salvo mostra o resumo do perfil
 * e "Revisar" (decisão 12); o OFX vai direto para a revisão. O arquivo só existe aqui, no
 * navegador: recarregar volta ao começo. Na Fatia 04b, o resultado oferece criar regras com as
 * categorias escolhidas à mão na revisão (decisão 20).
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
  private readonly categoriasService = inject(CategoriasService);
  private readonly dialogo = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);
  private readonly regras = inject(RegrasService);
  private readonly fluxoDeRegras = inject(FluxoDeRegras);
  private readonly contaDaUrl = Number(inject(ActivatedRoute).snapshot.queryParamMap.get('conta')) || null;
  /** As linhas que ganharam categoria à mão na última confirmação, para "Tentar de novo" nas sugestões. */
  private aMao: readonly EscolhaAMao[] = [];

  readonly hoje = dataIsoNoFuso(inject(AGORA)());
  readonly contas = signal<ListaDeContas | null>(null);
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
  readonly contaId = signal<number | null>(null);
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
  readonly nomeDaCategoria = nomeDaCategoria;
  readonly temContaAtiva = computed(() => (this.contas()?.ativas.length ?? 0) > 0);
  readonly nomeDaConta = computed(() => this.contas()?.ativas.find((conta) => conta.id === this.contaId())?.nome ?? '');
  readonly passos = computed(() => passosDaImportacao(this.passo(), this.previa()?.formato ?? null));
  /** Enquanto a API lê um OFX, o mockup explica que ele não pede perfil. */
  readonly lendoOfx = computed(() => this.lendo() && (this.arquivo()?.name.toLowerCase().endsWith('.ofx') ?? false));
  /** O subtítulo do cabeçalho: o arquivo e a conta na revisão e no perfil. */
  readonly subtitulo = computed(() => {
    const previa = this.previa();
    if (this.passo() === 'revisao' && previa !== null) {
      const periodo = previa.dataInicio !== null && previa.dataFim !== null
        ? ` · de ${dataCurta(previa.dataInicio).slice(0, 5)} a ${dataCurta(previa.dataFim)}` : '';
      return `${previa.nomeArquivo} · ${previa.contaNome}${periodo}`;
    }
    if (this.passo() === 'perfil') {
      return `${this.arquivo()?.name ?? ''} · ${this.nomeDaConta()}`;
    }
    return 'Traga o extrato do banco em OFX ou CSV. Nada é gravado antes da sua revisão.';
  });
  readonly frases = computed(() => {
    const resultado = this.resultado();
    return resultado === null ? null : frasesDoResultado(resultado, this.porRegras());
  });
  readonly tamanhoDoArquivo = tamanhoDoArquivo;
  readonly resumoDoPerfil = resumoDoPerfil;
  readonly resumoDoHistorico = resumoDoHistorico;
  readonly dataCurta = dataCurta;
  readonly dataHoraCurta = dataHoraCurta;

  constructor() {
    this.contasService.listar().subscribe({
      next: (lista) => {
        this.contas.set(lista);
        this.contaId.set(contaPadrao(lista.ativas, this.contaDaUrl, this.servico.ultimaConta));
      },
      error: () => this.falha.set(true),
    });
    this.categoriasService.listar().subscribe({ next: (lista) => this.categorias.set(lista), error: () => this.falha.set(true) });
    this.carregarHistorico();
  }

  /** Com um arquivo já escolhido, a troca de conta lê de novo (o perfil do CSV é da conta). */
  escolherConta(valor: string): void {
    this.contaId.set(valor === '' ? null : Number(valor));
    this.erroDoArquivo.set(null);
    this.previa.set(null);
    const arquivo = this.arquivo();
    if (arquivo !== null && erroDeTamanho(arquivo.size) === null) {
      this.lerArquivo();
    }
  }

  /** O tamanho é conferido aqui, antes de enviar (decisão 35); o resto, na API. */
  escolherArquivo(arquivos: FileList | null): void {
    const arquivo = arquivos?.item(0) ?? null;
    if (arquivo === null) {
      return;
    }
    this.arquivo.set(arquivo);
    this.previa.set(null);
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
   * alerta (mockup, linha 623): sem isso, a conta não teria outro caminho até o perfil. Com
   * `contaDeVolta`, um erro devolve a conta anterior (o "Importar em X" que não deu certo).
   */
  lerArquivo(contaDeVolta: number | null = null): void {
    const arquivo = this.arquivo();
    const contaId = this.contaId();
    if (arquivo === null) {
      return;
    }
    if (contaId === null) {
      this.erroDoArquivo.set('Escolha a conta.');
      return;
    }
    const doArquivo = this.passo() === 'arquivo';
    const doPerfil = this.passo() === 'perfil';
    this.lendo.set(true);
    this.erroDoArquivo.set(null);
    this.erroDaConfirmacao.set(null);
    this.servico.previa(arquivo, contaId).subscribe({
      next: (previa) => {
        this.servico.ultimaConta = contaId;
        this.lendo.set(false);
        this.previa.set(previa);
        this.passo.set(doArquivo && previa.formato === 'CSV' ? 'arquivo' : 'revisao');
      },
      error: (erro: unknown) => {
        if (pedePerfil(erro)) {
          this.passoAntesDoPerfil.set(null);
          this.abrirPerfil(null);
        } else if (perfilNaoLe(erro) && !doPerfil) {
          this.passoAntesDoPerfil.set(null);
          this.erroDoArquivo.set(mensagemDoArquivo(erro));
          this.servico.perfil(contaId).subscribe({ next: (formato) => this.abrirPerfil(formato), error: () => this.abrirPerfil(null) });
        } else {
          this.lendo.set(false);
          this.erroDoArquivo.set(mensagemDoArquivo(erro));
          if (contaDeVolta !== null) {
            this.contaId.set(contaDeVolta);
          }
        }
      },
    });
  }

  /** "Editar perfil" (resumo) ou "Editar perfil do CSV" (revisão): a amostra com o perfil salvo. */
  editarPerfil(): void {
    const contaId = this.contaId();
    if (contaId !== null) {
      this.passoAntesDoPerfil.set(this.passo() === 'revisao' ? 'revisao' : 'arquivo');
      this.lendo.set(true);
      this.erroDoArquivo.set(null);
      this.servico.perfil(contaId).subscribe({ next: (formato) => this.abrirPerfil(formato), error: () => this.abrirPerfil(null) });
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
    const anterior = this.contaId();
    this.contaId.set(contaId);
    this.lerArquivo(anterior);
  }

  importar(confirmacao: ConfirmacaoDaRevisao): void {
    const arquivo = this.arquivo();
    const previa = this.previa();
    if (arquivo === null || previa === null || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.erroDaConfirmacao.set(null);
    this.servico.confirmar(arquivo, previa.contaId, previa.hash, confirmacao.escolhas).subscribe({
      next: (resultado) => {
        this.enviando.set(false);
        this.porRegras.set(confirmacao.porRegras);
        this.resultado.set(resultado);
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

  recomecar(): void {
    this.passo.set('arquivo');
    this.arquivo.set(null);
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

  /** O histórico recarrega em qualquer fechamento: o Esc e o clique fora também fecham depois de restaurar. */
  verExcluidos(item: ItemDoHistorico): void {
    abrirExcluidos(this.dialogo, item).subscribe(() => this.carregarHistorico());
  }

  /** Decisão 32: confirmação com o que sai; com a conta arquivada, só o aviso e o caminho para Contas. */
  desfazer(item: ItemDoHistorico): void {
    if (item.contaArquivada) {
      abrirConfirmacao(this.dialogo, {
        titulo: 'Esta importação não pode ser desfeita agora',
        icone: 'arquivar',
        perigo: false,
        paragrafos: [[{ forte: item.contaNome }, ' está arquivada. Reative a conta para desfazer esta importação.']],
        confirmar: 'Ir para Contas',
        cancelar: 'Fechar',
        falha: 'Não foi possível abrir Contas.',
        acao: () => of(true),
      }).subscribe((resultado) => {
        if (resultado === true) {
          void this.router.navigate(['/contas']);
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
    const paragrafo: TrechoDeTexto[] = ['Os ', { forte: item.importadas === 1 ? '1 lançamento' : `${item.importadas} lançamentos` },
      ` que ela trouxe saem de ${item.contaNome}${extras.length > 0 ? `, inclusive ${extras.join(' e ')}` : ''}. O saldo muda na hora.`];
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

  carregarHistorico(): void {
    this.servico.listar().subscribe({ next: (lista) => this.historico.set(lista), error: () => this.historico.set(null) });
  }

  private abrirPerfil(formato: Parameters<ImportacaoService['amostra']>[1]): void {
    const arquivo = this.arquivo();
    if (arquivo === null) {
      return;
    }
    this.servico.amostra(arquivo, formato).subscribe({
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
