import { Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { debounceTime, filter } from 'rxjs';

import { Alerta } from '../../../shared/alerta/alerta';
import { dataCurta } from '../../../shared/datas/data-curta';
import { Icone } from '../../../shared/icone/icone';
import { escreverValorDigitado, lerValorDigitado } from '../../../shared/moeda/valor-digitado';
import { Valor } from '../../../shared/valor/valor';
import { camposDoErro, detalheDoErro, statusDoErro } from '../../auth/erros-http';
import { mesSemAno, textoDaCategoria } from '../../cartoes/apresentacao';
import { ComprasService, PreviaDaCompra } from '../../cartoes/compras.service';
import { dataLonga, dicaDaAtrasada } from '../frases';
import { Candidato, Candidatos, Confirmacao, Ocorrencia, RecorrenciasService } from '../recorrencias.service';

export interface DadosDoConfirmarDialogo {
  readonly ocorrencia: Ocorrencia;
  readonly hoje: string;
  readonly jaLancei?: boolean;
}

@Component({
  selector: 'app-confirmar-dialogo',
  imports: [MatButtonModule, MatDialogModule, ReactiveFormsModule, Alerta, Icone, Valor],
  templateUrl: './confirmar-dialogo.html',
  styleUrl: './confirmar-dialogo.scss',
})
export class ConfirmarDialogo {
  private readonly servico = inject(RecorrenciasService);
  private readonly compras = inject(ComprasService);
  private readonly referencia = inject<MatDialogRef<ConfirmarDialogo, ResultadoDoConfirmar | undefined>>(MatDialogRef);
  readonly dados = inject<DadosDoConfirmarDialogo>(MAT_DIALOG_DATA);

  readonly formulario = new FormGroup({
    data: new FormControl(this.dados.ocorrencia.data, { nonNullable: true }),
    valor: new FormControl(
      this.dados.ocorrencia.valorVaria ? '' : escreverValorDigitado(this.dados.ocorrencia.valorPrevisto),
      { nonNullable: true },
    ),
    observacao: new FormControl('', { nonNullable: true }),
  });
  readonly candidatos = signal<Candidatos | null>(null);
  readonly erro = signal<string | null>(null);
  readonly erros = signal<Readonly<Record<'data' | 'valor' | 'observacao', string | null>>>({ data: null, valor: null, observacao: null });
  readonly enviando = signal(false);
  readonly jaLancei = signal(this.dados.jaLancei === true);
  readonly escolhido = signal<number | null>(null);
  readonly previaDaCompra = signal<PreviaDaCompra | null>(null);
  readonly titulo = computed(() => this.jaLancei()
    ? `Já lancei: ${this.dados.ocorrencia.descricao}`
    : `Confirmar ${this.dados.ocorrencia.descricao}`);
  readonly textoDoValorVariavel = 'O valor varia: digite o da conta (estimativa ';
  readonly dataLonga = dataLonga;
  readonly dataCurta = dataCurta;
  readonly dicaDaAtrasada = dicaDaAtrasada;
  readonly textoDaCategoria = textoDaCategoria;
  readonly mesSemAno = mesSemAno;
  readonly deCartao = this.dados.ocorrencia.cartaoId !== null;

  constructor() {
    this.carregarCandidatos();
    if (this.deCartao) {
      this.formulario.valueChanges.pipe(
        debounceTime(300),
        filter(() => this.valorValido() !== null),
      ).subscribe(() => this.carregarPreviaDaCompra());
      this.carregarPreviaDaCompra();
    }
  }

  confirmar(): void {
    const valor = this.valorValido();
    const data = this.formulario.controls.data.value;
    if (valor === null || data === '') {
      this.erros.set({ data: data === '' ? 'Informe a data.' : null, valor: valor === null ? 'Informe o valor.' : null, observacao: null });
      return;
    }
    this.enviando.set(true);
    this.erro.set(null);
    this.servico.confirmar(this.dados.ocorrencia.recorrenciaId, this.dados.ocorrencia.periodo, {
      data,
      valor,
      observacao: this.formulario.controls.observacao.value.trim() || null,
    }).subscribe({
      next: (confirmacao) => this.referencia.close(confirmacao),
      error: (erro: unknown) => this.tratarErro(erro),
    });
  }

  ligar(candidato?: Candidato): void {
    const id = candidato?.id ?? this.escolhido();
    if (id === null || id === undefined) {
      return;
    }
    const corpo = this.deCartao ? { lancamentoId: null, compraId: id } : { lancamentoId: id, compraId: null };
    this.enviando.set(true);
    this.erro.set(null);
    this.servico.ligar(this.dados.ocorrencia.recorrenciaId, this.dados.ocorrencia.periodo, corpo).subscribe({
      next: (confirmacao) => this.referencia.close({ ...confirmacao, ligada: true }),
      error: (erro: unknown) => this.tratarErro(erro),
    });
  }

  abrirJaLancei(): void {
    this.jaLancei.set(true);
  }

  voltar(): void {
    this.jaLancei.set(false);
  }

  pular(): void {
    this.servico.pular(this.dados.ocorrencia.recorrenciaId, this.dados.ocorrencia.periodo).subscribe({
      next: () => this.referencia.close('pulada'),
      error: (erro: unknown) => this.tratarErro(erro),
    });
  }

  selecionar(candidato: Candidato): void {
    this.escolhido.set(candidato.id);
  }

  private carregarCandidatos(): void {
    this.servico.candidatos(this.dados.ocorrencia.recorrenciaId, this.dados.ocorrencia.periodo).subscribe({
      next: (candidatos) => {
        this.candidatos.set(candidatos);
        if (this.jaLancei() && candidatos.candidatos[0] !== undefined) {
          this.escolhido.set(candidatos.candidatos[0].id);
        }
      },
      error: () => this.candidatos.set(null),
    });
  }

  private carregarPreviaDaCompra(): void {
    const valor = this.valorValido() ?? this.dados.ocorrencia.valorPrevisto;
    const data = this.formulario.controls.data.value;
    if (data === '') {
      return;
    }
    this.compras.previa({ cartaoId: this.dados.ocorrencia.cartaoId!, data, valorTotal: valor, parcelas: 1, parcelaInicial: 1 })
      .subscribe({ next: (previa) => this.previaDaCompra.set(previa), error: () => this.previaDaCompra.set(null) });
  }

  private valorValido(): number | null {
    const lido = lerValorDigitado(this.formulario.controls.valor.value);
    return 'erro' in lido || lido.valor <= 0 ? null : lido.valor;
  }

  private tratarErro(erro: unknown): void {
    this.enviando.set(false);
    if (statusDoErro(erro) === 400) {
      const campos = camposDoErro(erro);
      this.erros.set({ data: campos['data'] ?? null, valor: campos['valor'] ?? null, observacao: campos['observacao'] ?? null });
    } else {
      this.erro.set(detalheDoErro(erro, 'Não foi possível confirmar. Tente novamente.'));
    }
  }
}

/** O que o diálogo devolve: a confirmação (com `ligada` quando ligou a um lançamento ou compra que já existia) ou 'pulada'. */
export type ResultadoDoConfirmar = (Confirmacao & { readonly ligada?: boolean }) | 'pulada';

export function abrirConfirmarOcorrencia(dialogo: MatDialog, dados: DadosDoConfirmarDialogo) {
  return dialogo.open<ConfirmarDialogo, DadosDoConfirmarDialogo, ResultadoDoConfirmar | undefined>(ConfirmarDialogo, {
    data: dados,
    panelClass: ['flux-dialogo', 'flux-dialogo--embaixo-no-celular'],
    width: '520px',
    maxWidth: 'calc(100vw - 24px)',
    autoFocus: dados.ocorrencia.valorVaria ? '#confirmar-valor' : '#confirmar-data',
  }).afterClosed();
}
