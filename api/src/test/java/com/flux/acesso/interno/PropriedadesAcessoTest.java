package com.flux.acesso.interno;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class PropriedadesAcessoTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withPropertyValues(
                    "app.jwt.validade-acesso=15m",
                    "app.renovacao.validade=30d",
                    "app.renovacao.cookie-seguro=true");

    @Test
    void rejeitaSegredoComTrintaEUmBytes() {
        contextRunner.withPropertyValues("app.jwt.segredo=" + "a".repeat(31)).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("32 bytes");
        });
    }

    @Test
    void aceitaSegredoComTrintaEDoisBytes() {
        contextRunner.withPropertyValues("app.jwt.segredo=" + "a".repeat(32)).run(context ->
                assertThat(context).hasNotFailed());
    }

    @Test
    void contaBytesEmUtf8() {
        contextRunner.withPropertyValues("app.jwt.segredo=" + "ç".repeat(16)).run(context ->
                assertThat(context).hasNotFailed());
    }

    @Test
    void rejeitaSegredoVazio() {
        contextRunner.withPropertyValues("app.jwt.segredo=").run(context ->
                assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PropriedadesAcesso.class)
    static class Config {
    }
}
