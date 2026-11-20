package com.flux.suporte;

import java.util.List;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.flux.suporte.ApiDeTeste.conta;

/**
 * Apoio das integrações do cartão (Fatia 05a): contas, cartões, compras e estornos pela API, e a leitura das
 * parcelas e das faturas no banco.
 */
public abstract class CartoesBase extends IntegracaoBase {

    @Autowired
    protected JdbcTemplate jdbc;

    protected long novaConta(UsuarioDeTeste usuario, String nome) {
        return novaConta(usuario, nome, "CORRENTE");
    }

    protected long novaConta(UsuarioDeTeste usuario, String nome, String tipo) {
        return api.idCriado(api.post(usuario, "/api/contas", conta(nome, tipo, "0", "2025-01-01", "null")));
    }

    /** Corpo de POST/PUT /api/cartoes. {@code cor} vai como JSON cru ("\"AZUL\"" ou "null"). */
    protected static String cartao(String nome, String limite, int diaFechamento, int diaVencimento, long contaId, String cor) {
        return "{\"nome\":\"" + nome + "\",\"limite\":" + limite + ",\"diaFechamento\":" + diaFechamento + ",\"diaVencimento\":"
                + diaVencimento + ",\"contaPagamentoId\":" + contaId + ",\"cor\":" + cor + "}";
    }

    protected long novoCartao(UsuarioDeTeste usuario, String nome, int diaFechamento, int diaVencimento, long contaId) {
        return api.idCriado(api.post(usuario, "/api/cartoes", cartao(nome, "5000", diaFechamento, diaVencimento, contaId, "null")));
    }

    /** Corpo de POST/PUT /api/compras e de POST /api/compras/previa, sem observação. */
    protected static String compra(long cartaoId, String data, String descricao, String valor, int parcelas, int parcelaInicial,
                                   Long categoriaId) {
        return "{\"cartaoId\":" + cartaoId + ",\"data\":\"" + data + "\",\"descricao\":\"" + descricao + "\",\"valorTotal\":" + valor
                + ",\"parcelas\":" + parcelas + ",\"parcelaInicial\":" + parcelaInicial + ",\"categoriaId\":" + categoriaId
                + ",\"observacao\":null}";
    }

    protected long novaCompra(UsuarioDeTeste usuario, long cartaoId, String data, String descricao, String valor, int parcelas) {
        return api.idCriado(api.post(usuario, "/api/compras", compra(cartaoId, data, descricao, valor, parcelas, 1, null)));
    }

    /** Corpo de POST/PUT /api/estornos, sem observação. */
    protected static String estorno(long cartaoId, String data, String descricao, String valor, Long categoriaId) {
        return "{\"cartaoId\":" + cartaoId + ",\"data\":\"" + data + "\",\"descricao\":\"" + descricao + "\",\"valor\":" + valor
                + ",\"categoriaId\":" + categoriaId + ",\"observacao\":null}";
    }

    protected long idDaCategoria(UsuarioDeTeste usuario, String nome) {
        return jdbc.queryForObject("select id from categorias where usuario_id = ? and nome = ?", Long.class, usuario.id(), nome);
    }

    /** "número data valor mês-da-fatura" de cada parcela da compra, a primeira antes. */
    protected List<String> parcelas(long compraId) {
        return jdbc.queryForList("select l.numero_parcela || ' ' || l.data || ' ' || l.valor || ' ' || to_char(f.mes_referencia, 'YYYY-MM') "
                + "from lancamentos l join faturas f on f.id = l.fatura_id where l.compra_id = ? order by l.numero_parcela", String.class,
                compraId);
    }

    /** "mês fechamento vencimento ajustado" de cada fatura do cartão, em ordem. */
    protected List<String> faturas(long cartaoId) {
        return jdbc.queryForList("select to_char(mes_referencia, 'YYYY-MM') || ' ' || data_fechamento || ' ' || data_vencimento || ' ' "
                + "|| fechamento_ajustado from faturas where cartao_id = ? order by mes_referencia", String.class, cartaoId);
    }

    protected int contar(String tabela, UsuarioDeTeste usuario) {
        return jdbc.queryForObject("select count(*) from " + tabela + " where usuario_id = ?", Integer.class, usuario.id());
    }
}
