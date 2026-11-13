package com.flux.lancamentos.interno;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.flux.suporte.CartoesBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decisão 49 da 05a: a compra pega a trava de cartões antes de ler o cartão, e o cartão novo pega a de contas antes
 * de ler a conta de pagamento. Outra transação segura a trava, arquiva e termina; o pedido preso nela vê o arquivado.
 */
class TravasDosCartoesIT extends CartoesBase {

    @Autowired
    private TransactionTemplate transacao;

    @Test
    void aCompraEsperaATravaDeCartoesEVeOCartaoArquivado() throws Exception {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, novaConta(usuario, "Banco Horizonte"));
        CompletableFuture<Void> arquivamento = segurarATravaE("cartoes:", usuario.id(),
                "update cartoes set arquivado_em = now() where id = " + horizonte);
        MvcTestResult resultado = api.post(usuario, "/api/compras", compra(horizonte, "2025-11-09", "Loja", "100", 2, 1, null));
        arquivamento.get(15, TimeUnit.SECONDS);
        assertThat(resultado).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Cartão Horizonte está arquivado. Reative o cartão para lançar nele.");
        assertThat(contar("compras", usuario)).isZero();
    }

    @Test
    void oCartaoNovoEsperaATravaDeContasEVeAContaArquivada() throws Exception {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        CompletableFuture<Void> arquivamento = segurarATravaE("contas:", usuario.id(), "update contas set arquivada_em = now() where id = " + conta);
        MvcTestResult resultado = api.post(usuario, "/api/cartoes", cartao("Cartão Horizonte", "5000", 3, 10, conta, "null"));
        arquivamento.get(15, TimeUnit.SECONDS);
        assertThat(resultado).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Banco Horizonte está arquivada. Escolha uma conta ativa.");
        assertThat(contar("cartoes", usuario)).isZero();
    }

    /** Segura a trava do usuário, espera o pedido preso nela e roda o SQL. */
    private CompletableFuture<Void> segurarATravaE(String chave, Long usuarioId, String sql) throws InterruptedException {
        CountDownLatch travado = new CountDownLatch(1);
        CompletableFuture<Void> tarefa = CompletableFuture.runAsync(() -> transacao.executeWithoutResult(status -> {
            jdbc.queryForObject("select 1 from pg_advisory_xact_lock(hashtextextended(? || ?, 0))", Integer.class, chave,
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
