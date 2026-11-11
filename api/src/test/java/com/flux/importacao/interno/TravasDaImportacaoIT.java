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
 * Decisões 13 e 38: a confirmação pega a trava de contas e só depois lê a conta e classifica. Outra
 * transação segura a trava, muda o banco e termina; a confirmação vê a mudança.
 */
class TravasDaImportacaoIT extends ImportacaoBase {

    private static final byte[] EXTRATO = ofx("0260", "12345678-0",
            transacao("f1", "2026-11-05", "-6.00", "CAFE DA ESQUINA"),
            transacao("f2", "2026-11-06", "-12.50", "PADARIA CENTRAL"));

    @Autowired
    private TransactionTemplate transacao;

    @Test
    void confirmacaoEsperaATravaEVeAContaArquivada() throws Exception {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte", "2026-10-01");
        String hash = api.json(previa(usuario, horizonte, "extrato.ofx", EXTRATO)).get("hash").asString();

        CompletableFuture<Void> arquivamento = segurarATravaE(usuario.id(), "update contas set arquivada_em = now() where id = " + horizonte);
        MvcTestResult resultado = confirmar(usuario, horizonte, "extrato.ofx", EXTRATO, hash, escolhas(1, 2));
        arquivamento.get(15, TimeUnit.SECONDS);

        assertThat(resultado).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("CONTA_ARQUIVADA");
        assertThat(jdbc.queryForObject("select count(*) from lancamentos where usuario_id = ?", Integer.class, usuario.id())).isZero();
    }

    @Test
    void confirmacaoEsperaATravaEClassificaDeNovo() throws Exception {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte", "2026-10-01");
        String hash = api.json(previa(usuario, horizonte, "extrato.ofx", EXTRATO)).get("hash").asString();
        // Outra importação grava a linha f1 enquanto esta espera a trava.
        String outraImportacao = "with i as (insert into importacoes (usuario_id, conta_id, formato, nome_arquivo, hash_arquivo, "
                + "data_inicio, data_fim, lidas, importadas, duplicadas, anteriores, fora, desmarcadas) values (" + usuario.id() + ", "
                + horizonte + ", 'OFX', 'outra.ofx', repeat('b', 64), '2026-11-05', '2026-11-05', 1, 1, 0, 0, 0, 0) returning id) "
                + "insert into lancamentos (usuario_id, conta_id, data, descricao, descricao_chave, valor, tipo, origem, importacao_id, "
                + "chave_importacao, descricao_banco) select " + usuario.id() + ", " + horizonte + ", '2026-11-05', 'CAFE DA ESQUINA', "
                + "'cafe da esquina', -6.00, 'DESPESA', 'IMPORTADO', i.id, 'ID:" + horizonte + ":f1', 'CAFE DA ESQUINA' from i";

        CompletableFuture<Void> outra = segurarATravaE(usuario.id(), outraImportacao);
        MvcTestResult resultado = confirmar(usuario, horizonte, "extrato.ofx", EXTRATO, hash, escolhas(1, 2));
        outra.get(15, TimeUnit.SECONDS);

        assertThat(resultado).hasStatus(201);
        assertThat(resultado).bodyJson().extractingPath("importadas").isEqualTo(1);
        assertThat(resultado).bodyJson().extractingPath("entraramPorOutra").isEqualTo(1);
        assertThat(resultado).bodyJson().extractingPath("jaImportadas").isEqualTo(0);
        assertThat(jdbc.queryForObject("select duplicadas from importacoes where usuario_id = ? order by id desc limit 1", Integer.class,
                usuario.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from lancamentos where usuario_id = ?", Integer.class, usuario.id())).isEqualTo(2);
    }

    /** Segura a trava de contas do usuário, espera a confirmação presa nela e roda o SQL. */
    private CompletableFuture<Void> segurarATravaE(Long usuarioId, String sql) throws InterruptedException {
        CountDownLatch travado = new CountDownLatch(1);
        CompletableFuture<Void> tarefa = CompletableFuture.runAsync(() -> transacao.executeWithoutResult(status -> {
            jdbc.queryForObject("select 1 from pg_advisory_xact_lock(hashtextextended('contas:' || ?, 0))", Integer.class,
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
