package com.flux.acesso.interno;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class PropriedadesContaTest {

    private static final String MENSAGEM_URL = "app.conta.url-base (APP_URL_BASE) precisa ser uma URL http ou https, sem barra no fim";
    private static final String MENSAGEM_CONTROLADOR = "Com APP_CADASTRO_PUBLICO=true, defina APP_CONTROLADOR_NOME e APP_CONTROLADOR_EMAIL";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withPropertyValues("app.conta.url-base=http://localhost:4200");

    @Test
    void rejeitaUrlBaseVazia() {
        contextRunner.withPropertyValues("app.conta.url-base=").run(context ->
                assertThat(context).hasFailed().getFailure().hasStackTraceContaining(MENSAGEM_URL));
    }

    @Test
    void rejeitaUrlBaseComBarraNoFim() {
        contextRunner.withPropertyValues("app.conta.url-base=http://localhost:4200/").run(context ->
                assertThat(context).hasFailed().getFailure().hasStackTraceContaining(MENSAGEM_URL));
    }

    @Test
    void rejeitaUrlBaseSemEsquema() {
        contextRunner.withPropertyValues("app.conta.url-base=localhost:4200").run(context ->
                assertThat(context).hasFailed().getFailure().hasStackTraceContaining(MENSAGEM_URL));
    }

    @Test
    void exigeNomeDoControladorQuandoCadastroPublicoEstaLigado() {
        contextRunner.withPropertyValues("app.conta.cadastro-publico=true", "app.conta.controlador.nome=",
                "app.conta.controlador.email=privacidade@teste.local").run(context ->
                assertThat(context).hasFailed().getFailure().hasStackTraceContaining(MENSAGEM_CONTROLADOR));
    }

    @Test
    void exigeEmailDoControladorQuandoCadastroPublicoEstaLigado() {
        contextRunner.withPropertyValues("app.conta.cadastro-publico=true", "app.conta.controlador.nome=Responsável",
                "app.conta.controlador.email=").run(context ->
                assertThat(context).hasFailed().getFailure().hasStackTraceContaining(MENSAGEM_CONTROLADOR));
    }

    @Test
    void aceitaCadastroPublicoDesligadoSemContato() {
        contextRunner.withPropertyValues("app.conta.cadastro-publico=false").run(context ->
                assertThat(context).hasNotFailed());
    }

    @Test
    void usaLimitePorIpComoPadrao() {
        contextRunner.withPropertyValues("app.conta.cadastro-publico=false").run(context ->
                assertThat(context.getBean(PropriedadesConta.class).limitePorIp()).isTrue());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PropriedadesConta.class)
    static class Config {
    }
}
