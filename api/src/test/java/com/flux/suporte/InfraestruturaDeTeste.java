package com.flux.suporte;

import java.time.Clock;

import com.flux.acesso.interno.TokenAcessoService;
import com.flux.acesso.interno.UsuarioService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class InfraestruturaDeTeste {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17");
    }

    @Bean
    @Primary
    RelogioDeTeste relogioDeTeste() {
        return new RelogioDeTeste();
    }

    @Bean
    UsuariosDeTeste usuariosDeTeste(UsuarioService usuarioService, TokenAcessoService tokenAcessoService) {
        return new UsuariosDeTeste(usuarioService, tokenAcessoService);
    }
}
