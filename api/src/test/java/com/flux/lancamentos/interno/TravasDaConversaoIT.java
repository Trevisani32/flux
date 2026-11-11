package com.flux.lancamentos.interno;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decisão 36 da 04c (ADR 0012): converter e desfazer pegam a trava de contas e só depois conferem as
 * contas dos lados. Outra transação segura a trava, arquiva uma conta e termina; o pedido preso nela vê
 * a conta arquivada.
 */
class TravasDaConversaoIT extends ConversaoBase {

    @Autowired
    private TransactionTemplate transacao;

    @Test
    void converterEsperaATravaDeContasEVeAContaDoParArquivada() throws Exception {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long conjunta = novaConta(usuario, "Conta conjunta");
        long ted = importado(usuario, horizonte, "2026-11-02", "-1000.00", "TED");
        long recebida = importado(usuario, conjunta, "2026-11-03", "1000.00", "RECEBIDA");

        CompletableFuture<Void> arquivamento = segurarATravaDeContasE(usuario.id(),
                "update contas set arquivada_em = now() where id = " + conjunta);
        MvcTestResult resultado = converter(usuario, ted, recebida, null, null);
        arquivamento.get(15, TimeUnit.SECONDS);

        assertThat(resultado).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Conta conjunta está arquivada. Reative a conta para converter este lançamento.");
        assertThat(contar("transferencias", usuario)).isZero();
    }

    @Test
    void desfazerEsperaATravaDeContasEVeAContaArquivada() throws Exception {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long conjunta = novaConta(usuario, "Conta conjunta");
        long ted = importado(usuario, horizonte, "2026-11-02", "-1000.00", "TED");
        long recebida = importado(usuario, conjunta, "2026-11-03", "1000.00", "RECEBIDA");
        long id = converterComPar(usuario, ted, recebida, null);

        CompletableFuture<Void> arquivamento = segurarATravaDeContasE(usuario.id(),
                "update contas set arquivada_em = now() where id = " + conjunta);
        MvcTestResult resultado = api.acao(usuario, "/api/transferencias/{id}/desfazer", id);
        arquivamento.get(15, TimeUnit.SECONDS);

        assertThat(resultado).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Conta conjunta está arquivada. Reative a conta para desfazer esta transferência.");
        assertThat(lados(id)).hasSize(2);
    }

    /** Segura a trava de contas do usuário, espera o pedido preso nela e roda o SQL. */
    private CompletableFuture<Void> segurarATravaDeContasE(Long usuarioId, String sql) throws InterruptedException {
        CountDownLatch travado = new CountDownLatch(1);
        CompletableFuture<Void> tarefa = CompletableFuture.runAsync(() -> transacao.executeWithoutResult(status -> {
            jdbc.queryForObject("select 1 from pg_advisory_xact_lock(hashtextextended(? || ?, 0))", Integer.class, "contas:",
                    String.valueOf(usuarioId));
            travado.countDown();
            Awaitility.await().atMost(Duration.ofSeconds(10)).pollInSameThread().pollInterval(Duration.ofMillis(20))
                    .until(() -> {
                        jdbc.execute("select pg_stat_clear_snapshot()");
                        return jdbc.queryForObject("select count(*) from pg_stat_activity where pid <> pg_backend_pid() "
                                + "and datname = current_database() and wait_event_type = 'Lock' and wait_event = 'advisory'",
                                Integer.class) > 0;
                    });
            jdbc.execute(sql);
        }));
        assertThat(travado.await(10, TimeUnit.SECONDS)).isTrue();
        return tarefa;
    }
}