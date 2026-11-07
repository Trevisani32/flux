import { Component, computed, inject } from '@angular/core';

import { ConfiguracaoService } from '../../../core/configuracao/configuracao.service';
import { LayoutDocumento, OutroDocumento, SecaoDoDocumento } from '../layout-documento/layout-documento';

/** Texto aprovado no mockup da Fatia 02 (quadro "Política de privacidade"). Mudar o texto = mudar a versão. */
@Component({
  selector: 'app-privacidade',
  imports: [LayoutDocumento],
  templateUrl: './privacidade.html',
})
export class Privacidade {
  private readonly configuracao = inject(ConfiguracaoService).configuracao;

  readonly responsavel = computed(() => this.configuracao().controlador.nome || '—');
  readonly contato = computed(() => this.configuracao().controlador.email);
  readonly outro: OutroDocumento = { rota: '/termos-de-uso', rotulo: '← Termos de uso' };
  readonly secoes: readonly SecaoDoDocumento[] = [
    { id: 'p1', rotulo: '1. Quem cuida dos dados' },
    { id: 'p2', rotulo: '2. Que dados coletamos' },
    { id: 'p3', rotulo: '3. Para que usamos' },
    { id: 'p4', rotulo: '4. Com quem compartilhamos' },
    { id: 'p5', rotulo: '5. Cookies' },
    { id: 'p6', rotulo: '6. Por quanto tempo' },
    { id: 'p7', rotulo: '7. Seus direitos' },
    { id: 'p8', rotulo: '8. Segurança' },
    { id: 'p9', rotulo: '9. Menores de idade' },
    { id: 'p10', rotulo: '10. Mudanças' },
  ];
}
