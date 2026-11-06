package com.flux.suporte;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

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

    @AfterEach
    void reiniciaRelogio() {
        relogioDeTeste.reiniciar();
    }
}
