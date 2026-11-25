import { DOCUMENT } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';

import { AGORA } from '../../../core/tempo/agora';
import { Alerta } from '../../../shared/alerta/alerta';
import { dataIsoNoFuso } from '../../../shared/datas/data-curta';
import { Icone } from '../../../shared/icone/icone';
import { MENSAGEM_MUITAS_TENTATIVAS, detalheDoErro, statusDoErro } from '../../auth/erros-http';
import {
  ExportacaoService,
  FormatoDaExportacao,
  nomeDoArquivo,
  salvarArquivo,
} from '../exportacao.service';

export interface DadosDaExportacao {
  readonly formato: FormatoDaExportacao;
}

@Component({
  selector: 'app-exportar-dados-dialogo',
  imports: [MatButtonModule, MatDialogModule, ReactiveFormsModule, Alerta, Icone],
  templateUrl: './exportar-dados-dialogo.html',
  styleUrl: './exportar-dados-dialogo.scss',
})
export class ExportarDadosDialogo {
  private readonly servico = inject(ExportacaoService);
  private readonly referencia = inject<MatDialogRef<ExportarDadosDialogo, string>>(MatDialogRef);
  private readonly documento = inject(DOCUMENT);

  readonly dados = inject<DadosDaExportacao>(MAT_DIALOG_DATA);
  readonly nome = nomeDoArquivo(this.dados.formato, dataIsoNoFuso(inject(AGORA)()));
  readonly senha = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  readonly formulario = new FormGroup({ senha: this.senha });
  readonly senhaVisivel = signal(false);
  readonly baixando = signal(false);
  readonly erroSenha = signal<string | null>(null);
  readonly erro = signal<{ readonly icone: 'alerta' | 'relogio'; readonly texto: string } | null>(null);

  baixar(): void {
    if (this.senha.invalid || this.baixando()) {
      return;
    }
    this.baixando.set(true);
    this.erroSenha.set(null);
    this.erro.set(null);
    this.servico.baixar(this.dados.formato, this.senha.value, this.nome).subscribe({
      next: (arquivo) => {
        salvarArquivo(this.documento, arquivo);
        this.referencia.close(arquivo.nome);
      },
      error: (erro: unknown) => {
        const status = statusDoErro(erro);
        if (status === 422) {
          this.erroSenha.set(detalheDoErro(erro, 'Senha atual incorreta.'));
        } else {
          this.erro.set(status === 429
            ? { icone: 'relogio', texto: MENSAGEM_MUITAS_TENTATIVAS }
            : { icone: 'alerta', texto: 'Não foi possível baixar o arquivo. Tente novamente.' });
        }
        this.baixando.set(false);
      },
    });
  }

  alternarSenha(): void {
    this.senhaVisivel.update((visivel) => !visivel);
  }
}
