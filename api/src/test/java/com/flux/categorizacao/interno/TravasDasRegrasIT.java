package com.flux.categorizacao.interno;

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
 * Decisão 25 da 04b (ADR 0012): criar uma regra e aplicar as regras pegam a trava de contas e a de
 * categorias e só depois conferem a conta, a categoria e os lançamentos. Outra transação segura uma
 * das travas, muda o banco e termina; o pedido preso nela vê a mudança.
 */
class TravasDasRegrasIT extends RegrasBase {

    @Autowired
    private TransactionTemplate transacao;

    @Test
    void criarEsperaATravaEVeACategoriaArquivada() throws Exception {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long lazer = idDaCategoria(usuario, "Lazer");

        CompletableFuture<Void> arquivamento = segurarATravaE("categorias:", usuario.id(),
                "update categorias set arquivada_em = now() where id = " + lazer);
        MvcTestResult resultado = api.post(usuario, "/api/regras", regra("CONTEM", "cinema", null, lazer, null));
        arquivamento.get(15, TimeUnit.SECONDS);

        assertThat(resultado).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Escolha uma categoria ativa.");
        assertThat(jdbc.queryForObject("select count(*) from regras_categorizacao where usuario_id = ?", Integer.class, usuario.id())).isZero();
    }

    @Test
    void criarEsperaATravaDeContasEVeAContaArquivada() throws Exception {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long lazer = idDaCategoria(usuario, "Lazer");

        CompletableFuture<Void> arquivamento = segurarATravaE("contas:", usuario.id(),
                "update contas set arquivada_em = now() where id = " + horizonte);
        MvcTestResult resultado = api.post(usuario, "/api/regras", regra("CONTEM", "cinema", horizonte, lazer, null));
        arquivamento.get(15, TimeUnit.SECONDS);

        assertThat(resultado).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Escolha uma conta ativa.");
        assertThat(jdbc.queryForObject("select count(*) from regras_categorizacao where usuario_id = ?", Integer.class, usuario.id())).isZero();
    }

    @Test
    void aplicarEsperaATravaENaoTrocaACategoriaPostaNoMeio() throws Exception {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long app = idDaCategoria(usuario, "App de transporte");
        long lazer = idDaCategoria(usuario, "Lazer");
        long regra = criarRegra(usuario, "CONTEM", "uber", null, app, "Uber");
        long trip = importado(usuario, horizonte, "2026-11-09", "-23.90", "PAG*UBERTRIP 8812");

        CompletableFuture<Void> categorizacao = segurarATravaE("categorias:", usuario.id(), "update lancamentos set categoria_id = " + lazer
                + " where id = " + trip);
        MvcTestResult resultado = api.post(usuario, "/api/regras/aplicacao", "{\"regras\":[" + regra + "]}");
        categorizacao.get(15, TimeUnit.SECONDS);

        assertThat(resultado).hasStatusOk().bodyJson().extractingPath("categorizados").isEqualTo(0);
        assertThat(categoriaDoLancamento(trip)).isEqualTo(lazer);
        assertThat(descricaoDoLancamento(trip)).isEqualTo("PAG*UBERTRIP 8812");
    }

    /** Segura a trava dada ("contas:" ou "categorias:") do usuário, espera o pedido preso nela e roda o SQL. */
    private CompletableFuture<Void> segurarATravaE(String trava, Long usuarioId, String sql) throws InterruptedException {
        CountDownLatch travado = new CountDownLatch(1);
        CompletableFuture<Void> tarefa = CompletableFuture.runAsync(() -> transacao.executeWithoutResult(status -> {
            jdbc.queryForObject("select 1 from pg_advisory_xact_lock(hashtextextended(? || ?, 0))", Integer.class, trava,
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
