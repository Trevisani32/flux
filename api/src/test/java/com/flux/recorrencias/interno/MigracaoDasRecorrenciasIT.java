package com.flux.recorrencias.interno;

import java.util.List;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static com.flux.suporte.ApiDeTeste.lancamento;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigracaoDasRecorrenciasIT extends RecorrenciasBase {

    @Test
    void restricoesEChaves() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        UsuarioDeTeste outro = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Conta da migração");
        long contaDoOutro = novaConta(outro, "Conta do outro");
        long cartao = novoCartao(usuario, "Cartão da migração", 3, 10, conta);
        long categoriaDespesa = idDaCategoria(usuario, "Contas da casa");
        long categoriaReceita = idDaCategoria(usuario, "Salário");
        long lancamento = api.idCriado(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "10", "2026-11-25", "Lançamento", conta,
                categoriaDespesa)));
        long lancamentoDoOutro = api.idCriado(api.post(outro, "/api/lancamentos", lancamento("DESPESA", "10", "2026-11-25", "Outro",
                contaDoOutro, null)));
        long recorrencia = recorrencia(usuario.id(), "Válida", conta, null, categoriaDespesa, "DESPESA", "MENSAL", 1, "10");
        long outraRecorrencia = recorrencia(usuario.id(), "Outra", conta, null, categoriaDespesa, "DESPESA", "MENSAL", 2, "10");

        assertRecusa("insert into recorrencias (usuario_id, descricao, valor, tipo, cartao_id, frequencia, dia_do_mes, comeca_em, "
                + "ocorrencias_desde) "
                + "values (" + usuario.id() + ", 'Receita no cartão', 1, 'RECEITA', " + cartao
                + ", 'MENSAL', 1, '2026-11-25', '2026-11-25')",
                "ck_recorrencias_cartao_so_despesa");
        assertRecusa("insert into recorrencias (usuario_id, descricao, valor, tipo, conta_id, cartao_id, frequencia, dia_do_mes, "
                + "comeca_em, "
                + "ocorrencias_desde) values (" + usuario.id() + ", 'Dois alvos', 1, 'DESPESA', " + conta + ", " + cartao
                + ", 'MENSAL', 1, '2026-11-25', '2026-11-25')", "ck_recorrencias_conta_ou_cartao");
        assertRecusa("insert into recorrencias (usuario_id, descricao, valor, tipo, frequencia, dia_do_mes, comeca_em, "
                + "ocorrencias_desde) values ("
                + usuario.id() + ", 'Sem alvo', 1, 'DESPESA', 'MENSAL', 1, '2026-11-25', '2026-11-25')", "ck_recorrencias_conta_ou_cartao");
        assertRecusa("insert into recorrencias (usuario_id, descricao, valor, tipo, categoria_id, conta_id, frequencia, dia_do_mes, "
                + "comeca_em, "
                + "ocorrencias_desde) values (" + usuario.id() + ", 'Categoria de receita', 1, 'DESPESA', " + categoriaReceita
                + ", " + conta
                + ", 'MENSAL', 1, '2026-11-25', '2026-11-25')", "fk_recorrencias_categoria");
        assertRecusa("insert into recorrencias (usuario_id, descricao, valor, tipo, conta_id, frequencia, dia_do_mes, comeca_em, "
                + "ocorrencias_desde) "
                + "values (" + usuario.id() + ", 'Conta de outro', 1, 'DESPESA', " + contaDoOutro
                + ", 'MENSAL', 1, '2026-11-25', '2026-11-25')", "fk_recorrencias_conta");
        assertRecusa("insert into recorrencias (usuario_id, descricao, valor, tipo, conta_id, frequencia, dia_do_mes, comeca_em, "
                + "ocorrencias_desde) "
                + "values (" + usuario.id() + ", 'Mensal sem dia', 1, 'DESPESA', " + conta
                + ", 'MENSAL', null, '2026-11-25', '2026-11-25')", "ck_recorrencias_dia");
        assertRecusa("insert into recorrencias (usuario_id, descricao, valor, tipo, conta_id, frequencia, dia_do_mes, comeca_em, "
                + "ocorrencias_desde) "
                + "values (" + usuario.id() + ", 'Semanal com dia', 1, 'DESPESA', " + conta
                + ", 'SEMANAL', 1, '2026-11-25', '2026-11-25')", "ck_recorrencias_dia");
        assertRecusa("insert into recorrencias (usuario_id, descricao, valor, tipo, conta_id, frequencia, dia_do_mes, comeca_em, "
                + "ocorrencias_desde) "
                + "values (" + usuario.id() + ", 'Valor zero', 0, 'DESPESA', " + conta
                + ", 'MENSAL', 1, '2026-11-25', '2026-11-25')", "ck_recorrencias_valor");
        assertRecusa(ocorrenciaTratada(usuario.id(), recorrencia, "2026-11-01", "PULADA", lancamento, null), "ck_ocorrencias_ligacao");
        assertRecusa(ocorrenciaTratada(usuario.id(), recorrencia, "2026-11-01", "EFETIVADA", null, null), "ck_ocorrencias_ligacao");
        jdbc.execute(ocorrenciaTratada(usuario.id(), recorrencia, "2026-11-01", "EFETIVADA", lancamento, null));
        assertRecusa(ocorrenciaTratada(usuario.id(), recorrencia, "2026-11-01", "PULADA", null, null), "uq_ocorrencias_periodo");
        assertRecusa(ocorrenciaTratada(usuario.id(), outraRecorrencia, "2026-11-01", "EFETIVADA", lancamento, null),
                "ux_ocorrencias_lancamento");
        assertRecusa(ocorrenciaTratada(usuario.id(), outraRecorrencia, "2026-12-01", "EFETIVADA", lancamentoDoOutro, null),
                "fk_ocorrencias_lancamento");
        assertThat(jdbc.queryForList("select conname from pg_constraint where conname in ('uq_lancamentos_id_usuario', "
                + "'uq_compras_id_usuario') order by conname", String.class))
                .containsExactly("uq_compras_id_usuario", "uq_lancamentos_id_usuario");
    }

    private long recorrencia(long usuarioId, String descricao, long conta, Long cartao, long categoria, String tipo, String frequencia,
                             Integer dia, String valor) {
        return jdbc.queryForObject("insert into recorrencias (usuario_id, descricao, valor, tipo, categoria_id, conta_id, cartao_id, "
                + "frequencia, "
                + "dia_do_mes, comeca_em, ocorrencias_desde) values (?, ?, ?::numeric, ?, ?, ?, ?, ?, ?, '2026-11-25', "
                + "'2026-11-25') returning id",
                Long.class, usuarioId, descricao, valor, tipo, categoria, conta, cartao, frequencia, dia);
    }

    private static String ocorrenciaTratada(long usuarioId, long recorrencia, String periodo, String situacao, Long lancamento,
                                            Long compra) {
        return "insert into ocorrencias_tratadas (usuario_id, recorrencia_id, periodo, data_prevista, situacao, lancamento_id, "
                + "compra_id) values ("
                + usuarioId + ", " + recorrencia + ", '" + periodo + "', '2026-11-25', '" + situacao + "', "
                + (lancamento == null ? "null" : lancamento) + ", " + (compra == null ? "null" : compra) + ")";
    }

    private void assertRecusa(String sql, String restricao) {
        assertThatThrownBy(() -> jdbc.execute(sql)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(restricao);
    }
}
