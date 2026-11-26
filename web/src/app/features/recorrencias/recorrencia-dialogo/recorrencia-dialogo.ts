import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { debounceTime, filter } from 'rxjs';

import { Alerta } from '../../../shared/alerta/alerta';
import { Icone } from '../../../shared/icone/icone';
import { escreverValorDigitado, lerValorDigitado } from '../../../shared/moeda/valor-digitado';
import { Valor } from '../../../shared/valor/valor';
import { camposDoErro, detalheDoErro, statusDoErro } from '../../auth/erros-http';
import { Cartao, ListaDeCartoes } from '../../cartoes/cartoes.service';
import { opcoesDeCategoria } from '../../lancamentos/apresentacao';
import { Conta, ListaDeContas } from '../../contas/contas.service';
import { Categoria, ListaDeCategorias } from '../../categorias/categorias.service';
import { dataDaOcorrencia, dicaDaFrequencia, fraseDaFrequencia, notaDoCartao, notaDoDiaAjustado } from '../frases';
import { DadosDaRecorrencia, PreviaDaRecorrencia, Recorrencia, RecorrenciasService, TipoDaRecorrencia } from '../recorrencias.service';

export interface DadosDoDialogoDeRecorrencia {
  readonly recorrencia: Recorrencia | null;
  readonly hoje: string;
  readonly contas: ListaDeContas;
  readonly cartoes: ListaDeCartoes;
  readonly categorias: ListaDeCategorias;
}

type CampoDoDialogo = 'descricao' | 'valor' | 'alvo' | 'categoria' | 'frequencia' | 'diaDoMes' | 'comecaEm' | 'terminaEm';

const MENSAGENS_DO_VALOR: Readonly<Record<string, string>> = {
  vazio: 'Informe o valor.',
  invalido: 'Digite um valor como 1.234,56.',
  casas: 'Use no máximo 2 casas decimais.',
  grande: 'Use no máximo 13 dígitos antes da vírgula.',
};

@Component({
  selector: 'app-recorrencia-dialogo',
  imports: [MatButtonModule, MatDialogModule, ReactiveFormsModule, Alerta, Icone, Valor],
  templateUrl: './recorrencia-dialogo.html',
  styleUrl: './recorrencia-dialogo.scss',
})
export class RecorrenciaDialogo {
  private readonly servico = inject(RecorrenciasService);
  private readonly referencia = inject<MatDialogRef<RecorrenciaDialogo, Recorrencia | undefined>>(MatDialogRef);
  readonly dados = inject<DadosDoDialogoDeRecorrencia>(MAT_DIALOG_DATA);

  readonly formulario = new FormGroup({
    tipo: new FormControl<TipoDaRecorrencia>('DESPESA', { nonNullable: true }),
    descricao: new FormControl('', { nonNullable: true }),
    valor: new FormControl('', { nonNullable: true }),
    valorVaria: new FormControl(false, { nonNullable: true }),
    alvo: new FormControl('', { nonNullable: true }),
    categoria: new FormControl('', { nonNullable: true }),
    frequencia: new FormControl<'MENSAL' | 'SEMANAL' | 'ANUAL'>('MENSAL', { nonNullable: true }),
    diaDoMes: new FormControl<number | null>(Number(this.dados.hoje.slice(8, 10))),
    comecaEm: new FormControl(this.dados.hoje, { nonNullable: true }),
    terminaEm: new FormControl('', { nonNullable: true }),
  });
  readonly erros = signal<Readonly<Record<CampoDoDialogo, string | null>>>({
    descricao: null, valor: null, alvo: null, categoria: null, frequencia: null, diaDoMes: null, comecaEm: null, terminaEm: null,
  });
  readonly erroGeral = signal<string | null>(null);
  readonly enviando = signal(false);
  readonly previa = signal<PreviaDaRecorrencia | null>(null);
  readonly carregandoPrevia = signal(false);
  readonly tipo = toSignal(this.formulario.controls.tipo.valueChanges, { initialValue: this.formulario.controls.tipo.value });
  readonly alvo = toSignal(this.formulario.controls.alvo.valueChanges, { initialValue: this.formulario.controls.alvo.value });
  readonly frequencia = toSignal(this.formulario.controls.frequencia.valueChanges, {
    initialValue: this.formulario.controls.frequencia.value,
  });
  readonly diaDoMes = toSignal(this.formulario.controls.diaDoMes.valueChanges, { initialValue: this.formulario.controls.diaDoMes.value });
  readonly comecaEm = toSignal(this.formulario.controls.comecaEm.valueChanges, { initialValue: this.formulario.controls.comecaEm.value });
  readonly alvoEhCartao = computed(() => this.alvo().startsWith('cartao-'));
  readonly opcoesDeCategoria = computed(() => {
    const grupo = this.tipo() === 'DESPESA' ? this.dados.categorias.despesas : this.dados.categorias.receitas;
    const opcoes = opcoesDeCategoria(grupo);
    const atual = this.categoriaArquivadaDaEdicao();
    return atual === null || opcoes.some((opcao) => opcao.id === atual.id)
      ? opcoes
      : [...opcoes, { id: atual.id, rotulo: `${atual.nome} (arquivada)` }];
  });
  readonly dicaDaFrequencia = dicaDaFrequencia;
  readonly fraseDaFrequencia = fraseDaFrequencia;
  readonly notaDoDiaAjustado = notaDoDiaAjustado;
  readonly notaDoCartao = notaDoCartao;
  readonly dataDaOcorrencia = dataDaOcorrencia;
  readonly frequencias: readonly ('MENSAL' | 'SEMANAL' | 'ANUAL')[] = ['MENSAL', 'SEMANAL', 'ANUAL'];

  constructor() {
    const recorrencia = this.dados.recorrencia;
    if (recorrencia !== null) {
      this.formulario.setValue({
        tipo: recorrencia.tipo,
        descricao: recorrencia.descricao,
        valor: escreverValorDigitado(recorrencia.valor),
        valorVaria: recorrencia.valorVaria,
        alvo: recorrencia.contaId === null ? `cartao-${recorrencia.cartaoId}` : `conta-${recorrencia.contaId}`,
        categoria: recorrencia.categoriaId === null ? '' : String(recorrencia.categoriaId),
        frequencia: recorrencia.frequencia,
        diaDoMes: recorrencia.diaDoMes,
        comecaEm: recorrencia.comecaEm,
        terminaEm: recorrencia.terminaEm ?? '',
      });
    }
    this.formulario.valueChanges.pipe(debounceTime(300), filter(() => this.podePrever())).subscribe(() => this.carregarPrevia());
    this.carregarPrevia();
  }

  escolherTipo(tipo: TipoDaRecorrencia): void {
    if (tipo === 'RECEITA' && this.alvoEhCartao()) {
      return;
    }
    this.formulario.controls.tipo.setValue(tipo);
    this.formulario.controls.categoria.setValue('');
  }

  escolherFrequencia(frequencia: 'MENSAL' | 'SEMANAL' | 'ANUAL'): void {
    if (this.dados.recorrencia !== null) {
      return;
    }
    this.formulario.controls.frequencia.setValue(frequencia);
    if (frequencia !== 'MENSAL') {
      this.formulario.controls.diaDoMes.setValue(null);
    } else if (this.formulario.controls.diaDoMes.value === null) {
      this.formulario.controls.diaDoMes.setValue(Number(this.dados.hoje.slice(8, 10)));
    }
  }

  mudouAlvo(): void {
    if (this.alvoEhCartao()) {
      this.formulario.controls.tipo.setValue('DESPESA');
    }
  }

  salvar(): void {
    const dados = this.dadosParaSalvar();
    if (dados === null) {
      return;
    }
    this.enviando.set(true);
    this.erroGeral.set(null);
    const pedido = this.dados.recorrencia === null ? this.servico.criar(dados) : this.servico.editar(this.dados.recorrencia.id, dados);
    pedido.subscribe({
      next: (recorrencia) => this.referencia.close(recorrencia),
      error: (erro: unknown) => {
        this.enviando.set(false);
        if (statusDoErro(erro) === 400) {
          this.aplicarErros(camposDoErro(erro));
        } else {
          this.erroGeral.set(detalheDoErro(erro, 'Não foi possível salvar. Tente novamente.'));
        }
      },
    });
  }

  erro(campo: CampoDoDialogo): string | null {
    return this.erros()[campo];
  }

  alvoArquivado(): string | null {
    const alvo = this.formulario.controls.alvo.value;
    const conta = alvo.startsWith('conta-') ? this.conta(Number(alvo.slice(6))) : null;
    const cartao = alvo.startsWith('cartao-') ? this.cartao(Number(alvo.slice(7))) : null;
    return conta?.arquivadaEm ? `${conta.nome} (arquivada)` : cartao?.arquivadoEm ? `${cartao.nome} (arquivado)` : null;
  }

  private podePrever(): boolean {
    const { frequencia, diaDoMes, comecaEm, terminaEm } = this.formulario.getRawValue();
    return comecaEm !== '' && (frequencia !== 'MENSAL' || diaDoMes !== null) && (terminaEm === '' || terminaEm >= comecaEm);
  }

  private carregarPrevia(): void {
    if (!this.podePrever()) {
      this.previa.set(null);
      return;
    }
    const valor = this.formulario.getRawValue();
    const alvo = valor.alvo;
    this.carregandoPrevia.set(true);
    this.servico.previa({
      frequencia: valor.frequencia,
      diaDoMes: valor.frequencia === 'MENSAL' ? valor.diaDoMes : null,
      comecaEm: valor.comecaEm,
      terminaEm: valor.terminaEm === '' ? null : valor.terminaEm,
      cartaoId: alvo.startsWith('cartao-') ? Number(alvo.slice(7)) : null,
      recorrenciaId: this.dados.recorrencia?.id ?? null,
    }).subscribe({
      next: (previa) => {
        this.previa.set(previa);
        this.carregandoPrevia.set(false);
      },
      error: () => this.carregandoPrevia.set(false),
    });
  }

  valorDaPrevia(): number | null {
    const lido = lerValorDigitado(this.formulario.controls.valor.value);
    return 'erro' in lido || lido.valor <= 0 ? null : lido.valor;
  }

  private dadosParaSalvar(): DadosDaRecorrencia | null {
    const valor = this.formulario.getRawValue();
    const erros: Record<CampoDoDialogo, string | null> = {
      descricao: null, valor: null, alvo: null, categoria: null, frequencia: null, diaDoMes: null, comecaEm: null, terminaEm: null,
    };
    const lido = lerValorDigitado(valor.valor);
    if ('erro' in lido) {
      erros.valor = MENSAGENS_DO_VALOR[lido.erro];
    } else if (lido.valor <= 0) {
      erros.valor = 'O valor precisa ser maior que zero.';
    }
    if (valor.descricao.trim() === '') {
      erros.descricao = 'Escreva a descrição.';
    }
    if (valor.alvo === '') {
      erros.alvo = 'Escolha a conta ou o cartão.';
    }
    if (valor.frequencia === 'MENSAL' && (valor.diaDoMes === null || valor.diaDoMes < 1 || valor.diaDoMes > 31)) {
      erros.diaDoMes = 'Escolha um dia de 1 a 31.';
    }
    if (valor.comecaEm === '') {
      erros.comecaEm = 'Informe o início.';
    }
    if (valor.terminaEm !== '' && valor.comecaEm !== '' && valor.terminaEm < valor.comecaEm) {
      erros.terminaEm = `O fim precisa ser no dia do início ou depois (${valor.comecaEm.split('-').reverse().join('/')}).`;
    }
    this.erros.set(erros);
    if (Object.values(erros).some((erro) => erro !== null) || 'erro' in lido) {
      return null;
    }
    const contaId = valor.alvo.startsWith('conta-') ? Number(valor.alvo.slice(6)) : null;
    const cartaoId = valor.alvo.startsWith('cartao-') ? Number(valor.alvo.slice(7)) : null;
    return {
      tipo: valor.tipo,
      descricao: valor.descricao.trim(),
      valor: lido.valor,
      valorVaria: valor.valorVaria,
      categoriaId: valor.categoria === '' ? null : Number(valor.categoria),
      contaId,
      cartaoId,
      frequencia: valor.frequencia,
      diaDoMes: valor.frequencia === 'MENSAL' ? valor.diaDoMes : null,
      comecaEm: valor.comecaEm,
      terminaEm: valor.terminaEm === '' ? null : valor.terminaEm,
    };
  }

  private aplicarErros(campos: Readonly<Record<string, string>>): void {
    this.erros.set({
      descricao: campos['descricao'] ?? null,
      valor: campos['valor'] ?? null,
      alvo: campos['contaId'] ?? campos['cartaoId'] ?? null,
      categoria: campos['categoriaId'] ?? null,
      frequencia: campos['frequencia'] ?? null,
      diaDoMes: campos['diaDoMes'] ?? null,
      comecaEm: campos['comecaEm'] ?? null,
      terminaEm: campos['terminaEm'] ?? null,
    });
  }

  private conta(id: number): Conta | undefined {
    return [...this.dados.contas.ativas, ...this.dados.contas.arquivadas].find((conta) => conta.id === id);
  }

  private cartao(id: number): Cartao | undefined {
    return [...this.dados.cartoes.ativos, ...this.dados.cartoes.arquivados].find((cartao) => cartao.id === id);
  }

  private categoriaArquivadaDaEdicao(): Categoria | null {
    const id = this.dados.recorrencia?.categoriaId;
    if (id === null || id === undefined) {
      return null;
    }
    const grupos = [this.dados.categorias.despesas, this.dados.categorias.receitas];
    for (const grupo of grupos) {
      for (const principal of [...grupo.ativas, ...grupo.arquivadas]) {
        if (principal.id === id) {
          return principal;
        }
        const sub = principal.subcategorias.find((categoria) => categoria.id === id);
        if (sub !== undefined) {
          return sub;
        }
      }
    }
    return null;
  }
}

export function abrirRecorrencia(dialogo: MatDialog, dados: DadosDoDialogoDeRecorrencia) {
  return dialogo.open<RecorrenciaDialogo, DadosDoDialogoDeRecorrencia, Recorrencia | undefined>(RecorrenciaDialogo, {
    data: dados,
    panelClass: ['flux-dialogo', 'flux-dialogo--cheio-no-celular'],
    width: '640px',
    maxWidth: 'calc(100vw - 32px)',
    maxHeight: 'calc(100vh - 32px)',
  }).afterClosed();
}
