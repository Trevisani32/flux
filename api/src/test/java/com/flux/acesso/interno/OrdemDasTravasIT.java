package com.flux.acesso.interno;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.flux.suporte.CaixaDeEntrada;
import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirmar ou redefinir enquanto chega um pedido de link novo do mesmo usuário. O pedido
 * (TokensDeEmail) trava o usuário e depois os links; confirmar e redefinir travam na mesma
 * ordem. A transação do teste faz o papel do pedido: trava o usuário, espera a requisição
 * ficar presa e então invalida os links. Com a ordem invertida, o Postgres acusa deadlock e a
 * requisição dá 500.
 */
class OrdemDasTravasIT extends IntegracaoBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transacao;

    @Test
    void confirmarEnquantoChegaUmLinkNovoNaoDaDeadlock() throws Exception {
        String email = "travas-" + UUID.randomUUID() + "@teste.local";
        assertThat(mvc.post().uri("/api/auth/cadastrar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Ana\",\"email\":\"" + email
                        + "\",\"senha\":\"senha-de-teste-123\",\"aceiteDosTermos\":true}")).hasStatus(202);
        String token = CaixaDeEntrada.token(
                caixaDeEntrada.mensagensPara(email, "Confirme seu e-mail no Flux").getFirst());
        Long usuarioId = jdbc.queryForObject("select id from usuarios where email = ?", Long.class, email);

        CompletableFuture<Void> pedido = pedidoDeLinkNovo(usuarioId, "CONFIRMACAO");
        MvcTestResult resposta = mvc.post().uri("/api/auth/confirmar-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}").exchange();
        pedido.get(15, TimeUnit.SECONDS);

        // O pedido invalidou o link primeiro; a confirmação, que esperava a trava do usuário, vê isso.
        assertThat(resposta).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("LINK_INVALIDO");
    }

    @Test
    void redefinirEnquantoChegaUmLinkNovoNaoDaDeadlock() throws Exception {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        assertThat(mvc.post().uri("/api/auth/esqueci-a-senha").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + usuario.email() + "\"}")).hasStatus(202);
        String token = CaixaDeEntrada.token(
                caixaDeEntrada.mensagensPara(usuario.email(), "Redefinir sua senha do Flux").getFirst());

        CompletableFuture<Void> pedido = pedidoDeLinkNovo(usuario.id(), "REDEFINICAO");
        MvcTestResult resposta = mvc.post().uri("/api/auth/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"novaSenha\":\"senha-nova-de-teste-456\"}").exchange();
        pedido.get(15, TimeUnit.SECONDS);

        assertThat(resposta).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("LINK_INVALIDO");
    }

    /** Trava o usuário, espera outra sessão ficar presa numa trava e invalida os links pendentes. */
    private CompletableFuture<Void> pedidoDeLinkNovo(Long usuarioId, String finalidade) throws InterruptedException {
        CountDownLatch usuarioTravado = new CountDownLatch(1);
        CompletableFuture<Void> pedido = CompletableFuture.runAsync(() -> transacao.executeWithoutResult(status -> {
            jdbc.queryForObject("select id from usuarios where id = ? for update", Long.class, usuarioId);
            usuarioTravado.countDown();
            Awaitility.await().atMost(Duration.ofSeconds(10)).pollInSameThread().pollInterval(Duration.ofMillis(20))
                    .until(() -> {
                        // Dentro de uma transação, o pg_stat_activity é lido uma vez só sem isto.
                        jdbc.execute("select pg_stat_clear_snapshot()");
                        return jdbc.queryForObject("select count(*) from pg_stat_activity where pid <> pg_backend_pid() "
                                + "and datname = current_database() and wait_event_type = 'Lock'", Integer.class) > 0;
                    });
            jdbc.update("update tokens_email set invalidado_em = now() where usuario_id = ? and finalidade = ? "
                    + "and usado_em is null and invalidado_em is null", usuarioId, finalidade);
        }));
        assertThat(usuarioTravado.await(10, TimeUnit.SECONDS)).isTrue();
        return pedido;
    }
}
