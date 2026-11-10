package com.flux.lancamentos.interno;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;

import static com.flux.suporte.ApiDeTeste.conta;
import static com.flux.suporte.ApiDeTeste.lancamento;
import static com.flux.suporte.ApiDeTeste.transferencia;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR 0012: quem cria ou edita um lançamento pega antes a trava de contas e a de categorias.
 * Aqui, outra transação segura a trava, espera o pedido ficar preso nela, muda a conta ou a
 * categoria e termina: o pedido vê a mudança e responde 422, em vez de cair na FK com 500.
 */
class TravasDosLancamentosIT extends IntegracaoBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transacao;

    @Test
    void lancamentoEsperaATravaDeContasEVeAContaExcluida() throws Exception {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Horizonte", "CORRENTE", "0", "2026-10-01", "null")));

        CompletableFuture<Void> exclusao = segurarATravaE("contas:", usuario.id(), "delete from contas where id = " + horizonte);
        MvcTestResult resposta = api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "10", "2026-10-06", "X", horizonte, null));
        exclusao.get(15, TimeUnit.SECONDS);

        assertThat(resposta).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Conta não encontrada.");
    }

    @Test
    void lancamentoEsperaATravaDeCategoriasEVeACategoriaExcluida() throws Exception {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Horizonte", "CORRENTE", "0", "2026-10-01", "null")));
        long lazer = jdbc.queryForObject("select id from categorias where usuario_id = ? and nome = 'Lazer'", Long.class, usuario.id());

        CompletableFuture<Void> exclusao = segurarATravaE("categorias:", usuario.id(), "delete from categorias where id = " + lazer);
        MvcTestResult resposta = api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "10", "2026-10-06", "Cinema", horizonte, lazer));
        exclusao.get(15, TimeUnit.SECONDS);

        assertThat(resposta).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Categoria não encontrada.");
    }

    @Test
    void transferenciaEsperaATravaDeContasEVeAContaArquivada() throws Exception {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Horizonte", "CORRENTE", "0", "2026-10-01", "null")));
        long poupanca = api.idCriado(api.post(usuario, "/api/contas", conta("Poupança", "POUPANCA", "0", "2026-10-01", "null")));

        CompletableFuture<Void> arquivamento = segurarATravaE("contas:", usuario.id(),
                "update contas set arquivada_em = now() where id = " + poupanca);
        MvcTestResult resposta = api.post(usuario, "/api/transferencias", transferencia("10", "2026-10-06", horizonte, poupanca, null));
        arquivamento.get(15, TimeUnit.SECONDS);

        assertThat(resposta).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("CONTA_ARQUIVADA");
        assertThat(jdbc.queryForObject("select count(*) from lancamentos where usuario_id = ?", Integer.class, usuario.id())).isZero();
    }

    @Test
    void edicaoDoLancamentoEsperaATravaDeContasEVeAContaExcluida() throws Exception {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Horizonte", "CORRENTE", "0", "2026-10-01", "null")));
        long carteira = api.idCriado(api.post(usuario, "/api/contas", conta("Carteira", "CARTEIRA", "0", "2026-10-01", "null")));
        long lancamento = api.idCriado(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "10", "2026-10-06", "X", horizonte, null)));

        CompletableFuture<Void> exclusao = segurarATravaE("contas:", usuario.id(), "delete from contas where id = " + carteira);
        MvcTestResult resposta = api.put(usuario, "/api/lancamentos/{id}", lancamento("DESPESA", "10", "2026-10-06", "X", carteira, null), lancamento);
        exclusao.get(15, TimeUnit.SECONDS);

        assertThat(resposta).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Conta não encontrada.");
        assertThat(jdbc.queryForObject("select conta_id from lancamentos where id = ?", Long.class, lancamento)).isEqualTo(horizonte);
    }

    @Test
    void edicaoDaTransferenciaEsperaATravaDeContasEVeAContaArquivada() throws Exception {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Horizonte", "CORRENTE", "0", "2026-10-01", "null")));
        long poupanca = api.idCriado(api.post(usuario, "/api/contas", conta("Poupança", "POUPANCA", "0", "2026-10-01", "null")));
        long carteira = api.idCriado(api.post(usuario, "/api/contas", conta("Carteira", "CARTEIRA", "0", "2026-10-01", "null")));
        long transferenciaId = api.idCriado(api.post(usuario, "/api/transferencias", transferencia("10", "2026-10-06", horizonte, poupanca, null)));

        CompletableFuture<Void> arquivamento = segurarATravaE("contas:", usuario.id(),
                "update contas set arquivada_em = now() where id = " + carteira);
        MvcTestResult resposta = api.put(usuario, "/api/transferencias/{id}", transferencia("10", "2026-10-06", horizonte, carteira, null),
                transferenciaId);
        arquivamento.get(15, TimeUnit.SECONDS);

        assertThat(resposta).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("CONTA_ARQUIVADA");
        assertThat(jdbc.queryForObject("select conta_id from lancamentos where transferencia_id = ? and valor > 0", Long.class, transferenciaId))
                .isEqualTo(poupanca);
    }

    /** Segura a trava ('contas:' ou 'categorias:' + usuário), espera outra sessão presa numa trava e roda o SQL. */
    private CompletableFuture<Void> segurarATravaE(String prefixo, Long usuarioId, String sql) throws InterruptedException {
        CountDownLatch travado = new CountDownLatch(1);
        CompletableFuture<Void> tarefa = CompletableFuture.runAsync(() -> transacao.executeWithoutResult(status -> {
            jdbc.queryForObject("select 1 from pg_advisory_xact_lock(hashtextextended(? || ?, 0))", Integer.class, prefixo,
                    String.valueOf(usuarioId));
            travado.countDown();
            Awaitility.await().atMost(Duration.ofSeconds(10)).pollInSameThread().pollInterval(Duration.ofMillis(20))
                    .until(() -> {
                        // Dentro de uma transação, o pg_stat_activity é lido uma vez só sem isto.
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
