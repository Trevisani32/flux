package com.flux.importacao.interno;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fatia 05c (decisão 44): a revisão da fatura sugere a categoria e a descrição legível como a da conta, pela regra do
 * cartão ou sem filtro (nunca pela de uma conta), olhando a descrição do extrato sem "Parcela k/N"; a confirmação grava o
 * que a revisão mostrou em todas as parcelas.
 */
class RegrasNaImportacaoDeFaturaIT extends FaturaBase {

    private static String regra(String texto, Long contaId, Long cartaoId, long categoriaId, String descricao) {
        return "{\"condicao\":\"CONTEM\",\"texto\":\"" + texto + "\",\"contaId\":" + contaId + ",\"cartaoId\":" + cartaoId
                + ",\"categoriaId\":" + categoriaId + ",\"descricao\":" + (descricao == null ? "null" : "\"" + descricao + "\"") + "}";
    }

    @Test
    void aRevisaoDaFaturaSugereARegraDoCartaoEAConfirmacaoGravaEmTodasAsParcelas() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, conta);
        perfilDoNubank(usuario, horizonte);
        long educacao = idDaCategoria(usuario, "Educação");
        long mercado = idDaCategoria(usuario, "Mercado");
        long padaria = idDaCategoria(usuario, "Restaurante");
        api.idCriado(api.post(usuario, "/api/regras", regra("padaria", conta, null, padaria, null)));
        api.idCriado(api.post(usuario, "/api/regras", regra("mercado", null, null, mercado, null)));
        api.idCriado(api.post(usuario, "/api/regras", regra("curso online", null, horizonte, educacao, "Curso de inglês")));
        byte[] fatura = csvDoNubank(linha(dia(m().minusMonths(1), 9), "Padaria Pao Quente", "22.50"),
                linha(dia(m().minusMonths(1), 12), "Mercado Bom Preco", "80.00"),
                linha(dia(m().minusMonths(4), 20), "Curso Online Ficticio - Parcela 3/10", "120.00"));

        JsonNode previa = api.json(previaNoCartao(usuario, horizonte, null, "fatura.csv", fatura));

        JsonNode linhas = previa.get("linhas");
        assertThat(linhas.valueStream().map(linha -> linha.get("linha").asInt() + ":" + (linha.get("sugestao").isNull() ? "-"
                : linha.get("sugestao").get("posicao").asInt() + ":" + linha.get("sugestao").get("descricao").asString(""))).toList())
                .containsExactly("3:2:", "2:-", "4:1:Curso de inglês");
        String hash = previa.get("hash").asString();
        String escolhas = "[{\"linha\":4,\"categoriaId\":" + educacao + ",\"descricao\":\"Curso de inglês\"},"
                + "{\"linha\":3,\"categoriaId\":" + mercado + ",\"descricao\":null}]";
        assertThat(confirmarNoCartao(usuario, horizonte, m().toString(), "fatura.csv", fatura, hash, escolhas)).hasStatus(201);

        long curso = jdbc.queryForObject("select id from compras where usuario_id = ? and parcelas = 10", Long.class, usuario.id());
        assertThat(jdbc.queryForList("select distinct descricao || '|' || descricao_banco || '|' || categoria_id from lancamentos "
                + "where compra_id = ?", String.class, curso)).containsExactly("Curso de inglês|Curso Online Ficticio|" + educacao);
        assertThat(jdbc.queryForObject("select count(*) from lancamentos where compra_id = ?", Integer.class, curso)).isEqualTo(8);
        assertThat(jdbc.queryForObject("select descricao || '|' || categoria_id from lancamentos where usuario_id = ? "
                + "and descricao_banco = 'Mercado Bom Preco'", String.class, usuario.id())).isEqualTo("Mercado Bom Preco|" + mercado);
    }
}
