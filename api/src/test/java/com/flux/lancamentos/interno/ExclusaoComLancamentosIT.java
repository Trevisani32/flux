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

/** A trava de exclusão da 03a com as implementações reais, e o campo "uso" do 422 (decisão 33). */
class ExclusaoComLancamentosIT extends IntegracaoBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void contaComLancamentosOuTransferenciasNaoPodeSerExcluida() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Horizonte", "CORRENTE", "0", "2026-10-01", "null")));
        long poupanca = api.idCriado(api.post(usuario, "/api/contas", conta("Poupança", "POUPANCA", "0", "2026-10-01", "null")));
        long luz = api.idCriado(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "184.37", "2026-10-03", "Luz", horizonte, null)));
        long salario = api.idCriado(api.post(usuario, "/api/lancamentos", lancamento("RECEITA", "7200", "2026-10-05", "Salário", horizonte, null)));
        long reserva = api.idCriado(api.post(usuario, "/api/transferencias", transferencia("500", "2026-10-05", horizonte, poupanca, null)));

        MvcTestResult recusada = api.delete(usuario, "/api/contas/{id}", horizonte);
        assertThat(recusada).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("CONTA_EM_USO");
        assertThat(recusada).bodyJson().extractingPath("uso").isEqualTo("2 lançamentos e 1 transferência");
        assertThat(recusada).bodyJson().extractingPath("detail").isEqualTo("Esta conta não pode ser excluída porque tem "
                + "2 lançamentos e 1 transferência. Arquive a conta para tirá-la do saldo total e dos seletores.");
        assertThat(api.delete(usuario, "/api/contas/{id}", poupanca)).hasStatus(422).bodyJson().extractingPath("uso")
                .isEqualTo("1 transferência");

        api.delete(usuario, "/api/transferencias/{id}", reserva);
        api.delete(usuario, "/api/lancamentos/{id}", luz);
        assertThat(api.delete(usuario, "/api/contas/{id}", horizonte)).hasStatus(422).bodyJson().extractingPath("uso")
                .isEqualTo("1 lançamento");
        api.delete(usuario, "/api/lancamentos/{id}", salario);
        assertThat(api.delete(usuario, "/api/contas/{id}", horizonte)).hasStatus(204);
        assertThat(api.delete(usuario, "/api/contas/{id}", poupanca)).hasStatus(204);
    }

    @Test
    void categoriaComLancamentosNaoPodeSerExcluida() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Horizonte", "CORRENTE", "0", "2026-10-01", "null")));
        long mercado = categoria(usuario, "Mercado");
        long id = api.idCriado(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "287.43", "2026-10-06", "Mercado", horizonte, mercado)));

        MvcTestResult principal = api.delete(usuario, "/api/categorias/{id}", categoria(usuario, "Alimentação"));
        assertThat(principal).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("CATEGORIA_EM_USO");
        assertThat(principal).bodyJson().extractingPath("uso").isEqualTo("1 lançamento");
        assertThat(principal).bodyJson().extractingPath("detail").isEqualTo("Esta categoria não pode ser excluída porque ela e "
                + "as subcategorias têm 1 lançamento. Arquive a categoria para tirá-la dos seletores.");
        assertThat(api.delete(usuario, "/api/categorias/{id}", mercado)).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Esta categoria não pode ser excluída porque tem 1 lançamento. Arquive a categoria para tirá-la dos seletores.");
        assertThat(api.delete(usuario, "/api/categorias/{id}", categoria(usuario, "Saúde"))).hasStatus(204);

        api.delete(usuario, "/api/lancamentos/{id}", id);
        assertThat(api.delete(usuario, "/api/categorias/{id}", categoria(usuario, "Alimentação"))).hasStatus(204);
        assertThat(jdbc.queryForObject("select count(*) from categorias where usuario_id = ?", Integer.class, usuario.id())).isEqualTo(19);
    }

    private long categoria(UsuarioDeTeste usuario, String nome) {
        return jdbc.queryForObject("select id from categorias where usuario_id = ? and nome = ?", Long.class, usuario.id(), nome);
    }
}
