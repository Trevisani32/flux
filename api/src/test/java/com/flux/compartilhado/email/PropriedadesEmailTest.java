package com.flux.compartilhado.email;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class PropriedadesEmailTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void rejeitaRemetenteVazio() {
        contextRunner.withPropertyValues("app.email.remetente=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining(
                    "app.email.remetente (APP_EMAIL_REMETENTE) precisa ser um endereço de e-mail");
        });
    }

    @Test
    void rejeitaRemetenteSemArroba() {
        contextRunner.withPropertyValues("app.email.remetente=nao-e-email").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining(
                    "app.email.remetente (APP_EMAIL_REMETENTE) precisa ser um endereço de e-mail");
        });
    }

    @Test
    void aceitaRemetenteValido() {
        contextRunner.withPropertyValues("app.email.remetente=nao-responda@teste.local").run(context ->
                assertThat(context).hasNotFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PropriedadesEmail.class)
    static class Config {
    }
}
