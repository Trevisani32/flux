package com.flux.acesso.interno;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

/** Com o cadastro desligado, o endpoint não existe: 404 de rota inexistente, antes da validação. */
@WebMvcTest(controllers = {CadastroController.class, ConfiguracaoPublicaController.class},
        properties = "app.conta.cadastro-publico=false")
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(CadastroDesligadoTest.Config.class)
class CadastroDesligadoTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void cadastroDesligadoRespondeComoRotaInexistente() {
        assertThat(mvc.post().uri("/api/auth/cadastrar").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .hasStatus(404)
                .bodyJson().extractingPath("detail").isEqualTo("O recurso solicitado não existe.");
        assertThat(mvc.get().uri("/api/publico/configuracao"))
                .hasStatusOk()
                .bodyJson().extractingPath("cadastroPublico").isEqualTo(false);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        PropriedadesConta propriedadesConta() {
            return new PropriedadesConta("http://localhost:4200", false, null, true);
        }
    }
}
