package com.flux.acesso.interno;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.flux.compartilhado.eventos.LimpezaDeEventos;
import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class LimpezaDiariaIT extends IntegracaoBase {

    @Autowired private LimpezaDiaria limpezaDiaria;
    @Autowired private LimpezaDeEventos limpezaDeEventos;
    @Autowired private JdbcTemplate jdbc;

    private UUID eventoAntigo;
    private UUID eventoNovo;
    private Long tokenEmailVencido;
    private Long tokenEmailPendente;
    private Long tokenRenovacaoValido;

    @AfterEach
    void apagarEventosInseridos() {
        if (eventoAntigo != null) jdbc.update("delete from event_publication where id = ?", eventoAntigo);
        if (eventoNovo != null) jdbc.update("delete from event_publication where id = ?", eventoNovo);
        if (tokenEmailVencido != null) jdbc.update("delete from tokens_email where id = ?", tokenEmailVencido);
        if (tokenEmailPendente != null) jdbc.update("delete from tokens_email where id = ?", tokenEmailPendente);
        if (tokenRenovacaoValido != null) jdbc.update("delete from tokens_renovacao where id = ?", tokenRenovacaoValido);
    }

    @Test
    void apagaCadastroNaoConfirmadoDepoisDeSeteDiasEMantemConfirmado() {
        String email = "limpeza-" + UUID.randomUUID() + "@teste.local";
        assertThat(mvc.post().uri("/api/auth/cadastrar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Ana\",\"email\":\"" + email + "\",\"senha\":\"senha-de-teste-123\",\"aceiteDosTermos\":true}"))
                .hasStatus(202);
        UsuarioDeTeste confirmado = usuariosDeTeste.criar();
        relogioDeTeste.avancar(Duration.ofDays(7).plusMinutes(1));

        limpezaDiaria.executar();

        assertThat(jdbc.queryForObject("select count(*) from usuarios where email = ?", Integer.class, email)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from usuarios where id = ?", Integer.class, confirmado.id())).isEqualTo(1);
    }

    @Test
    void mantemCadastroNaoConfirmadoComMenosDeSeteDias() {
        String email = "recente-" + UUID.randomUUID() + "@teste.local";
        assertThat(mvc.post().uri("/api/auth/cadastrar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Ana\",\"email\":\"" + email + "\",\"senha\":\"senha-de-teste-123\",\"aceiteDosTermos\":true}"))
                .hasStatus(202);
        relogioDeTeste.avancar(Duration.ofDays(6));

        limpezaDiaria.executar();

        assertThat(jdbc.queryForObject("select count(*) from usuarios where email = ?", Integer.class, email)).isEqualTo(1);
    }

    @Test
    void apagaLinkDeEmailVencidoEMantemPendente() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        Instant agora = Instant.now(relogioDeTeste);
        tokenEmailVencido = jdbc.queryForObject("insert into tokens_email (usuario_id, finalidade, expira_em, criado_em) values (?, ?, ?, ?) returning id", Long.class,
                usuario.id(), "REDEFINICAO", Timestamp.from(agora.minus(Duration.ofHours(1))), Timestamp.from(agora.minus(Duration.ofHours(2))));
        tokenEmailPendente = jdbc.queryForObject("insert into tokens_email (usuario_id, finalidade, expira_em, criado_em) values (?, ?, ?, ?) returning id", Long.class,
                usuario.id(), "CONFIRMACAO", Timestamp.from(agora.plus(Duration.ofHours(1))), Timestamp.from(agora));

        limpezaDiaria.executar();

        assertThat(jdbc.queryForObject("select count(*) from tokens_email where id = ?", Integer.class, tokenEmailVencido)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from tokens_email where id = ?", Integer.class, tokenEmailPendente)).isEqualTo(1);
    }

    @Test
    void apagaSessaoVencidaEDesfazReferenciaDaSessaoValida() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        Instant agora = Instant.now(relogioDeTeste);
        Long vencido = jdbc.queryForObject("insert into tokens_renovacao (usuario_id, token_hash, expira_em) values (?, ?, ?) returning id", Long.class,
                usuario.id(), Tokens.hash(Tokens.novoValor()), Timestamp.from(agora.minus(Duration.ofHours(1))));
        tokenRenovacaoValido = jdbc.queryForObject("insert into tokens_renovacao (usuario_id, token_hash, expira_em, substituido_por) values (?, ?, ?, ?) returning id", Long.class,
                usuario.id(), Tokens.hash(Tokens.novoValor()), Timestamp.from(agora.plus(Duration.ofDays(1))), vencido);

        limpezaDiaria.executar();

        assertThat(jdbc.queryForObject("select count(*) from tokens_renovacao where id = ?", Integer.class, vencido)).isZero();
        assertThat(jdbc.queryForObject("select substituido_por from tokens_renovacao where id = ?", Long.class, tokenRenovacaoValido)).isNull();
    }

    @Test
    void apagaSomenteEventosComMaisDeSeteDias() {
        Instant agora = Instant.now(relogioDeTeste);
        eventoAntigo = UUID.randomUUID(); eventoNovo = UUID.randomUUID();
        inserirEvento(eventoAntigo, agora.minus(Duration.ofDays(8)));
        inserirEvento(eventoNovo, agora);

        limpezaDeEventos.apagarAntigas();

        assertThat(jdbc.queryForObject("select count(*) from event_publication where id = ?", Integer.class, eventoAntigo)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from event_publication where id = ?", Integer.class, eventoNovo)).isEqualTo(1);
    }

    private void inserirEvento(UUID id, Instant data) {
        jdbc.update("insert into event_publication (id, listener_id, event_type, serialized_event, publication_date) values (?, ?, ?, ?, ?)",
                id, "teste", "teste", "{}", Timestamp.from(data));
    }
}
