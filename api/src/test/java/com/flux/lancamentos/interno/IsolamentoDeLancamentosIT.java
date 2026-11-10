package com.flux.lancamentos.interno;

import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.flux.suporte.ApiDeTeste.conta;
import static com.flux.suporte.ApiDeTeste.lancamento;
import static com.flux.suporte.ApiDeTeste.texto;
import static com.flux.suporte.ApiDeTeste.transferencia;
import static org.assertj.core.api.Assertions.assertThat;

/** ADR 0007: B não lista, não lê, não edita, não exclui e não referencia nada de A. */
class IsolamentoDeLancamentosIT extends IntegracaoBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void outroUsuarioNaoAlcancaLancamentosTransferenciasContasNemCategorias() {
        UsuarioDeTeste dono = usuariosDeTeste.criar();
        UsuarioDeTeste outro = usuariosDeTeste.criar();
        long contaDoDono = api.idCriado(api.post(dono, "/api/contas", conta("Do dono", "CORRENTE", "0", "2026-10-01", "null")));
        long poupancaDoDono = api.idCriado(api.post(dono, "/api/contas", conta("Poupança", "POUPANCA", "0", "2026-10-01", "null")));
        long mercadoDoDono = jdbc.queryForObject("select id from categorias where usuario_id = ? and nome = 'Mercado'", Long.class, dono.id());
        long lancamento = api.idCriado(api.post(dono, "/api/lancamentos", lancamento("DESPESA", "10", "2026-10-06", "Do dono", contaDoDono, mercadoDoDono)));
        long transferenciaDoDono = api.idCriado(api.post(dono, "/api/transferencias", transferencia("5", "2026-10-06", contaDoDono, poupancaDoDono, null)));
        long contaDoOutro = api.idCriado(api.post(outro, "/api/contas", conta("Do outro", "CORRENTE", "0", "2026-10-01", "null")));
        long poupancaDoOutro = api.idCriado(api.post(outro, "/api/contas", conta("Poupança", "POUPANCA", "0", "2026-10-01", "null")));

        assertThat(api.get(outro, "/api/lancamentos/{id}", lancamento)).hasStatus(404).bodyJson().extractingPath("detail")
                .isEqualTo("Lançamento não encontrado.");
        assertThat(api.put(outro, "/api/lancamentos/{id}", lancamento("DESPESA", "1", "2026-10-06", "X", contaDoOutro, null), lancamento)).hasStatus(404);
        assertThat(api.delete(outro, "/api/lancamentos/{id}", lancamento)).hasStatus(404);
        assertThat(api.get(outro, "/api/transferencias/{id}", transferenciaDoDono)).hasStatus(404).bodyJson().extractingPath("detail")
                .isEqualTo("Transferência não encontrada.");
        assertThat(api.put(outro, "/api/transferencias/{id}", transferencia("1", "2026-10-06", contaDoOutro, poupancaDoOutro, null), transferenciaDoDono))
                .hasStatus(404);
        assertThat(api.delete(outro, "/api/transferencias/{id}", transferenciaDoDono)).hasStatus(404);

        assertThat(api.post(outro, "/api/lancamentos", lancamento("DESPESA", "1", "2026-10-06", "X", contaDoDono, null)))
                .hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Conta não encontrada.");
        assertThat(api.post(outro, "/api/lancamentos", lancamento("DESPESA", "1", "2026-10-06", "X", contaDoOutro, mercadoDoDono)))
                .hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Categoria não encontrada.");
        assertThat(api.post(outro, "/api/transferencias", transferencia("1", "2026-10-06", contaDoOutro, contaDoDono, null)))
                .hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Conta não encontrada.");

        assertThat(texto(api.get(outro, "/api/lancamentos?mes=2026-10"))).contains("\"dias\":[]");
        assertThat(texto(api.get(outro, "/api/lancamentos?mes=2026-10&conta={id}", contaDoDono))).contains("\"dias\":[]");
        assertThat(texto(api.get(outro, "/api/lancamentos?mes=2026-10&categoria={id}", mercadoDoDono))).contains("\"dias\":[]");
        assertThat(texto(api.get(outro, "/api/lancamentos/ultimos"))).isEqualTo("[]");
        assertThat(jdbc.queryForObject("select count(*) from lancamentos where usuario_id = ?", Integer.class, dono.id())).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from lancamentos where usuario_id = ?", Integer.class, outro.id())).isZero();

        // Nem na edição: B edita o que é dele apontando para a conta de A, e nada muda.
        long lancamentoDoOutro = api.idCriado(api.post(outro, "/api/lancamentos", lancamento("DESPESA", "1", "2026-10-06", "Do outro", contaDoOutro, null)));
        long transferenciaDoOutro = api.idCriado(api.post(outro, "/api/transferencias", transferencia("1", "2026-10-06", contaDoOutro, poupancaDoOutro, null)));
        assertThat(api.put(outro, "/api/lancamentos/{id}", lancamento("DESPESA", "1", "2026-10-06", "Do outro", contaDoDono, null), lancamentoDoOutro))
                .hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Conta não encontrada.");
        assertThat(api.put(outro, "/api/transferencias/{id}", transferencia("1", "2026-10-06", contaDoOutro, contaDoDono, null), transferenciaDoOutro))
                .hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Conta não encontrada.");
        assertThat(jdbc.queryForObject("select count(*) from lancamentos where conta_id = ?", Integer.class, contaDoDono)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from lancamentos where usuario_id = ? and conta_id = ?", Integer.class, outro.id(), contaDoOutro))
                .isEqualTo(2);
    }
}
