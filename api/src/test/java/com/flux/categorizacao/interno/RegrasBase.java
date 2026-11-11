package com.flux.categorizacao.interno;

import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.flux.suporte.ApiDeTeste.conta;
import static com.flux.suporte.ApiDeTeste.lancamento;

/**
 * Apoio das integrações das regras: contas, categorias padrão pelo nome, o corpo da regra e
 * lançamentos manuais (pela API) e importados (por SQL, com uma importação de mentira).
 */
abstract class RegrasBase extends IntegracaoBase {

    @Autowired
    protected JdbcTemplate jdbc;

    protected long novaConta(UsuarioDeTeste usuario, String nome) {
        return api.idCriado(api.post(usuario, "/api/contas", conta(nome, "CORRENTE", "0", "2026-10-01", "null")));
    }

    protected long idDaCategoria(UsuarioDeTeste usuario, String nome) {
        return jdbc.queryForObject("select id from categorias where usuario_id = ? and nome = ?", Long.class, usuario.id(), nome);
    }

    /** Corpo de POST/PUT /api/regras ({@code contaId} nulo = todas as contas; {@code descricao} nula = não troca). */
    protected static String regra(String condicao, String texto, Long contaId, Long categoriaId, String descricao) {
        return "{\"condicao\":" + (condicao == null ? "null" : "\"" + condicao + "\"") + ",\"texto\":"
                + (texto == null ? "null" : "\"" + texto + "\"") + ",\"contaId\":" + contaId + ",\"categoriaId\":" + categoriaId
                + ",\"descricao\":" + (descricao == null ? "null" : "\"" + descricao + "\"") + "}";
    }

    protected long criarRegra(UsuarioDeTeste usuario, String condicao, String texto, Long contaId, long categoriaId, String descricao) {
        return api.idCriado(api.post(usuario, "/api/regras", regra(condicao, texto, contaId, categoriaId, descricao)));
    }

    /** Lançamento manual em 05/11/2026, pela API. {@code valor} positivo; o tipo dá o sinal. */
    protected long manual(UsuarioDeTeste usuario, long contaId, String tipo, String valor, String descricao, Long categoriaId) {
        return api.idCriado(api.post(usuario, "/api/lancamentos", lancamento(tipo, valor, "2026-11-05", descricao, contaId, categoriaId)));
    }

    /**
     * Importado sem categoria, com a descrição do extrato igual à descrição, numa importação só dele
     * (por SQL: a importação de verdade tem as próprias integrações). {@code valor} com sinal.
     */
    protected long importado(UsuarioDeTeste usuario, long contaId, String data, String valor, String descricao) {
        Long importacao = jdbc.queryForObject("insert into importacoes (usuario_id, conta_id, formato, nome_arquivo, hash_arquivo, "
                + "data_inicio, data_fim, lidas, importadas, duplicadas, anteriores, fora, desmarcadas) values (?, ?, 'OFX', 'teste.ofx', "
                + "md5(random()::text) || md5(random()::text), ?::date, ?::date, 1, 1, 0, 0, 0, 0) returning id", Long.class,
                usuario.id(), contaId, data, data);
        return jdbc.queryForObject("insert into lancamentos (usuario_id, conta_id, data, descricao, descricao_chave, valor, tipo, origem, "
                + "importacao_id, chave_importacao, descricao_banco) values (?, ?, ?::date, ?, lower(?), ?::numeric, "
                + "case when ?::numeric > 0 then 'RECEITA' else 'DESPESA' end, 'IMPORTADO', ?, 'ID:' || ? || ':' || md5(random()::text), ?) "
                + "returning id", Long.class, usuario.id(), contaId, data, descricao, descricao, valor, valor, importacao, contaId, descricao);
    }

    protected Long categoriaDoLancamento(long id) {
        return jdbc.queryForObject("select categoria_id from lancamentos where id = ?", Long.class, id);
    }

    protected String descricaoDoLancamento(long id) {
        return jdbc.queryForObject("select descricao from lancamentos where id = ?", String.class, id);
    }
}
