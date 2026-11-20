package com.flux.lancamentos.interno;

import com.flux.suporte.IntegracaoBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A V4 como o ADR 0012 e o ADR 0013 pedem: o uso de conta e de categoria é RESTRICT, nunca CASCADE. As
 * listas já contam o que a V5 acrescentou a lançamentos (a FK e os dois índices da importação) e o que a V8 acrescentou
 * (Fatia 05a: as FKs do cartão, da fatura e da compra, RESTRICT, e os três índices das parcelas).
 */
class MigracaoDeLancamentosIT extends IntegracaoBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void chavesEstrangeirasEIndicesDaV4() {
        assertThat(jdbc.queryForList("select version || ':' || success from flyway_schema_history order by installed_rank",
                String.class)).contains("4:true");
        // confdeltype: r = RESTRICT, c = CASCADE.
        assertThat(jdbc.queryForList("select conname || ':' || confdeltype::text from pg_constraint where contype = 'f' "
                + "and conrelid in ('lancamentos'::regclass, 'transferencias'::regclass) order by conname", String.class))
                .containsExactly("fk_lancamentos_cartao:r", "fk_lancamentos_categoria:r", "fk_lancamentos_compra:r", "fk_lancamentos_conta:r",
                        "fk_lancamentos_fatura:r", "fk_lancamentos_importacao:r", "fk_lancamentos_transferencia:c", "lancamentos_usuario_id_fkey:c", "transferencias_usuario_id_fkey:c");
        assertThat(jdbc.queryForList("select indexname from pg_indexes where tablename = 'lancamentos' order by indexname",
                String.class)).containsExactly("ix_lancamentos_cartao", "ix_lancamentos_categoria", "ix_lancamentos_fatura", "ix_lancamentos_importacao",
                "ix_lancamentos_usuario_conta_data", "ix_lancamentos_usuario_data", "lancamentos_pkey",
                "ux_lancamentos_chave_importacao", "ux_lancamentos_lado_da_transferencia", "ux_lancamentos_parcela");
        assertThat(jdbc.queryForObject("select pg_get_constraintdef(oid) from pg_constraint where conname = 'fk_lancamentos_categoria'",
                String.class)).isEqualTo("FOREIGN KEY (categoria_id, usuario_id, tipo) REFERENCES categorias(id, usuario_id, natureza) "
                + "ON DELETE RESTRICT");
    }
}
