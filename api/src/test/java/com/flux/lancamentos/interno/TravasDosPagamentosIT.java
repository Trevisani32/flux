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
 * Decisão 49 da Fatia 05 (ADR 0018): o pagamento pega a trava de contas e depois a de cartões antes de ler a conta e o
 * cartão; a compra pega a de cartões antes de conferir a fatura paga. Outra transação segura a trava, muda o que o pedido
 * lê e termina; o pedido preso nela vê a mudança.
 */
class TravasDosPagamentosIT extends PagamentosBase {

    @Autowired
    private TransactionTemplate transacao;

    @Test
    void oPagamentoEsperaAsTravasDeContasEDeCartoesEVeOQueMudou() throws Exception {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long carteira = novaConta(usuario, "Carteira", "CARTEIRA");
        long cartao = cartaoQueFecha5(usuario, "Cartão Horizonte", conta);
        novaCompra(usuario, cartao, diaUmAtras(2), "Livraria", "100", 1);

        CompletableFuture<Void> contaArquivada = segurarATravaE("contas:", usuario.id(),
                "update contas set arquivada_em = now() where id = " + carteira);
        MvcTestResult naConta = pagar(usuario, cartao, mesAtras(2), carteira, hoje().toString(), "100");
        contaArquivada.get(15, TimeUnit.SECONDS);
        assertThat(naConta).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Carteira está arquivada. Escolha uma conta ativa.");

        CompletableFuture<Void> cartaoArquivado = segurarATravaE("cartoes:", usuario.id(),
                "update cartoes set arquivado_em = now() where id = " + cartao);
        MvcTestResult noCartao = pagar(usuario, cartao, mesAtras(2), conta, hoje().toString(), "100");
        cartaoArquivado.get(15, TimeUnit.SECONDS);
        assertThat(noCartao).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Cartão Horizonte está arquivado. Reative o cartão para pagar a fatura.");
        assertThat(jdbc.queryForObject("select count(*) from lancamentos where tipo = 'PAGAMENTO_FATURA' and usuario_id = ?", Integer.class,
                usuario.id())).isZero();
    }

    @Test
    void excluirACompraEsperaATravaDeCartoesEVeAFaturaPaga() throws Exception {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long cartao = cartaoQueFecha5(usuario, "Cartão Horizonte", conta);
        long compra = novaCompra(usuario, cartao, diaUmAtras(2), "Livraria", "100", 1);
        long fatura = jdbc.queryForObject("select fatura_id from lancamentos where compra_id = ?", Long.class, compra);

        CompletableFuture<Void> pagamento = segurarATravaE("cartoes:", usuario.id(), "insert into lancamentos (usuario_id, conta_id, data, "
                + "descricao, descricao_chave, valor, tipo, origem, fatura_paga_id) values (" + usuario.id() + ", " + conta + ", current_date, "
                + "'Pagamento', 'pagamento', -100, 'PAGAMENTO_FATURA', 'MANUAL', " + fatura + ")");
        MvcTestResult excluir = api.delete(usuario, "/api/compras/{id}", compra);
        pagamento.get(15, TimeUnit.SECONDS);
        assertThat(excluir).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("FATURA_PAGA");
        assertThat(contar("compras", usuario)).isEqualTo(1);
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
