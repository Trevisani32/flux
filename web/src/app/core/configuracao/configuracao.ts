export interface Controlador {
  readonly nome: string;
  readonly email: string;
}

/** Resposta de GET /api/publico/configuracao. */
export interface ConfiguracaoPublica {
  readonly cadastroPublico: boolean;
  readonly controlador: Controlador;
  readonly versaoDosTermos: string;
}

/** Usada antes da primeira resposta e quando a API não responde: cadastro desligado, sem contato. */
export const CONFIGURACAO_PADRAO: ConfiguracaoPublica = {
  cadastroPublico: false,
  controlador: { nome: '', email: '' },
  versaoDosTermos: '',
};
