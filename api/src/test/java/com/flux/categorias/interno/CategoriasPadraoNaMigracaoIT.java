package com.flux.categorias.interno;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.flux.compartilhado.texto.TextoComparavel;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A V3 dá as categorias padrão a quem já existia antes dela, igual à lista do Java que
 * os usuários novos recebem pelo evento, e cria as restrições de isolamento e de nome.
 */
class CategoriasPadraoNaMigracaoIT {

    @Test
    void usuariosQueJaExistiamRecebemAMesmaArvoreDaListaDoJava() {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            Flyway.configure().dataSource(dataSource).target("2").load().migrate();
            jdbc.update("insert into usuarios (nome, email, senha_hash, criado_em, atualizado_em, email_confirmado_em) "
                    + "values ('Confirmado', 'confirmado@teste.local', 'hash', now(), now(), now())");
            jdbc.update("insert into usuarios (nome, email, senha_hash, criado_em, atualizado_em) "
                    + "values ('Sem confirmar', 'sem-confirmar@teste.local', 'hash', now(), now())");
            Flyway.configure().dataSource(dataSource).load().migrate();

            assertThat(jdbc.queryForList("select version || ':' || success from flyway_schema_history "
                    + "order by installed_rank", String.class)).containsExactly("1:true", "2:true", "3:true");
            for (String email : List.of("confirmado@teste.local", "sem-confirmar@teste.local")) {
                Long usuarioId = jdbc.queryForObject("select id from usuarios where email = ?", Long.class, email);
                assertThat(arvoreNoBanco(jdbc, usuarioId)).as(email).isEqualTo(arvoreDaLista());
                assertThat(jdbc.queryForList("select nome, nome_chave from categorias where usuario_id = ?", usuarioId))
                        .allSatisfy(linha -> assertThat(linha.get("nome_chave"))
                                .isEqualTo(TextoComparavel.chave((String) linha.get("nome"))));
                assertThat(jdbc.queryForObject("select count(*) from categorias where usuario_id = ? "
                        + "and (arquivada_em is not null or arquivada_com_a_mae)", Integer.class, usuarioId)).isZero();
            }

            assertThat(jdbc.queryForObject("select indexdef from pg_indexes where indexname = 'ux_categorias_nome_ativa'",
                    String.class)).contains("NULLS NOT DISTINCT").contains("WHERE (arquivada_em IS NULL)");
            assertThat(jdbc.queryForObject("select indexdef from pg_indexes where indexname = 'ux_contas_nome_ativa'",
                    String.class)).contains("(usuario_id, nome_chave)").contains("WHERE (arquivada_em IS NULL)");
            assertThat(jdbc.queryForList("select conname || ':' || confdeltype::text from pg_constraint "
                    + "where contype = 'f' and conrelid in ('contas'::regclass, 'categorias'::regclass) order by conname",
                    String.class)).containsExactly("categorias_usuario_id_fkey:c", "contas_usuario_id_fkey:c",
                    "fk_categorias_mae:c");
        }
    }

    /** "NATUREZA/Principal" e "NATUREZA/Principal/Subcategoria", na ordem de inserção. */
    private static List<String> arvoreDaLista() {
        List<String> arvore = new ArrayList<>();
        for (CategoriasPadrao.Padrao padrao : CategoriasPadrao.LISTA) {
            arvore.add(padrao.natureza() + "/" + padrao.nome());
            padrao.subcategorias().forEach(sub -> arvore.add(padrao.natureza() + "/" + padrao.nome() + "/" + sub));
        }
        return arvore.stream().sorted().toList();
    }

    private static List<String> arvoreNoBanco(JdbcTemplate jdbc, Long usuarioId) {
        List<Map<String, Object>> linhas = jdbc.queryForList("select c.natureza, c.nome, m.nome as mae "
                + "from categorias c left join categorias m on m.id = c.categoria_mae_id where c.usuario_id = ?", usuarioId);
        return linhas.stream()
                .map(linha -> linha.get("natureza") + "/" + (linha.get("mae") == null ? "" : linha.get("mae") + "/")
                        + linha.get("nome"))
                .sorted().toList();
    }
}
