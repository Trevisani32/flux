package com.flux.acesso.interno;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/** A V2 confirma o e-mail de quem já existia e troca a FK de substituido_por para SET NULL. */
class MigracaoContasDeUsuarioIT {

    @Test
    void usuariosExistentesViramConfirmados() {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            Flyway.configure().dataSource(dataSource).target("1").load().migrate();
            jdbc.update("insert into usuarios (nome, email, senha_hash, criado_em, atualizado_em) "
                    + "values ('Antigo', 'antigo@teste.local', 'hash', '2026-01-02T03:04:05Z', now())");
            Flyway.configure().dataSource(dataSource).load().migrate();

            assertThat(jdbc.queryForObject("select email_confirmado_em = criado_em from usuarios "
                    + "where email = 'antigo@teste.local'", Boolean.class)).isTrue();
            assertThat(jdbc.queryForObject("select confdeltype from pg_constraint "
                    + "where conname = 'tokens_renovacao_substituido_por_fkey'", String.class)).isEqualTo("n");
            assertThat(jdbc.queryForObject("select count(*) from information_schema.tables "
                    + "where table_name in ('tokens_email', 'event_publication')", Integer.class)).isEqualTo(2);
        }
    }
}
