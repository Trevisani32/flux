import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router } from '@angular/router';

import { AGORA } from '../../core/tempo/agora';
import { Alerta } from '../../shared/alerta/alerta';
import { CabecalhoPagina } from '../../shared/cabecalho-pagina/cabecalho-pagina';
import { Card } from '../../shared/card/card';
import { abrirConfirmacao } from '../../shared/confirmacao/confirmacao-dialogo';
import { dataCurta, dataCurtaDoInstante, dataIsoNoFuso } from '../../shared/datas/data-curta';
import { EstadoVazio } from '../../shared/estado-vazio/estado-vazio';
import { Icone } from '../../shared/icone/icone';
import { SaldoEmDestaque } from '../../shared/saldo-em-destaque/saldo-em-destaque';
import { Valor } from '../../shared/valor/valor';
import { detalheDoErro, statusDoErro } from '../auth/erros-http';
import { corDaConta, quantidadeDeContasAtivas, tipoDaConta } from './apresentacao';
import { ContaDialogo, DadosDoDialogoDeConta } from './conta-dialogo/conta-dialogo';
import { Conta, ContasService, ListaDeContas } from './contas.service';

@Component({
  selector: 'app-contas',
  imports: [MatButtonModule, MatMenuModule, MatDividerModule, Alerta, CabecalhoPagina, Card, EstadoVazio, Icone, SaldoEmDestaque, Valor],
  templateUrl: './contas.html',
  styleUrl: './contas.scss',
})
export class Contas {
  private readonly servico = inject(ContasService);
  private readonly dialogo = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly rota = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly agora = inject(AGORA);

  readonly tipoDaConta = tipoDaConta;
  readonly corDaConta = corDaConta;
  readonly dataCurta = dataCurta;
  readonly dataCurtaDoInstante = dataCurtaDoInstante;
  readonly quantidadeDeContasAtivas = quantidadeDeContasAtivas;
  readonly lista = signal<ListaDeContas | null>(null);
  readonly falhaAoCarregar = signal(false);
  readonly arquivadasAbertas = signal(false);
  readonly erroAoReativar = signal<string | null>(null);
  /** "Cadastrar conta" do Início chega com ?nova=conta: abre o diálogo uma vez. */
  private novaPedida = this.rota.snapshot.queryParamMap.get('nova') === 'conta';

  constructor() {
    this.carregar();
  }

  carregar(): void {
    this.falhaAoCarregar.set(false);
    this.servico.listar().subscribe({
      next: (lista) => {
        this.lista.set(lista);
        if (this.novaPedida) {
          this.novaPedida = false;
          void this.router.navigate([], { relativeTo: this.rota, queryParams: { nova: null }, replaceUrl: true });
          this.novaConta();
        }
      },
      error: () => this.falhaAoCarregar.set(true),
    });
  }

  hojeCurto(): string {
    return dataCurta(dataIsoNoFuso(this.agora()));
  }

  novaConta(): void {
    this.abrirDialogo(null);
  }

  editar(conta: Conta): void {
    this.abrirDialogo(conta);
  }

  /** A lista de lançamentos do mês atual, filtrada pela conta (decisão 31). */
  verLancamentos(conta: Conta): void {
    void this.router.navigate(['/lancamentos'], { queryParams: { conta: conta.id } });
  }

  arquivar(conta: Conta): void {
    if (conta.saldoAtual === 0) {
      this.servico.arquivar(conta.id).subscribe({
        next: () => {
          this.carregar();
          this.snackBar.open(`${conta.nome} arquivada.`, 'Desfazer', { duration: 8000 })
            .onAction().subscribe(() => this.reativar(conta));
        },
        error: () => this.snackBar.open('Não foi possível arquivar a conta. Tente novamente.', 'Fechar', { duration: 5000 }),
      });
      return;
    }
    abrirConfirmacao(this.dialogo, {
      titulo: `Arquivar ${conta.nome}?`,
      icone: 'arquivar',
      perigo: false,
      alerta: { titulo: 'O saldo desta conta é', valor: conta.saldoAtual, texto: 'Ele deixa de contar no saldo total.' },
      paragrafos: [['A conta sai do saldo total e dos seletores. O histórico continua nos relatórios, e você pode reativá-la em "Arquivadas".']],
      confirmar: 'Arquivar conta',
      falha: 'Não foi possível arquivar a conta. Tente novamente.',
      acao: () => this.servico.arquivar(conta.id),
    }).subscribe((arquivada) => {
      if (arquivada === true) {
        this.carregar();
      }
    });
  }

  reativar(conta: Conta): void {
    this.erroAoReativar.set(null);
    this.servico.reativar(conta.id).subscribe({
      next: () => {
        this.carregar();
        this.snackBar.open(`${conta.nome} reativada.`, 'Fechar', { duration: 5000 });
      },
      error: (erro: unknown) => {
        this.erroAoReativar.set(statusDoErro(erro) === 422
          ? detalheDoErro(erro, 'Não foi possível reativar a conta. Tente novamente.')
          : 'Não foi possível reativar a conta. Tente novamente.');
        this.arquivadasAbertas.set(true);
      },
    });
  }

  excluir(conta: Conta): void {
    const arquivada = conta.arquivadaEm !== null;
    abrirConfirmacao(this.dialogo, {
      titulo: `Excluir ${conta.nome}?`,
      icone: 'lixeira',
      perigo: true,
      paragrafos: [['A conta sai do Flux de vez. ', { forte: 'Não dá para desfazer.' }]],
      confirmar: 'Excluir conta',
      falha: 'Não foi possível excluir a conta. Tente novamente.',
      acao: () => this.servico.excluir(conta.id),
      // Decisão 33: com lançamentos, o mesmo diálogo vira o bloqueio, com o uso que veio no 422.
      bloqueio: {
        codigo: 'CONTA_EM_USO',
        titulo: 'Esta conta não pode ser excluída',
        paragrafo: (uso) => arquivada
          ? [{ forte: conta.nome }, ` tem ${uso}. Ela continua arquivada, com o histórico nos relatórios.`]
          : [{ forte: conta.nome }, ` tem ${uso}. Para tirá-la do saldo total e dos seletores sem perder o histórico, arquive a conta.`],
        alternativa: arquivada ? undefined : 'Arquivar conta',
      },
    }).subscribe((resultado) => {
      if (resultado === true) {
        this.carregar();
      } else if (resultado === 'alternativa') {
        this.arquivar(conta);
      }
    });
  }

  alternarArquivadas(): void {
    this.arquivadasAbertas.update((abertas) => !abertas);
  }

  private abrirDialogo(conta: Conta | null): void {
    this.dialogo.open<ContaDialogo, DadosDoDialogoDeConta, Conta>(ContaDialogo, {
      data: { conta, hoje: dataIsoNoFuso(this.agora()) },
      panelClass: 'flux-dialogo',
      width: '540px',
      maxWidth: 'calc(100vw - 32px)',
      maxHeight: 'calc(100vh - 32px)',
      autoFocus: '#conta-nome',
    }).afterClosed().subscribe((salva) => {
      if (salva !== undefined) {
        this.carregar();
      }
    });
  }
}
