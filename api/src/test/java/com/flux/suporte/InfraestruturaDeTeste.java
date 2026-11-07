package com.flux.suporte;

import com.flux.acesso.interno.TokenAcessoService;
import com.flux.acesso.interno.UsuarioService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@TestConfiguration(proxyBeanMethods = false)
public class InfraestruturaDeTeste {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17");
    }

    @Bean
    ContainerMailpit mailpit() {
        return new ContainerMailpit();
    }

    @Bean
    DynamicPropertyRegistrar propriedadesDoMailpit(ContainerMailpit mailpit) {
        return registro -> {
            registro.add("spring.mail.host", mailpit::getHost);
            registro.add("spring.mail.port", mailpit::portaSmtp);
        };
    }

    @Bean
    CaixaDeEntrada caixaDeEntrada(ContainerMailpit mailpit, JsonMapper jsonMapper,
                                  @Qualifier("applicationTaskExecutor") ThreadPoolTaskExecutor executor) {
        return new CaixaDeEntrada(mailpit, jsonMapper, executor);
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
