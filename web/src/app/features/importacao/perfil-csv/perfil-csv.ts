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
import { ehNubank, NUBANK_CONTA } from '../apresentacao';
import { Amostra, FormatoCsv, ImportacaoService } from '../importacao.service';

type Modo = 'UMA' | 'DUAS';

/** Os campos que mostram o próprio erro; o resto (como "colunas") vai para o alerta do formulário. */
const CAMPOS_COM_ERRO = ['linhasDeCabecalho', 'colunaData', 'colunaDescricao', 'colunaValor', 'colunaSaidas'] as const;
type CampoComErro = (typeof CAMPOS_COM_ERRO)[number];

/**
 * "Como ler este CSV" (decisão 12): a amostra do arquivo, os campos do perfil e a primeira linha
 * lida pela API a cada mudança. "Salvar perfil e revisar" grava o perfil da conta. Os erros dos
 * campos aparecem no campo (mockup, "Campos do perfil"), da tela ou do 400 da API.
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
  readonly contaId = input.required<number>();
  readonly contaNome = input.required<string>();
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
    modelo: new FormControl<'NUBANK_CONTA' | 'PERSONALIZADO'>('PERSONALIZADO', { nonNullable: true }),
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
  });
  readonly modo = signal<Modo>('UMA');
  /**
   * O alerta "Parece um extrato da conta do Nubank" só quando o cabeçalho é do Nubank e o formato
   * carregado é o modelo pronto: um perfil salvo de outro banco não vira "Nubank (conta)".
   */
  readonly sugeriuNubank = computed(() => this.amostraInicial().modelo === 'NUBANK_CONTA' && ehNubank(this.amostraInicial().formato));
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
    this.aplicar(inicial.formato, ehNubank(inicial.formato) ? 'NUBANK_CONTA' : 'PERSONALIZADO');
    this.formulario.controls.modelo.valueChanges.pipe(takeUntilDestroyed(this.destruicao)).subscribe((modelo) => {
      if (modelo === 'NUBANK_CONTA') {
        this.aplicar(NUBANK_CONTA, 'NUBANK_CONTA');
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
        return this.servico.amostra(this.arquivo(), this.formato()).pipe(catchError((erro: unknown) => {
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

  /** O formato que a tela monta (sem coluna de saídas no modo de uma coluna). */
  formato(): FormatoCsv {
    const valores = this.formulario.getRawValue();
    return {
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
  }

  salvar(): void {
    if (this.enviando() || this.lendo()) {
      return;
    }
    this.erroGeral.set(null);
    const valores = this.formulario.getRawValue();
    if (valores.modo === 'DUAS' && valores.colunaSaidas === null) {
      this.errosDosCampos.set({ colunaSaidas: 'Escolha a coluna das saídas.' });
      return;
    }
    if (valores.modo === 'DUAS' && Number(valores.colunaSaidas) === Number(valores.colunaValor)) {
      this.errosDosCampos.set({ colunaSaidas: 'Use colunas diferentes para entradas e saídas.' });
      return;
    }
    this.errosDosCampos.set({});
    this.enviando.set(true);
    this.servico.salvarPerfil(this.contaId(), this.formato()).subscribe({
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

  private aplicar(formato: FormatoCsv, modelo: 'NUBANK_CONTA' | 'PERSONALIZADO'): void {
    const modo: Modo = formato.colunaSaidas === null ? 'UMA' : 'DUAS';
    this.modo.set(modo);
    this.formulario.setValue({ ...formato, modelo, modo }, { emitEvent: false });
  }
}
