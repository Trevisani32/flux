import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { CONFIGURACAO_PADRAO, ConfiguracaoPublica } from './configuracao';

@Injectable({ providedIn: 'root' })
export class ConfiguracaoService {
  private readonly http = inject(HttpClient);
  private readonly valor = signal<ConfiguracaoPublica>(CONFIGURACAO_PADRAO);
  private carregada = false;
  private emAndamento: Promise<ConfiguracaoPublica> | null = null;

  readonly configuracao = this.valor.asReadonly();
  readonly cadastroPublico = computed(() => this.valor().cadastroPublico);

  /** Busca a configuração pública. Nunca rejeita: se a API falhar, fica o padrão. */
  carregar(): Promise<ConfiguracaoPublica> {
    if (this.emAndamento !== null) {
      return this.emAndamento;
    }
    this.emAndamento = firstValueFrom(this.http.get<ConfiguracaoPublica>('/api/publico/configuracao'))
      .then((configuracao) => {
        this.valor.set(configuracao);
        this.carregada = true;
        return configuracao;
      })
      .catch(() => this.valor())
      .finally(() => {
        this.emAndamento = null;
      });
    return this.emAndamento;
  }

  /** A configuração já carregada, ou uma nova tentativa se a anterior falhou. */
  garantir(): Promise<ConfiguracaoPublica> {
    return this.carregada ? Promise.resolve(this.valor()) : this.carregar();
  }
}
