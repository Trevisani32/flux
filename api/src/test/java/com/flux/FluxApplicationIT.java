package com.flux;

import com.flux.suporte.IntegracaoBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FluxApplicationIT extends IntegracaoBase {

    @Test
    void healthEstaDisponivel() {
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk().bodyJson().extractingPath("status")
                .isEqualTo("UP");
    }

    @Test
    void documentacaoEstaDesligadaForaDoPerfilDev() {
        assertThat(mvc.get().uri("/v3/api-docs")).hasStatus(404);
        assertThat(mvc.get().uri("/swagger-ui.html")).hasStatus(404);
    }

    @Test
    void rotaDaApiSemTokenRetornaNaoAutenticado() {
        assertThat(mvc.get().uri("/api/rota-inexistente")).hasStatus(401);
    }
}
