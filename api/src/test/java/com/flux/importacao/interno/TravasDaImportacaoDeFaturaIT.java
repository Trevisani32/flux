package com.flux.importacao.interno;

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
 * Fatia 05c (decisão 49): a confirmação da fatura pega a trava de cartões e só depois lê o cartão e classifica; o
 * desfazer também espera a trava de cartões. Outra transação segura a trava, muda o banco e termina; o pedido vê a
 * mudança.
 */
class TravasDaImportacaoDeFaturaIT extends FaturaBase {

    @Autowired
    private TransactionTemplate transacao;

    @Test
    void aConfirmacaoEsperaATravaDeCartoesEVeOCartaoArquivado() throws Exception {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, novaConta(usuario, "Banco Horizonte"));
        perfilDoNubank(usuario, horizonte);
        byte[] fatura = csvDoNubank(linha(dia(m().minusMonths(1), 9), "Padaria Pao Quente", "22.50"));
        String hash = api.json(previaNoCartao(usuario, horizonte, null, "fatura.csv", fatura)).get("hash").asString();

        CompletableFuture<Void> arquivamento = segurarATravaE(usuario.id(), "update cartoes set arquivado_em = now() where id = " + horizonte);
        MvcTestResult resultado = confirmarNoCartao(usuario, horizonte, m().toString(), "fatura.csv", fatura, hash, escolhas(2));
        arquivamento.get(15, TimeUnit.SECONDS);

        assertThat(resultado).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("CARTAO_ARQUIVADO");
        assertThat(contar("lancamentos", usuario)).isZero();
        assertThat(contar("importacoes", usuario)).isZero();
    }

    @Test
    void oDesfazerEsperaATravaDeCartoesEVeAFaturaPaga() throws Exception {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, novaConta(usuario, "Banco Horizonte"));
        perfilDoNubank(usuario, horizonte);
        long importacao = importarNoCartao(usuario, horizonte, "fatura.csv",
                csvDoNubank(linha(dia(m().minusMonths(1), 9), "Padaria Pao Quente", "22.50")), 2);

        CompletableFuture<Void> marca = segurarATravaE(usuario.id(), "update faturas set paga_fora_em = now() where cartao_id = " + horizonte);
        MvcTestResult resultado = api.acao(usuario, "/api/importacoes/{id}/desfazer", importacao);
        marca.get(15, TimeUnit.SECONDS);

        assertThat(resultado).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("FATURA_PAGA");
        assertThat(contar("lancamentos", usuario)).isEqualTo(1);
    }

    /** Segura a trava de cartões do usuário, espera o pedido preso nela e roda o SQL. */
    private CompletableFuture<Void> segurarATravaE(Long usuarioId, String sql) throws InterruptedException {
        CountDownLatch travado = new CountDownLatch(1);
        CompletableFuture<Void> tarefa = CompletableFuture.runAsync(() -> transacao.executeWithoutResult(status -> {
            jdbc.queryForObject("select 1 from pg_advisory_xact_lock(hashtextextended('cartoes:' || ?, 0))", Integer.class,
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
