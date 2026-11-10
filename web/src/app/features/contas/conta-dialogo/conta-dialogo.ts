import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { Observable } from 'rxjs';

import { Alerta } from '../../../shared/alerta/alerta';
import { Icone } from '../../../shared/icone/icone';
import { ErroDoValor, escreverValorDigitado, lerValorDigitado, valorDigitado } from '../../../shared/moeda/valor-digitado';
import { textoObrigatorio } from '../../../shared/validadores/validadores';
import { camposDoErro, codigoDoErro, detalheDoErro, statusDoErro } from '../../auth/erros-http';
import { CORES_DE_CONTA, TIPOS_DE_CONTA } from '../apresentacao';
import { Conta, ContasService, CorConta, DadosDaConta, TipoConta } from '../contas.service';

export interface DadosDoDialogoDeConta {
  /** null = Nova conta. */
  readonly conta: Conta | null;
  /** Hoje no fuso do negócio ("2026-11-09"): data padrão e máxima. */
  readonly hoje: string;
}

type CampoDaConta = 'nome' | 'saldoInicial' | 'dataSaldoInicial';

const MENSAGENS_DO_VALOR: Readonly<Record<ErroDoValor, string>> = {
  vazio: 'Informe o saldo inicial.',
  invalido: 'Digite um valor como 1.234,56.',
  casas: 'Use no máximo 2 casas decimais.',
  grande: 'Use no máximo 13 dígitos antes da vírgula.',
};

/** Recusa datas depois de {@code hoje}. As datas ISO comparam bem como texto. */
function dataAte(hoje: string) {
  return (controle: AbstractControl): ValidationErrors | null =>
    typeof controle.value === 'string' && controle.value > hoje ? { futura: true } : null;
}

/** "Nova conta" e "Editar conta". Fecha com a conta salva. */
@Component({
  selector: 'app-conta-dialogo',
  imports: [MatButtonModule, MatDialogModule, ReactiveFormsModule, Alerta, Icone],
  templateUrl: './conta-dialogo.html',
  styleUrl: './conta-dialogo.scss',
})
export class ContaDialogo {
  private readonly servico = inject(ContasService);
  private readonly referencia = inject<MatDialogRef<ContaDialogo, Conta>>(MatDialogRef);

  readonly dados = inject<DadosDoDialogoDeConta>(MAT_DIALOG_DATA);
  readonly tipos = TIPOS_DE_CONTA;
  readonly cores = CORES_DE_CONTA;
  readonly formulario = new FormGroup({
    nome: new FormControl(this.dados.conta?.nome ?? '', { nonNullable: true, validators: [textoObrigatorio, Validators.maxLength(60)] }),
    tipo: new FormControl<TipoConta>(this.dados.conta?.tipo ?? 'CORRENTE', { nonNullable: true }),
    saldoInicial: new FormControl(escreverValorDigitado(this.dados.conta?.saldoInicial ?? 0), { nonNullable: true, validators: [valorDigitado] }),
    dataSaldoInicial: new FormControl(this.dados.conta?.dataSaldoInicial ?? this.dados.hoje, {
      nonNullable: true,
      validators: [Validators.required, dataAte(this.dados.hoje)],
    }),
    cor: new FormControl<CorConta | null>(this.dados.conta?.cor ?? null),
  });
  readonly enviando = signal(false);
  readonly erroGeral = signal<string | null>(null);
  /** Erros que vieram da API, por campo; somem quando o campo muda. */
  readonly errosDaApi = signal<Partial<Record<CampoDaConta, string>>>({});

  constructor() {
    for (const campo of ['nome', 'saldoInicial', 'dataSaldoInicial'] as const) {
      this.formulario.controls[campo].valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
        this.errosDaApi.update(({ [campo]: removido, ...outros }) => {
          void removido;
          return outros;
        });
      });
    }
  }

  /** Fecha sem resultado (undefined), como o Esc e o clique fora. */
  cancelar(): void {
    this.referencia.close();
  }

  get editando(): boolean {
    return this.dados.conta !== null;
  }

  erro(campo: CampoDaConta): string | null {
    const daApi = this.errosDaApi()[campo];
    if (daApi !== undefined) {
      return daApi;
    }
    const controle = this.formulario.controls[campo];
    if (!controle.touched || controle.valid) {
      return null;
    }
    if (campo === 'nome') {
      return controle.hasError('required') ? 'Informe o nome.' : 'Use no máximo 60 caracteres.';
    }
    if (campo === 'saldoInicial') {
      return MENSAGENS_DO_VALOR[controle.getError('valor') as ErroDoValor];
    }
    return controle.hasError('required') ? 'Informe a data do saldo inicial.' : 'A data não pode ser depois de hoje.';
  }

  /** Ao sair do campo, reescreve o valor no formato "1.234,56". */
  formatarSaldo(): void {
    const controle = this.formulario.controls.saldoInicial;
    const lido = lerValorDigitado(controle.value);
    if ('valor' in lido) {
      controle.setValue(escreverValorDigitado(lido.valor), { emitEvent: false });
    }
  }

  salvar(): void {
    if (this.enviando()) {
      return;
    }
    this.formulario.markAllAsTouched();
    const valores = this.formulario.getRawValue();
    const lido = lerValorDigitado(valores.saldoInicial);
    if (this.formulario.invalid || !('valor' in lido)) {
      return;
    }
    const pedido: DadosDaConta = {
      nome: valores.nome.trim(),
      tipo: valores.tipo,
      saldoInicial: lido.valor,
      dataSaldoInicial: valores.dataSaldoInicial,
      cor: valores.cor,
    };
    this.enviando.set(true);
    // Esc e clique fora esperam a resposta: fechar agora perderia o recarregamento da lista.
    this.referencia.disableClose = true;
    this.erroGeral.set(null);
    this.errosDaApi.set({});
    const chamada: Observable<Conta> = this.dados.conta === null
      ? this.servico.criar(pedido)
      : this.servico.atualizar(this.dados.conta.id, pedido);
    chamada.subscribe({
      next: (salva) => this.referencia.close(salva),
      error: (erro: unknown) => {
        this.tratarErro(erro);
        this.enviando.set(false);
        this.referencia.disableClose = false;
      },
    });
  }

  private tratarErro(erro: unknown): void {
    const status = statusDoErro(erro);
    if (status === 400) {
      const campos = camposDoErro(erro);
      const conhecidos: Partial<Record<CampoDaConta, string>> = {};
      for (const campo of ['nome', 'saldoInicial', 'dataSaldoInicial'] as const) {
        if (campos[campo] !== undefined) {
          conhecidos[campo] = campos[campo];
        }
      }
      this.errosDaApi.set(conhecidos);
      if (Object.keys(conhecidos).length === 0) {
        this.erroGeral.set('Confira os campos e tente de novo.');
      }
    } else if (status === 422 && codigoDoErro(erro) === 'NOME_EM_USO') {
      this.errosDaApi.set({ nome: detalheDoErro(erro, 'Já existe uma conta ativa com este nome.') });
    } else if (status === 422 || status === 404) {
      this.erroGeral.set(detalheDoErro(erro, 'Não foi possível salvar a conta. Tente novamente.'));
    } else {
      this.erroGeral.set('Não foi possível salvar a conta. Tente novamente.');
    }
  }
}
