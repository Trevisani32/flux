package com.flux.categorizacao.interno;

import com.flux.suporte.IntegracaoBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** A V6 como a decisão 37 da 04b pede: FKs compostas, RESTRICT só na categoria, posição adiada, CHECKs e o índice da condição. */
class MigracaoDasRegrasIT extends IntegracaoBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void chavesEstrangeirasChecksEIndicesDaV6() {
        assertThat(jdbc.queryForList("select version || ':' || success from flyway_schema_history order by installed_rank",
                String.class)).contains("6:true");
        // confdeltype: r = RESTRICT, c = CASCADE.
        assertThat(jdbc.queryForList("select conname || ':' || confdeltype::text from pg_constraint where contype = 'f' "
                + "and conrelid = 'regras_categorizacao'::regclass order by conname", String.class))
                .containsExactly("fk_regras_categoria:r", "fk_regras_conta:c", "regras_categorizacao_usuario_id_fkey:c");
        assertThat(jdbc.queryForObject("select pg_get_constraintdef(oid) from pg_constraint where conname = 'fk_regras_categoria'",
                String.class)).isEqualTo("FOREIGN KEY (categoria_id, usuario_id, natureza) REFERENCES categorias(id, usuario_id, natureza) "
                + "ON DELETE RESTRICT");
        assertThat(jdbc.queryForObject("select condeferrable::text || ':' || condeferred::text from pg_constraint "
                + "where conname = 'uq_regras_posicao'", String.class)).isEqualTo("true:true");
        assertThat(jdbc.queryForList("select conname from pg_constraint where contype = 'c' and conrelid = 'regras_categorizacao'::regclass "
                + "order by conname", String.class))
                .containsExactly("ck_regras_condicao", "ck_regras_natureza", "ck_regras_posicao", "ck_regras_texto");
        assertThat(jdbc.queryForObject("select indexdef from pg_indexes where indexname = 'ux_regras_condicao'", String.class))
                .isEqualTo("CREATE UNIQUE INDEX ux_regras_condicao ON public.regras_categorizacao USING btree (usuario_id, natureza, "
                        + "condicao, texto_chave, conta_id) NULLS NOT DISTINCT");
        assertThat(jdbc.queryForList("select indexname from pg_indexes where tablename = 'regras_categorizacao' order by indexname",
                String.class)).containsExactly("ix_regras_categoria", "ix_regras_conta", "regras_categorizacao_pkey", "uq_regras_posicao",
                "ux_regras_condicao");
    }
}
