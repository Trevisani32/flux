package com.flux.suporte;

import com.flux.compartilhado.limite.LimiteDeTentativasEmMemoria;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InfraestruturaDeTeste.class)
public abstract class IntegracaoBase {

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    protected RelogioDeTeste relogioDeTeste;

    @Autowired
    protected UsuariosDeTeste usuariosDeTeste;

    @Autowired
    protected CaixaDeEntrada caixaDeEntrada;

    @Autowired
    protected LimiteDeTentativasEmMemoria limiteDeTentativas;

    @Autowired
    private JsonMapper jsonMapper;

    protected ApiDeTeste api;

    @BeforeEach
    void prepararApi() {
        api = new ApiDeTeste(mvc, jsonMapper);
    }

    @AfterEach
    void reiniciaRelogioEContadores() {
        caixaDeEntrada.aguardarEnvios();
        relogioDeTeste.reiniciar();
        limiteDeTentativas.limpar();
    }
}
