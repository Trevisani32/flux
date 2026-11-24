import { Component, computed, DestroyRef, inject, input, OnInit, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { catchError, debounceTime, EMPTY, switchMap } from 'rxjs';

import { Alerta } from '../../../shared/alerta/alerta';
import { dataCurta } from '../../../shared/datas/data-curta';
import { Icone } from '../../../shared/icone/icone';
import { Valor } from '../../../shared/valor/valor';
import { camposDoErro, detalheDoErro, statusDoErro } from '../../auth/erros-http';
import { ehNubank, ehNubankFatura, NUBANK_CONTA, NUBANK_FATURA } from '../apresentacao';
import { Amostra, Destino, FormatoCsv, ImportacaoService } from '../importacao.service';

type Modo = 'UMA' | 'DUAS';
type Modelo = 'NUBANK_CONTA' | 'NUBANK_FATURA' | 'PERSONALIZADO';

/** Os campos que mostram o próprio erro; o resto (como "colunas") vai para o alerta do formulário. */
const CAMPOS_COM_ERRO = ['linhasDeCabecalho', 'colunaData', 'colunaDescricao', 'colunaValor', 'colunaSaidas'] as const;
type CampoComErro = (typeof CAMPOS_COM_ERRO)[number];

/**
 * "Como ler este CSV" (decisão 12): a amostra do arquivo, os campos do perfil e a primeira linha
 * lida pela API a cada mudança. "Salvar perfil e revisar" grava o perfil da conta. Os erros dos
 * campos aparecem no campo, da tela ou do 400 da API. Desde a Fatia 05c, o perfil pode ser de um cartão (decisão 39): o
 * modelo pronto é "Nubank (fatura)", a coluna única ganha "Compras vêm positivas", as colunas separadas são de créditos e
 * de compras, e o botão é "Salvar perfil e continuar" (a fatura é escolhida depois, no passo do arquivo).
 */
@Component({
  selector: 'app-perfil-csv',
  imports: [MatButtonModule, ReactiveFormsModule, Alerta, Icone, Valor],
  templateUrl: './perfil-csv.html',
  styleUrl: './perfil-csv.scss',
})
export class PerfilCsv implements OnInit {
  private readonly servico = inject(ImportacaoService);
  private readonly destruicao = inject(DestroyRef);

  readonly arquivo = input.required<File>();
  /** A conta ou, desde a Fatia 05c, o cartão do perfil. */
  readonly destino = input.required<Destino>();
  /** O nome da conta ou do cartão. */
  readonly nome = input.required<string>();
  readonly amostraInicial = input.required<Amostra>();
  /** A página lendo o arquivo de novo, depois de salvar: o botão espera. */
  readonly lendo = input(false);
  readonly salvo = output<void>();
  readonly voltar = output<void>();

  readonly amostra = signal<Amostra | null>(null);
  readonly enviando = signal(false);
  readonly erroGeral = signal<string | null>(null);
  readonly errosDosCampos = signal<Partial<Record<CampoComErro, string>>>({});
  readonly formulario = new FormGroup({
    modelo: new FormControl<Modelo>('PERSONALIZADO', { nonNullable: true }),
    separador: new FormControl<FormatoCsv['separador']>('VIRGULA', { nonNullable: true }),
    linhasDeCabecalho: new FormControl(1, { nonNullable: true }),
    colunaData: new FormControl(1, { nonNullable: true }),
    formatoData: new FormControl<FormatoCsv['formatoData']>('DD_MM_AAAA', { nonNullable: true }),
    colunaDescricao: new FormControl(2, { nonNullable: true }),
    modo: new FormControl<Modo>('UMA', { nonNullable: true }),
    colunaValor: new FormControl(3, { nonNullable: true }),
    colunaSaidas: new FormControl<number | null>(null),
    separadorDecimal: new FormControl<FormatoCsv['separadorDecimal']>('PONTO', { nonNullable: true }),
    colunaIdentificador: new FormControl<number | null>(null),
    comprasPositivas: new FormControl(false, { nonNullable: true }),
  });
  readonly modo = signal<Modo>('UMA');
  /** Fatia 05c: o perfil é de um cartão. */
  readonly doCartao = computed(() => this.destino().tipo === 'cartao');
  /**
   * O alerta "Parece um extrato da conta do Nubank" só quando o cabeçalho é do Nubank e o formato
   * carregado é o modelo pronto: um perfil salvo de outro banco não vira "Nubank (conta)". No cartão, "Parece a fatura do
   * cartão Nubank", com o modelo "Nubank (fatura)".
   */
  readonly sugeriuNubank = computed(() => this.amostraInicial().modelo === 'NUBANK_CONTA' && ehNubank(this.amostraInicial().formato));
  readonly sugeriuNubankFatura = computed(() => this.amostraInicial().modelo === 'NUBANK_FATURA'
    && ehNubankFatura(this.amostraInicial().formato));
  /** "1 · Data", "2 · Valor"...: o número da coluna e o nome do cabeçalho, quando há. */
  readonly colunas = computed(() => {
    const linhas = this.amostra()?.linhas ?? [];
    const cabecalho = linhas[0] ?? [];
    const quantidade = Math.max(1, ...linhas.map((linha) => linha.length));
    return Array.from({ length: quantidade }, (_, i) => ({ numero: i + 1, rotulo: cabecalho[i] ? `${i + 1} · ${cabecalho[i]}` : `${i + 1}` }));
  });
  readonly dataCurta = dataCurta;

  ngOnInit(): void {
    const inicial = this.amostraInicial();
    this.amostra.set(inicial);
    this.aplicar(inicial.formato, this.doCartao() ? (ehNubankFatura(inicial.formato) ? 'NUBANK_FATURA' : 'PERSONALIZADO')
      : ehNubank(inicial.formato) ? 'NUBANK_CONTA' : 'PERSONALIZADO');
    this.formulario.controls.modelo.valueChanges.pipe(takeUntilDestroyed(this.destruicao)).subscribe((modelo) => {
      if (modelo === 'NUBANK_CONTA') {
        this.aplicar(NUBANK_CONTA, 'NUBANK_CONTA');
      } else if (modelo === 'NUBANK_FATURA') {
        this.aplicar(NUBANK_FATURA, 'NUBANK_FATURA');
      }
    });
    this.formulario.controls.modo.valueChanges.pipe(takeUntilDestroyed(this.destruicao)).subscribe((modo) => {
      this.modo.set(modo);
      const seguinte = this.formulario.controls.colunaValor.value + 1;
      this.formulario.controls.colunaSaidas.setValue(modo === 'DUAS' && seguinte <= this.colunas().length ? seguinte : null);
    });
    // A primeira linha lida vem da API, com o formato da tela; o front não lê o valor. Um 400 (por
    // exemplo, 25 linhas de cabeçalho) vira o erro do campo e não encerra o fluxo: a próxima
    // mudança pede a amostra de novo.
    this.formulario.valueChanges.pipe(
      debounceTime(300),
      switchMap(() => {
        this.errosDosCampos.set({});
        return this.servico.amostra(this.arquivo(), this.formato(), this.doCartao()).pipe(catchError((erro: unknown) => {
          this.mostrarErros(erro);
          return EMPTY;
        }));
      }),
      takeUntilDestroyed(this.destruicao),
    ).subscribe((amostra) => this.amostra.set(amostra));
  }

  erroDe(campo: CampoComErro): string | null {
    return this.errosDosCampos()[campo] ?? null;
  }

  /**
   * O formato que a tela monta (sem coluna de saídas no modo de uma coluna). No cartão, "compras vêm positivas" só vale na
   * coluna única; na conta, ele não vai.
   */
  formato(): FormatoCsv {
    const valores = this.formulario.getRawValue();
    const formato: FormatoCsv = {
      separador: valores.separador,
      linhasDeCabecalho: Number(valores.linhasDeCabecalho),
      colunaData: Number(valores.colunaData),
      formatoData: valores.formatoData,
      colunaDescricao: Number(valores.colunaDescricao),
      colunaValor: Number(valores.colunaValor),
      colunaSaidas: valores.modo === 'DUAS' && valores.colunaSaidas !== null ? Number(valores.colunaSaidas) : null,
      colunaIdentificador: valores.colunaIdentificador === null ? null : Number(valores.colunaIdentificador),
      separadorDecimal: valores.separadorDecimal,
    };
    return this.doCartao() ? { ...formato, comprasPositivas: valores.modo === 'UMA' && valores.comprasPositivas } : formato;
  }

  salvar(): void {
    if (this.enviando() || this.lendo()) {
      return;
    }
    this.erroGeral.set(null);
    const valores = this.formulario.getRawValue();
    if (valores.modo === 'DUAS' && valores.colunaSaidas === null) {
      this.errosDosCampos.set({ colunaSaidas: this.doCartao() ? 'Escolha a coluna das compras.' : 'Escolha a coluna das saídas.' });
      return;
    }
    if (valores.modo === 'DUAS' && Number(valores.colunaSaidas) === Number(valores.colunaValor)) {
      this.errosDosCampos.set({ colunaSaidas: 'Use colunas diferentes para entradas e saídas.' });
      return;
    }
    this.errosDosCampos.set({});
    this.enviando.set(true);
    this.servico.salvarPerfil(this.destino(), this.formato()).subscribe({
      next: () => {
        this.enviando.set(false);
        this.salvo.emit();
      },
      error: (erro: unknown) => {
        this.enviando.set(false);
        this.mostrarErros(erro);
      },
    });
  }

  /** O 400 vai para os campos (o que não é de um campo, para o alerta); o resto, para o alerta. */
  private mostrarErros(erro: unknown): void {
    if (statusDoErro(erro) !== 400) {
      this.erroGeral.set(detalheDoErro(erro, 'Não foi possível salvar o perfil. Tente novamente.'));
      return;
    }
    const campos = camposDoErro(erro);
    const doCampo: Partial<Record<CampoComErro, string>> = {};
    const outros: string[] = [];
    for (const [campo, mensagem] of Object.entries(campos)) {
      if ((CAMPOS_COM_ERRO as readonly string[]).includes(campo)) {
        doCampo[campo as CampoComErro] = mensagem;
      } else {
        outros.push(mensagem);
      }
    }
    this.errosDosCampos.set(doCampo);
    this.erroGeral.set(outros[0] ?? null);
  }

  private aplicar(formato: FormatoCsv, modelo: Modelo): void {
    const modo: Modo = formato.colunaSaidas === null ? 'UMA' : 'DUAS';
    this.modo.set(modo);
    const { comprasPositivas, ...colunas } = formato;
    this.formulario.setValue({ ...colunas, modelo, modo, comprasPositivas: comprasPositivas === true }, { emitEvent: false });
  }
}
