import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ConfiguracaoService } from '../../../core/configuracao/configuracao.service';
import { LayoutDocumento, OutroDocumento, SecaoDoDocumento } from '../layout-documento/layout-documento';

/** Texto aprovado no mockup da Fatia 02 (quadro "Termos de uso"). Mudar o texto = mudar a versão. */
@Component({
  selector: 'app-termos-de-uso',
  imports: [RouterLink, LayoutDocumento],
  templateUrl: './termos-de-uso.html',
})
export class TermosDeUso {
  private readonly configuracao = inject(ConfiguracaoService).configuracao;

  readonly responsavel = computed(() => this.configuracao().controlador.nome || '—');
  readonly contato = computed(() => this.configuracao().controlador.email);
  readonly outro: OutroDocumento = { rota: '/privacidade', rotulo: 'Política de privacidade →' };
  readonly secoes: readonly SecaoDoDocumento[] = [
    { id: 't1', rotulo: '1. O que é o Flux' },
    { id: 't2', rotulo: '2. Quem pode usar' },
    { id: 't3', rotulo: '3. Sua conta e sua senha' },
    { id: 't4', rotulo: '4. Uso permitido' },
    { id: 't5', rotulo: '5. Seus dados' },
    { id: 't6', rotulo: '6. Sem garantias' },
    { id: 't7', rotulo: '7. Encerramento' },
    { id: 't8', rotulo: '8. Mudanças nos termos' },
    { id: 't9', rotulo: '9. Lei aplicável' },
    { id: 't10', rotulo: '10. Contato' },
  ];
}
