package com.flux.importacao.interno;

import java.util.List;

import com.flux.suporte.IntegracaoBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** As V5 e V10: a importação é de uma conta ou de uma fatura de cartão, com FKs compostas e os CHECKs do modelo. */
class MigracaoDaImportacaoIT extends IntegracaoBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void chavesEstrangeirasChecksEIndicesDasV5EV10() {
        assertThat(jdbc.queryForList("select version || ':' || success from flyway_schema_history order by installed_rank",
                String.class)).contains("5:true");
        // confdeltype: r = RESTRICT, c = CASCADE.
        assertThat(jdbc.queryForList("select conname || ':' || confdeltype::text from pg_constraint where contype = 'f' "
                + "and conrelid in ('importacoes'::regclass, 'perfis_csv'::regclass) order by conname", String.class))
                .containsExactly("fk_importacoes_cartao:c", "fk_importacoes_conta:c", "fk_importacoes_fatura:a",
                        "fk_perfis_csv_cartao:c", "fk_perfis_csv_conta:c", "importacoes_usuario_id_fkey:c",
                        "perfis_csv_usuario_id_fkey:c");
        assertThat(jdbc.queryForObject("select pg_get_constraintdef(oid) from pg_constraint where conname = 'fk_lancamentos_importacao'",
                String.class)).isEqualTo("FOREIGN KEY (importacao_id, conta_id, usuario_id) REFERENCES importacoes(id, conta_id, usuario_id) "
                + "ON DELETE RESTRICT");
        List<String> checks = jdbc.queryForList("select conname from pg_constraint where contype = 'c' and conrelid in "
                + "('importacoes'::regclass, 'perfis_csv'::regclass, 'lancamentos'::regclass) order by conname", String.class);
        assertThat(checks).contains("ck_importacoes_quantidades", "ck_importacoes_extrato", "ck_importacoes_conta_ou_cartao",
                "ck_importacoes_fatura", "ck_lancamentos_importado", "ck_lancamentos_ignorado",
                "ck_lancamentos_ignorado_em_conta", "ck_perfis_csv_colunas", "ck_perfis_csv_formato_data",
                "ck_perfis_csv_conta_ou_cartao", "ck_perfis_csv_compras_positivas");
        assertThat(jdbc.queryForObject("select indexdef from pg_indexes where indexname = 'ux_lancamentos_chave_importacao'", String.class))
                .isEqualTo("CREATE UNIQUE INDEX ux_lancamentos_chave_importacao ON public.lancamentos USING btree (usuario_id, "
                        + "chave_importacao) WHERE (chave_importacao IS NOT NULL)");
        assertThat(jdbc.queryForList("select indexname from pg_indexes where tablename = 'importacoes' order by indexname", String.class))
                .containsExactly("importacoes_pkey", "ix_importacoes_cartao", "ix_importacoes_conta", "ix_importacoes_fatura",
                        "ix_importacoes_hash", "ix_importacoes_usuario", "uq_importacoes_id_cartao_usuario",
                        "uq_importacoes_id_conta_usuario");
    }
}
