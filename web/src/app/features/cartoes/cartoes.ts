import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { Alerta } from '../../shared/alerta/alerta';
import { CabecalhoPagina } from '../../shared/cabecalho-pagina/cabecalho-pagina';
import { abrirConfirmacao, TrechoDeTexto } from '../../shared/confirmacao/confirmacao-dialogo';
import { dataCurtaDoInstante } from '../../shared/datas/data-curta';
import { EstadoVazio } from '../../shared/estado-vazio/estado-vazio';
import { Icone } from '../../shared/icone/icone';
import { partesDaMoeda } from '../../shared/moeda/moeda';
import { Valor } from '../../shared/valor/valor';
import { detalheDoErro, statusDoErro } from '../auth/erros-http';
import { corDaConta } from '../contas/apresentacao';
import { ContasService, ListaDeContas } from '../contas/contas.service';
import { diaEMes, diasDoCartao, mesSemAno, quantidadeDeCartoesAtivos, textoDoTotal } from './apresentacao';
import { CartaoDialogo, DadosDoDialogoDeCartao } from './cartao-dialogo/cartao-dialogo';
import { Cartao, CartoesService, ListaDeCartoes } from './cartoes.service';

/**
 * A tela de Cartões (decisões 1 a 6, 26 e 29 da 05a): um card por cartão ativo, com os dias, a conta de pagamento, o
 * limite e a fatura aberta; os arquivados embaixo. O cartão precisa de uma conta corrente, poupança ou carteira.
 */
@Component({
  selector: 'app-cartoes',
  imports: [MatButtonModule, MatMenuModule, MatDividerModule, RouterLink, Alerta, CabecalhoPagina, EstadoVazio, Icone, Valor],
  templateUrl: './cartoes.html',
  styleUrl: './cartoes.scss',
})
export class Cartoes {
  private readonly servico = inject(CartoesService);
  private readonly contasService = inject(ContasService);
  private readonly dialogo = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly rota = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly corDoCartao = corDaConta;
  readonly diasDoCartao = diasDoCartao;
  readonly diaEMes = diaEMes;
  readonly mesSemAno = mesSemAno;
  readonly textoDoTotal = textoDoTotal;
  readonly dataCurtaDoInstante = dataCurtaDoInstante;
  readonly lista = signal<ListaDeCartoes | null>(null);
  readonly contas = signal<ListaDeContas | null>(null);
  readonly falhaAoCarregar = signal(false);
  readonly arquivadosAbertos = signal(false);
  readonly erroAoReativar = signal<string | null>(null);
  /** Decisão 4: só conta ativa corrente, poupança ou carteira paga fatura. */
  readonly temContaQuePaga = computed(() => (this.contas()?.ativas ?? []).some((conta) => conta.tipo !== 'INVESTIMENTO'));
  readonly subtitulo = computed(() => {
    const ativos = this.lista()?.ativos.length ?? 0;
    return ativos === 0 ? 'A compra entra na fatura pelo dia de fechamento.'
      : `${quantidadeDeCartoesAtivos(ativos)}. A compra entra na fatura pelo dia de fechamento.`;
  });
  /** "Novo cartão" do Início chega com ?novo=cartao: abre o diálogo uma vez. */
  private novoPedido = this.rota.snapshot.queryParamMap.get('novo') === 'cartao';

  constructor() {
    this.carregar();
  }

  carregar(): void {
    this.falhaAoCarregar.set(false);
    this.contasService.listar().subscribe({
      next: (contas) => {
        this.contas.set(contas);
        this.abrirOPedido();
      },
      error: () => this.falhaAoCarregar.set(true),
    });
    this.servico.listar().subscribe({
      next: (lista) => {
        this.lista.set(lista);
        this.abrirOPedido();
      },
      error: () => this.falhaAoCarregar.set(true),
    });
  }

  novoCartao(): void {
    this.abrirDialogo(null);
  }

  editar(cartao: Cartao): void {
    this.abrirDialogo(cartao);
  }

  verFatura(cartao: Cartao): void {
    void this.router.navigate(['/cartoes', cartao.id, 'faturas', cartao.faturaAberta.mes]);
  }

  /** Decisão 5: com lançamentos em faturas que não fecharam, a confirmação diz quanto; sem eles, arquiva direto. */
  arquivar(cartao: Cartao): void {
    if (cartao.lancamentosEmAberto === 0) {
      this.servico.arquivar(cartao.id).subscribe({
        next: () => {
          this.carregar();
          this.snackBar.open('Cartão arquivado.', 'Desfazer', { duration: 8000 }).onAction().subscribe(() => this.reativar(cartao));
        },
        error: () => this.snackBar.open('Não foi possível arquivar o cartão. Tente novamente.', 'Fechar', { duration: 5000 }),
      });
      return;
    }
    const partes = partesDaMoeda(cartao.emAberto);
    const quanto: TrechoDeTexto[] = partes === null ? ['lançamentos']
      : partes.negativo ? ['um crédito de ', { forte: partes.absoluto }] : [{ forte: partes.absoluto }];
    abrirConfirmacao(this.dialogo, {
      titulo: `Arquivar ${cartao.nome}?`,
      icone: 'arquivar',
      perigo: false,
      paragrafos: [['Ele tem ', ...quanto, ' em faturas abertas e futuras. Arquivado, sai dos seletores, e as compras e faturas dele '
        + 'ficam só para leitura até você reativar.']],
      confirmar: 'Arquivar cartão',
      falha: 'Não foi possível arquivar o cartão. Tente novamente.',
      acao: () => this.servico.arquivar(cartao.id),
    }).subscribe((arquivado) => {
      if (arquivado === true) {
        this.carregar();
        this.snackBar.open('Cartão arquivado.', 'Fechar', { duration: 4000 });
      }
    });
  }

  reativar(cartao: Cartao): void {
    this.erroAoReativar.set(null);
    this.servico.reativar(cartao.id).subscribe({
      next: () => {
        this.carregar();
        this.snackBar.open(`${cartao.nome} reativado.`, 'Fechar', { duration: 5000 });
      },
      error: (erro: unknown) => {
        this.erroAoReativar.set(statusDoErro(erro) === 422
          ? detalheDoErro(erro, 'Não foi possível reativar o cartão. Tente novamente.')
          : 'Não foi possível reativar o cartão. Tente novamente.');
        this.arquivadosAbertos.set(true);
      },
    });
  }

  /** Decisão 5: com compras ou estornos, o mesmo diálogo vira o bloqueio, com o uso que veio no 422, e leva a arquivar. */
  excluir(cartao: Cartao): void {
    const arquivado = cartao.arquivadoEm !== null;
    abrirConfirmacao(this.dialogo, {
      titulo: `Excluir ${cartao.nome}?`,
      icone: 'lixeira',
      perigo: true,
      paragrafos: [['O cartão sai do Flux de vez, com as faturas vazias. ', { forte: 'Não dá para desfazer.' }]],
      confirmar: 'Excluir cartão',
      falha: 'Não foi possível excluir o cartão. Tente novamente.',
      acao: () => this.servico.excluir(cartao.id),
      bloqueio: {
        codigo: 'CARTAO_EM_USO',
        titulo: 'Este cartão não pode ser excluído',
        paragrafo: (uso) => arquivado
          ? [{ forte: cartao.nome }, ` tem ${uso}. Ele continua arquivado, com as faturas para consulta.`]
          : [{ forte: cartao.nome }, ` tem ${uso}. Arquive o cartão para tirá-lo dos seletores.`],
        alternativa: arquivado ? undefined : 'Arquivar cartão',
      },
    }).subscribe((resultado) => {
      if (resultado === true) {
        this.carregar();
        this.snackBar.open('Cartão excluído.', 'Fechar', { duration: 4000 });
      } else if (resultado === 'alternativa') {
        this.arquivar(cartao);
      }
    });
  }

  alternarArquivados(): void {
    this.arquivadosAbertos.update((abertos) => !abertos);
  }

  private abrirOPedido(): void {
    const contas = this.contas();
    if (!this.novoPedido || contas === null || this.lista() === null) {
      return;
    }
    this.novoPedido = false;
    void this.router.navigate([], { relativeTo: this.rota, queryParams: { novo: null }, replaceUrl: true });
    if (this.temContaQuePaga()) {
      this.novoCartao();
    }
  }

  private abrirDialogo(cartao: Cartao | null): void {
    const contas = this.contas();
    if (contas === null) {
      return;
    }
    this.dialogo.open<CartaoDialogo, DadosDoDialogoDeCartao, Cartao>(CartaoDialogo, {
      data: { cartao, contas },
      panelClass: 'flux-dialogo',
      width: '540px',
      maxWidth: 'calc(100vw - 32px)',
      maxHeight: 'calc(100vh - 32px)',
      autoFocus: '#cartao-nome',
    }).afterClosed().subscribe((salvo) => {
      if (salvo !== undefined) {
        this.carregar();
        this.snackBar.open(cartao === null ? 'Cartão criado.' : 'Cartão salvo.', 'Fechar', { duration: 4000 });
      }
    });
  }
}
