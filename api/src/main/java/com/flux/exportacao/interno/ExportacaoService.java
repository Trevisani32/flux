package com.flux.exportacao.interno;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import javax.sql.DataSource;

import com.flux.acesso.ConferenciaDaSenhaAtual;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exportar meus dados (Fatia 06b, ADR 0021). A senha é conferida antes, numa transação curta do acesso, com o limite das
 * outras conferências. O arquivo é escrito numa transação só de leitura em REPEATABLE READ: todas as tabelas no mesmo
 * retrato do banco, e o cursor do Postgres, que só existe com o autocommit desligado, entrega as linhas aos poucos.
 */
@Service
public class ExportacaoService {

    /** Linhas que o Postgres manda por vez (o fetchSize do cursor). */
    static final int LINHAS_POR_VEZ = 500;

    private final ConferenciaDaSenhaAtual conferencia;
    private final JdbcClient jdbc;
    private final ObjectWriter escritor;
    private final Clock clock;

    public ExportacaoService(ConferenciaDaSenhaAtual conferencia, DataSource dataSource, JsonMapper jsonMapper, Clock clock) {
        this.conferencia = conferencia;
        JdbcTemplate comCursor = new JdbcTemplate(dataSource);
        comCursor.setFetchSize(LINHAS_POR_VEZ);
        this.jdbc = JdbcClient.create(comCursor);
        this.escritor = jsonMapper.writerWithDefaultPrettyPrinter();
        this.clock = clock;
    }

    /** 429 (o limite), 422 ("Senha atual incorreta.") ou 404 (usuário apagado), antes de a resposta começar. */
    public void conferirSenha(Long usuarioId, String senhaAtual) {
        conferencia.conferir(usuarioId, senhaAtual);
    }

    /** "flux-dados-2026-11-24.json" ou "flux-lancamentos-2026-11-24.csv", com o hoje de São Paulo. */
    public String nomeDoArquivo(Formato formato) {
        LocalDate hoje = LocalDate.now(clock);
        return formato == Formato.JSON ? "flux-dados-" + hoje + ".json" : "flux-lancamentos-" + hoje + ".csv";
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void escrever(Long usuarioId, Formato formato, OutputStream saida) throws IOException {
        if (formato == Formato.JSON) {
            ArquivoJson.escrever(jdbc, escritor, usuarioId, Instant.now(clock), saida);
        } else {
            ArquivoCsv.escrever(jdbc, usuarioId, saida);
        }
    }
}
