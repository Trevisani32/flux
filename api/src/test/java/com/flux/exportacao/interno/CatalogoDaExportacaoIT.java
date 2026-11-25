package com.flux.exportacao.interno;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.flux.suporte.IntegracaoBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogoDaExportacaoIT extends IntegracaoBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void todaTabelaDoBancoEstaNoArquivoOuFora() {
        Set<String> tabelasDoBanco = new HashSet<>(jdbc.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = 'public' and table_type = 'BASE TABLE'
                """, String.class));
        Set<String> noArquivo = CatalogoDaExportacao.TABELAS.stream()
                .map(CatalogoDaExportacao.Tabela::sql)
                .collect(Collectors.toSet());
        Set<String> fora = CatalogoDaExportacao.TABELAS_FORA.keySet();

        assertThat(noArquivo).doesNotContainAnyElementsOf(fora);
        assertThat(uniao(noArquivo, fora)).containsExactlyInAnyOrderElementsOf(tabelasDoBanco);

        Set<String> comUsuarioId = new HashSet<>(jdbc.queryForList("""
                select distinct table_name
                from information_schema.columns
                where table_schema = 'public' and column_name = 'usuario_id'
                """, String.class));
        Set<String> comFkParaUsuarios = new HashSet<>(jdbc.queryForList("""
                select distinct tabela.relname
                from pg_constraint restricao
                join pg_class tabela on tabela.oid = restricao.conrelid
                join pg_class referenciada on referenciada.oid = restricao.confrelid
                join pg_namespace esquema on esquema.oid = tabela.relnamespace
                where restricao.contype = 'f' and esquema.nspname = 'public' and referenciada.relname = 'usuarios'
                """, String.class));

        assertThat(uniao(noArquivo, fora)).containsAll(comUsuarioId).containsAll(comFkParaUsuarios);
    }

    @Test
    void todaColunaVaiOuEstaEmForaComOTipoCerto() {
        for (CatalogoDaExportacao.Tabela tabela : CatalogoDaExportacao.TABELAS) {
            Map<String, String> colunasDoBanco = jdbc.query("""
                    select column_name, data_type
                    from information_schema.columns
                    where table_schema = 'public' and table_name = ?
                    """, resultado -> {
                Map<String, String> resultadoPorNome = new java.util.HashMap<>();
                while (resultado.next()) {
                    resultadoPorNome.put(resultado.getString(1), resultado.getString(2));
                }
                return resultadoPorNome;
            }, tabela.sql());
            Set<String> noArquivo = tabela.colunas().stream().map(CatalogoDaExportacao.Coluna::sql)
                    .collect(Collectors.toSet());

            assertThat(noArquivo).doesNotContainAnyElementsOf(tabela.fora());
            assertThat(uniao(noArquivo, tabela.fora())).containsExactlyInAnyOrderElementsOf(colunasDoBanco.keySet());
            assertThat(tabela.colunas().stream().map(CatalogoDaExportacao.Coluna::json).toList())
                    .doesNotHaveDuplicates();
            for (CatalogoDaExportacao.Coluna coluna : tabela.colunas()) {
                assertThat(colunasDoBanco.get(coluna.sql())).isIn(tiposDoBanco(coluna.tipo()));
            }
        }
        assertThat(CatalogoDaExportacao.FORA_DO_ARQUIVO).isNotBlank();
    }

    private static Set<String> uniao(Set<String> primeiro, Set<String> segundo) {
        Set<String> uniao = new HashSet<>(primeiro);
        uniao.addAll(segundo);
        return uniao;
    }

    private static Set<String> tiposDoBanco(CatalogoDaExportacao.Tipo tipo) {
        return switch (tipo) {
            case INTEIRO -> Set.of("bigint", "integer");
            case TEXTO -> Set.of("character varying");
            case DINHEIRO -> Set.of("numeric");
            case DATA, MES -> Set.of("date");
            case INSTANTE -> Set.of("timestamp with time zone");
            case BOOLEANO -> Set.of("boolean");
        };
    }
}
