package com.flux.lancamentos.interno;

import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.flux.suporte.ApiDeTeste.conta;
import static com.flux.suporte.ApiDeTeste.lancamento;
import static com.flux.suporte.ApiDeTeste.transferencia;
import static org.assertj.core.api.Assertions.assertThat;

/** Decisão 34: 1.000 lançamentos por mês e 100.000 no total, por usuário. */
class LimitesDeLancamentosIT extends IntegracaoBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void mesComMilLancamentosRecusaOutroEATransferencia() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Horizonte", "CORRENTE", "0", "2026-09-01", "null")));
        long poupanca = api.idCriado(api.post(usuario, "/api/contas", conta("Poupança", "POUPANCA", "0", "2026-09-01", "null")));
        inserir(usuario, horizonte, 998, "2026-10-01", 30);

        long novembro = api.idCriado(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "1", "2026-11-02", "Novembro", horizonte, null)));
        assertThat(api.post(usuario, "/api/transferencias", transferencia("1", "2026-10-10", horizonte, poupanca, null))).hasStatus(201);
        assertRecusa(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "1", "2026-10-10", "Mil e um", horizonte, null)),
                "Você chegou ao limite de 1.000 lançamentos em outubro de 2026.");
        assertRecusa(api.put(usuario, "/api/lancamentos/{id}", lancamento("DESPESA", "1", "2026-10-10", "Mudou de mês", horizonte, null), novembro),
                "Você chegou ao limite de 1.000 lançamentos em outubro de 2026.");
        assertThat(api.put(usuario, "/api/lancamentos/{id}", lancamento("DESPESA", "2", "2026-11-03", "No mesmo mês", horizonte, null), novembro))
                .hasStatusOk();
        assertThat(api.post(usuario, "/api/transferencias", transferencia("1", "2026-09-10", horizonte, poupanca, null))).hasStatus(201);
    }

    @Test
    void usuarioComCemMilLancamentosNaoCriaMais() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Horizonte", "CORRENTE", "0", "2016-01-01", "null")));
        long poupanca = api.idCriado(api.post(usuario, "/api/contas", conta("Poupança", "POUPANCA", "0", "2016-01-01", "null")));
        // 99.999 lançamentos espalhados por 3.650 dias desde 2016: nenhum mês chega a 1.000.
        inserir(usuario, horizonte, 99_999, "2016-01-01", 3_650);

        assertThat(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "1", "2026-10-10", "Cem mil", horizonte, null))).hasStatus(201);
        String mensagem = "Você chegou ao limite de 100.000 lançamentos.";
        assertRecusa(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "1", "2026-10-10", "Um a mais", horizonte, null)), mensagem);
        assertRecusa(api.post(usuario, "/api/transferencias", transferencia("1", "2026-10-10", horizonte, poupanca, null)), mensagem);
    }

    /** {@code quantidade} despesas de R$ 1,00 pelo SQL, a partir de {@code inicio}, espalhadas por {@code dias} dias. */
    private void inserir(UsuarioDeTeste usuario, long contaId, int quantidade, String inicio, int dias) {
        jdbc.update("insert into lancamentos (usuario_id, conta_id, data, descricao, descricao_chave, valor, tipo, origem) "
                + "select ?, ?, cast(? as date) + (g % ?), 'Carga', 'carga', -1, 'DESPESA', 'MANUAL' from generate_series(1, ?) g",
                usuario.id(), contaId, inicio, dias, quantidade);
    }

    private static void assertRecusa(MvcTestResult resposta, String mensagem) {
        assertThat(resposta).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("LIMITE_DE_LANCAMENTOS");
        assertThat(resposta).bodyJson().extractingPath("detail").isEqualTo(mensagem);
    }
}
