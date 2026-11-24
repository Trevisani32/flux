package com.flux.importacao.interno;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A V10 (Fatia 05c, decisões 43, 44 e 47): o banco recusa a importação com conta e cartão (ou sem nenhum), a de cartão
 * sem a fatura ou com a fatura de outro cartão, o importado de fatura ligado a uma importação de conta ou inexistente, o
 * lançamento de fatura excluído (ignorado), a compra importada ligada a uma importação de conta, a regra com conta e
 * cartão e o perfil com conta e cartão ou com "compras vêm positivas" na conta.
 */
class MigracaoDaImportacaoDeFaturaIT extends FaturaBase {

    @Test
    void oBancoGuardaAImportacaoNumaContaOuNumaFaturaDoCartao() {
        assertThat(jdbc.queryForList("select version || ':' || success from flyway_schema_history order by installed_rank",
                String.class)).contains("10:true");
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, conta);
        long lua = novoCartao(usuario, "Cartão Lua", 25, 5, conta);
        perfilDoNubank(usuario, horizonte);
        long importacao = importarNoCartao(usuario, horizonte, "fatura.csv",
                csvDoNubank(linha(dia(m().minusMonths(1), 9), "Padaria Pao Quente", "22.50")), 2);
        novaCompra(usuario, lua, dia(m(), 1), "Loja da Lua", "10", 1);
        long faturaDaLua = jdbc.queryForObject("select id from faturas where cartao_id = ?", Long.class, lua);
        long parcela = jdbc.queryForObject("select id from lancamentos where importacao_id = ?", Long.class, importacao);
        long compra = jdbc.queryForObject("select compra_id from lancamentos where id = ?", Long.class, parcela);
        long mercado = idDaCategoria(usuario, "Mercado");

        recusa("ck_importacoes_conta_ou_cartao", novaImportacao(usuario, conta + "", horizonte + "", "null"));
        recusa("ck_importacoes_conta_ou_cartao", novaImportacao(usuario, "null", "null", "null"));
        recusa("ck_importacoes_fatura", novaImportacao(usuario, "null", horizonte + "", "null"));
        recusa("fk_importacoes_fatura", novaImportacao(usuario, "null", horizonte + "", faturaDaLua + ""));
        long daConta = jdbc.queryForObject(novaImportacao(usuario, conta + "", "null", "null") + " returning id", Long.class);
        recusa("fk_lancamentos_importacao_do_cartao", "update lancamentos set importacao_id = " + daConta + " where id = " + parcela);
        recusa("fk_lancamentos_importacao_do_cartao", "update lancamentos set importacao_id = 999999999 where id = " + parcela);
        recusa("ck_lancamentos_ignorado_em_conta", "update lancamentos set ignorado_em = now(), categoria_id = null where id = " + parcela);
        recusa("fk_compras_importacao", "update compras set importacao_id = " + daConta + " where id = " + compra);
        recusa("ck_regras_conta_ou_cartao", "insert into regras_categorizacao (usuario_id, posicao, condicao, texto, texto_chave, conta_id, "
                + "cartao_id, natureza, categoria_id) values (" + usuario.id() + ", 1, 'CONTEM', 'x y', 'x y', " + conta + ", " + horizonte
                + ", 'DESPESA', " + mercado + ")");
        recusa("ck_perfis_csv_conta_ou_cartao", "update perfis_csv set conta_id = " + conta + " where cartao_id = " + horizonte);
        recusa("ck_perfis_csv_compras_positivas", "insert into perfis_csv (usuario_id, conta_id, separador, linhas_de_cabecalho, coluna_data, "
                + "formato_data, coluna_descricao, coluna_valor, separador_decimal, compras_positivas) values (" + usuario.id() + ", " + conta
                + ", 'VIRGULA', 1, 1, 'AAAA_MM_DD', 2, 3, 'PONTO', true)");
        // confdeltype: r = RESTRICT, c = CASCADE, a = NO ACTION.
        assertThat(jdbc.queryForList("select conname || ':' || confdeltype::text from pg_constraint where contype = 'f' "
                + "and conname in ('fk_importacoes_cartao', 'fk_importacoes_fatura', 'fk_lancamentos_importacao_do_cartao', "
                + "'fk_compras_importacao', 'fk_perfis_csv_cartao', 'fk_regras_cartao') order by conname", String.class))
                .containsExactly("fk_compras_importacao:r", "fk_importacoes_cartao:c", "fk_importacoes_fatura:a",
                        "fk_lancamentos_importacao_do_cartao:r", "fk_perfis_csv_cartao:c", "fk_regras_cartao:c");
    }

    /** O insert de uma importação com a conta, o cartão e a fatura dados (ids ou "null"). */
    private static String novaImportacao(UsuarioDeTeste usuario, String conta, String cartao, String fatura) {
        return "insert into importacoes (usuario_id, conta_id, cartao_id, fatura_id, formato, nome_arquivo, hash_arquivo, data_inicio, "
                + "data_fim, lidas, importadas, duplicadas, anteriores, fora, desmarcadas) values (" + usuario.id() + ", " + conta + ", "
                + cartao + ", " + fatura + ", 'CSV', 'x.csv', repeat('a', 64), current_date, current_date, 1, 1, 0, 0, 0, 0)";
    }

    private void recusa(String restricao, String sql) {
        assertThatThrownBy(() -> jdbc.update(sql)).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining(restricao);
    }
}
