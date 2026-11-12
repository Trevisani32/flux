package com.flux.lancamentos.interno;

import java.util.List;

import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.flux.suporte.ApiDeTeste.conta;
import static com.flux.suporte.ApiDeTeste.lancamento;

/**
 * Apoio das integrações da conversão (Fatia 04c): contas, lançamentos manuais (pela API) e importados
 * (por SQL, cada um numa importação de mentira, como nas regras), o pedido de conversão e a leitura
 * dos lados no banco.
 */
abstract class ConversaoBase extends IntegracaoBase {

    @Autowired
    protected JdbcTemplate jdbc;

    protected long novaConta(UsuarioDeTeste usuario, String nome) {
        return novaConta(usuario, nome, "2026-10-01");
    }

    protected long novaConta(UsuarioDeTeste usuario, String nome, String dataSaldoInicial) {
        return api.idCriado(api.post(usuario, "/api/contas", conta(nome, "CORRENTE", "0", dataSaldoInicial, "null")));
    }

    protected long idDaCategoria(UsuarioDeTeste usuario, String nome) {
        return jdbc.queryForObject("select id from categorias where usuario_id = ? and nome = ?", Long.class, usuario.id(), nome);
    }

    /** Lançamento manual pela API. {@code valor} positivo; o tipo dá o sinal. */
    protected long manual(UsuarioDeTeste usuario, long contaId, String tipo, String valor, String data, String descricao) {
        return api.idCriado(api.post(usuario, "/api/lancamentos", lancamento(tipo, valor, data, descricao, contaId, null)));
    }

    /**
     * Importado sem categoria, com a descrição do extrato igual à descrição, numa importação só dele (por
     * SQL: a importação de verdade tem as próprias integrações). {@code valor} com sinal.
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

    /** Corpo de POST /api/lancamentos/{id}/converter. */
    protected static String conversao(Long parId, Long contaId, String descricao) {
        return "{\"parId\":" + parId + ",\"contaId\":" + contaId + ",\"descricao\":"
                + (descricao == null ? "null" : "\"" + descricao + "\"") + "}";
    }

    protected MvcTestResult converter(UsuarioDeTeste usuario, long id, Long parId, Long contaId, String descricao) {
        return api.post(usuario, "/api/lancamentos/{id}/converter", conversao(parId, contaId, descricao), id);
    }

    /** Converte e devolve o id da transferência. */
    protected long converterComPar(UsuarioDeTeste usuario, long id, long parId, String descricao) {
        return api.idCriado(converter(usuario, id, parId, null, descricao));
    }

    /** "conta tipo valor data descrição categoria" de cada lado, a saída primeiro. */
    protected List<String> lados(long transferenciaId) {
        return jdbc.queryForList("select c.nome || ' ' || l.tipo || ' ' || l.valor || ' ' || l.data || ' ' || l.descricao || ' ' "
                + "|| coalesce(l.categoria_id::text, 'sem') from lancamentos l join contas c on c.id = l.conta_id "
                + "where l.transferencia_id = ? order by l.valor", String.class, transferenciaId);
    }

    /** "tipo valor data descrição categoria transferência" de um lançamento. */
    protected String lancamentoNoBanco(long id) {
        return jdbc.queryForObject("select l.tipo || ' ' || l.valor || ' ' || l.data || ' ' || l.descricao || ' ' "
                + "|| coalesce(l.categoria_id::text, 'sem') || ' ' || coalesce(l.transferencia_id::text, 'solto') from lancamentos l "
                + "where l.id = ?", String.class, id);
    }

    protected int contar(String tabela, UsuarioDeTeste usuario) {
        return jdbc.queryForObject("select count(*) from " + tabela + " where usuario_id = ?", Integer.class, usuario.id());
    }
}