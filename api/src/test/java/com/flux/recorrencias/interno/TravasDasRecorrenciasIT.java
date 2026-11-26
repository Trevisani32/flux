package com.flux.recorrencias.interno;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;

import static com.flux.suporte.ApiDeTeste.lancamento;
import static org.assertj.core.api.Assertions.assertThat;

class TravasDasRecorrenciasIT extends RecorrenciasBase {

    @Autowired
    private TransactionTemplate transacao;

    @Test
    void confirmarEsperaAsTresTravasEVeOQueMudou() throws Exception {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        Exemplo exemplo = montarOExemplo(usuario);
        int lancamentosAntes = contar("lancamentos", usuario);
        int comprasAntes = contar("compras", usuario);

        CompletableFuture<Void> contaArquivada = segurarATravaE("contas:", usuario.id(),
                "update contas set arquivada_em = now() where id = " + exemplo.bancoHorizonte());
        MvcTestResult naConta = confirmar(usuario, exemplo.aluguel(), "2026-12-01", "2026-12-01", "1850.00");
        contaArquivada.get(15, TimeUnit.SECONDS);
        assertRegra(naConta, "A recorrência está pausada: Banco Horizonte está arquivada.");

        CompletableFuture<Void> cartaoArquivado = segurarATravaE("cartoes:", usuario.id(),
                "update cartoes set arquivado_em = now() where id = " + exemplo.cartaoLua());
        MvcTestResult noCartao = confirmar(usuario, exemplo.netflix(), "2026-11-01", "2026-11-28", "55.90");
        cartaoArquivado.get(15, TimeUnit.SECONDS);
        assertRegra(noCartao, "A recorrência está pausada: Cartão Lua está arquivado.");

        CompletableFuture<Void> categoriaArquivada = segurarATravaE("categorias:", usuario.id(),
                "update categorias set arquivada_em = now() where id = " + idDaCategoria(usuario, "Saúde"));
        MvcTestResult naCategoria = confirmar(usuario, exemplo.plano(), "2026-12-01", "2026-12-10", "640.00");
        categoriaArquivada.get(15, TimeUnit.SECONDS);
        assertRegra(naCategoria, "A recorrência está pausada: a categoria Saúde está arquivada.");
        assertThat(contar("lancamentos", usuario)).isEqualTo(lancamentosAntes);
        assertThat(contar("compras", usuario)).isEqualTo(comprasAntes);
    }

    @Test
    void ligarEsperaATravaDeContas() throws Exception {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        Exemplo exemplo = montarOExemplo(usuario);
        long aluguel = api.idCriado(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "1850.00", "2026-11-30",
                "Aluguel apartamento",
                exemplo.bancoHorizonte(), idDaCategoria(usuario, "Aluguel"))));
        int tratadasAntes = contar("ocorrencias_tratadas", usuario);
        CompletableFuture<Void> apagado = segurarATravaE("contas:", usuario.id(), "delete from lancamentos where id = " + aluguel);
        MvcTestResult ligada = ligar(usuario, exemplo.aluguel(), "2026-12-01", aluguel, null);
        apagado.get(15, TimeUnit.SECONDS);
        assertRegra(ligada, "Lançamento não encontrado.");
        assertThat(contar("ocorrencias_tratadas", usuario)).isEqualTo(tratadasAntes);
    }

    @Test
    void duasConfirmacoesDoMesmoPeriodoNaoPassamJuntas() throws Exception {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        Exemplo exemplo = montarOExemplo(usuario);
        int lancamentosAntes = contar("lancamentos", usuario);
        int tratadasAntes = contar("ocorrencias_tratadas", usuario);
        CompletableFuture<Void> trava = segurarATravaE("recorrencias:", usuario.id(), "select 1");
        CompletableFuture<MvcTestResult> primeira = CompletableFuture.supplyAsync(
                () -> confirmar(usuario, exemplo.condominio(), "2026-12-01", "2026-12-01", "420.00"));
        CompletableFuture<MvcTestResult> segunda = CompletableFuture.supplyAsync(
                () -> confirmar(usuario, exemplo.condominio(), "2026-12-01", "2026-12-01", "420.00"));
        trava.get(15, TimeUnit.SECONDS);
        MvcTestResult respostaUm = primeira.get(15, TimeUnit.SECONDS);
        MvcTestResult respostaDois = segunda.get(15, TimeUnit.SECONDS);
        assertThat(List.of(respostaUm.getResponse().getStatus(), respostaDois.getResponse().getStatus()))
                .containsExactlyInAnyOrder(200, 422);
        MvcTestResult recusada = respostaUm.getResponse().getStatus() == 422 ? respostaUm : respostaDois;
        assertRegra(recusada, "Esta ocorrência já foi confirmada.");
        assertThat(contar("lancamentos", usuario)).isEqualTo(lancamentosAntes + 1);
        assertThat(contar("ocorrencias_tratadas", usuario)).isEqualTo(tratadasAntes + 1);
    }

    private CompletableFuture<Void> segurarATravaE(String chave, Long usuarioId, String sql) throws InterruptedException {
        CountDownLatch travado = new CountDownLatch(1);
        CompletableFuture<Void> tarefa = CompletableFuture.runAsync(() -> transacao.executeWithoutResult(status -> {
            jdbc.queryForObject("select 1 from pg_advisory_xact_lock(hashtextextended(? || ?, 0))", Integer.class, chave,
                    String.valueOf(usuarioId));
            travado.countDown();
            Awaitility.await().atMost(Duration.ofSeconds(10)).pollInSameThread().pollInterval(Duration.ofMillis(20)).until(() -> {
                jdbc.execute("select pg_stat_clear_snapshot()");
                return jdbc.queryForObject("select count(*) from pg_stat_activity where pid <> pg_backend_pid() "
                        + "and datname = current_database() "
                        + "and wait_event_type = 'Lock' and wait_event = 'advisory'", Integer.class) > 0;
            });
            jdbc.execute(sql);
        }));
        assertThat(travado.await(10, TimeUnit.SECONDS)).isTrue();
        return tarefa;
    }

    private static void assertRegra(MvcTestResult resposta, String detalhe) {
        assertThat(resposta).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo(detalhe);
    }
}
