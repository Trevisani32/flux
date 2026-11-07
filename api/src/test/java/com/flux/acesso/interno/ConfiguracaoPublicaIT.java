package com.flux.acesso.interno;

import com.flux.suporte.IntegracaoBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfiguracaoPublicaIT extends IntegracaoBase {

    @Test
    void expoeConfiguracaoNecessariaParaTelaPublica() {
        assertThat(mvc.get().uri("/api/publico/configuracao")).hasStatusOk()
                .bodyJson().extractingPath("cadastroPublico").isEqualTo(true);
        assertThat(mvc.get().uri("/api/publico/configuracao")).bodyJson().extractingPath("controlador.nome")
                .isEqualTo("Responsável fictício");
        assertThat(mvc.get().uri("/api/publico/configuracao")).bodyJson().extractingPath("controlador.email")
                .isEqualTo("privacidade@teste.local");
        assertThat(mvc.get().uri("/api/publico/configuracao")).bodyJson().extractingPath("versaoDosTermos")
                .isEqualTo("2026-11-06");
    }
}
