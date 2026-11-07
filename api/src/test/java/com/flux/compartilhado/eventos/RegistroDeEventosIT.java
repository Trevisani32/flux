package com.flux.compartilhado.eventos;

import com.flux.compartilhado.email.EnvioDeEmail;
import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

class RegistroDeEventosIT extends IntegracaoBase {

    @MockitoSpyBean
    private EnvioDeEmail envioDeEmail;

    @Autowired
    private FailedEventPublications falhas;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    @Qualifier("applicationTaskExecutor")
    private ThreadPoolTaskExecutor executor;

    @Test
    void falhaNoEnvioDeixaAPublicacaoPendenteEOReenvioEntrega() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        doThrow(new MailSendException("SMTP fora do ar (simulado)")).doCallRealMethod()
                .when(envioDeEmail).enviar(any());

        assertThat(mvc.post().uri("/api/auth/esqueci-a-senha").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + usuario.email() + "\"}")).hasStatus(202);
        caixaDeEntrada.aguardarEnvios();

        // Só a publicação deste teste: outras classes podem ter deixado linhas na tabela.
        Long tokenId = jdbc.queryForObject("select id from tokens_email where usuario_id = ?", Long.class,
                usuario.id());
        String evento = "{\"tokenId\":" + tokenId + "}";
        assertThat(jdbc.queryForObject("select status from event_publication where serialized_event = ?",
                String.class, evento)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select count(*) from tokens_email where usuario_id = ? and token_hash is null",
                Integer.class, usuario.id())).isEqualTo(1);
        assertThat(caixaDeEntrada.mensagensPara(usuario.email())).isEmpty();

        falhas.resubmit(ResubmissionOptions.defaults());

        assertThat(caixaDeEntrada.mensagensPara(usuario.email(), "Redefinir sua senha do Flux")).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from event_publication where serialized_event = ?",
                Integer.class, evento)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from tokens_email where usuario_id = ? and token_hash is not null",
                Integer.class, usuario.id())).isEqualTo(1);
    }

    @Test
    void executorDosEnviosTemDuasThreads() {
        // Cada envio segura uma conexão do banco durante o SMTP; o Hikari tem 10.
        assertThat(executor.getCorePoolSize()).isEqualTo(2);
    }
}
