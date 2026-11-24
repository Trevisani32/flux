package com.flux.importacao.interno;

import java.time.YearMonth;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fatia 05c (decisão 40): a compra importada fica na fatura escolhida. Ela só muda a descrição, a categoria e a
 * observação; excluída, sai de verdade, com as parcelas; e o ajuste de fechamento e a troca de dias não a movem.
 */
class CompraImportadaIT extends FaturaBase {

    @Test
    void aCompraImportadaSoMudaOsTextosEExcluidaSaiDeVerdade() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, novaConta(usuario, "Banco Horizonte"));
        perfilDoNubank(usuario, horizonte);
        String data = dia(m().minusMonths(4), 20);
        byte[] fatura = csvDoNubank(linha(dia(m().minusMonths(1), 9), "Padaria Pao Quente", "22.50"),
                linha(data, "Curso Online Ficticio - Parcela 3/10", "120.00"));
        importarNoCartao(usuario, horizonte, "fatura.csv", fatura, 2, 3);
        long curso = jdbc.queryForObject("select id from compras where usuario_id = ? and parcelas = 10", Long.class, usuario.id());
        long padaria = jdbc.queryForObject("select id from compras where usuario_id = ? and parcelas = 1", Long.class, usuario.id());
        long educacao = idDaCategoria(usuario, "Educação");

        MvcTestResult aberta = api.get(usuario, "/api/compras/{id}", curso);
        assertThat(aberta).hasStatusOk().bodyJson().extractingPath("importada").isEqualTo(true);
        assertThat(aberta).bodyJson().extractingPath("parcelaInicial").isEqualTo(3);
        assertThat(aberta).bodyJson().extractingPath("valorTotal").isEqualTo(1200.0);

        MvcTestResult outroValor = api.put(usuario, "/api/compras/{id}", compra(horizonte, data, "Curso Online Ficticio", "1300", 10, 3, null),
                curso);
        assertThat(outroValor).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("IMPORTADA");
        assertThat(outroValor).bodyJson().extractingPath("detail")
                .isEqualTo("Esta compra veio de uma fatura importada: só a descrição, a categoria e a observação mudam.");
        MvcTestResult outraData = api.put(usuario, "/api/compras/{id}", compra(horizonte, dia(m().minusMonths(4), 21), "Curso Online Ficticio",
                "1200", 10, 3, null), curso);
        assertThat(outraData).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("IMPORTADA");

        assertThat(api.put(usuario, "/api/compras/{id}", compra(horizonte, data, "Curso de inglês", "1200", 10, 3, educacao), curso))
                .hasStatusOk();
        assertThat(jdbc.queryForList("select distinct descricao || '|' || descricao_banco || '|' || categoria_id from lancamentos "
                + "where compra_id = ?", String.class, curso)).containsExactly("Curso de inglês|Curso Online Ficticio|" + educacao);
        JsonNode item = api.json(api.get(usuario, "/api/importacoes")).get(0);
        assertThat(item.get("editados").asInt()).isEqualTo(1);

        assertThat(api.delete(usuario, "/api/compras/{id}", padaria)).hasStatus(204);
        assertThat(jdbc.queryForObject("select count(*) from lancamentos where compra_id = ?", Integer.class, padaria)).isZero();
        JsonNode depois = api.json(api.get(usuario, "/api/importacoes")).get(0);
        assertThat(depois.get("excluidos").asInt()).isEqualTo(1);
        assertThat(depois.get("importadas").asInt()).isEqualTo(2);
        // Excluída de verdade, sem ficar ignorada: reimportar o arquivo traz a padaria de volta como nova.
        JsonNode denovo = api.json(previaNoCartao(usuario, horizonte, null, "fatura.csv", fatura));
        assertThat(denovo.get("linhas").valueStream().map(linha -> linha.get("linha").asInt() + ":" + linha.get("situacao").asString())
                .sorted().toList()).containsExactly("2:NOVA", "3:JA_IMPORTADA");
    }

    @Test
    void oAjusteEATrocaDeDiasNaoMovemACompraImportada() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, conta);
        perfilDoNubank(usuario, horizonte);
        YearMonth mes = YearMonth.from(hoje()).plusMonths(2);
        // A compra do dia 4 de M+2 cai na fatura de M+3 pelas datas; escolhida a de M+4 (a seguinte), ela fica lá.
        byte[] fatura = csvDoNubank(linha(dia(mes, 4), "Livraria Pagina", "59.90"));
        String hash = api.json(previaNoCartao(usuario, horizonte, mes.plusMonths(2).toString(), "fatura.csv", fatura)).get("hash").asString();
        assertThat(confirmarNoCartao(usuario, horizonte, mes.plusMonths(2).toString(), "fatura.csv", fatura, hash, escolhas(2)))
                .hasStatus(201);
        long livraria = jdbc.queryForObject("select id from compras where usuario_id = ? and importacao_id is not null", Long.class,
                usuario.id());
        long manual = novaCompra(usuario, horizonte, dia(mes, 4), "Compra a mao", "10", 1);
        assertThat(parcelas(livraria)).containsExactly("1 " + dia(mes.plusMonths(2), 10) + " -59.90 " + mes.plusMonths(2));
        assertThat(parcelas(manual)).containsExactly("1 " + dia(mes.plusMonths(1), 10) + " -10.00 " + mes.plusMonths(1));

        // O ajuste da fatura de M+2 para fechar no dia 5 traz a compra lançada à mão do dia 4 para ela; a importada fica.
        MvcTestResult ajuste = api.put(usuario, "/api/cartoes/{id}/faturas/{mes}/fechamento", "{\"data\":\"" + dia(mes, 5) + "\"}",
                horizonte, mes.toString());
        assertThat(ajuste).hasStatusOk().bodyJson().extractingPath("comprasMovidas").isEqualTo(1);
        assertThat(parcelas(manual)).containsExactly("1 " + dia(mes, 10) + " -10.00 " + mes);
        assertThat(parcelas(livraria)).containsExactly("1 " + dia(mes.plusMonths(2), 10) + " -59.90 " + mes.plusMonths(2));

        // A troca de dias (fecha 1, vence 8) recalcula as faturas seguintes à aberta e recoloca a compra lançada à mão; a
        // importada continua na fatura dela, que passa a vencer no dia 8.
        assertThat(api.put(usuario, "/api/cartoes/{id}", cartao("Cartão Horizonte", "5000", 1, 8, conta, "null"), horizonte)).hasStatusOk();
        assertThat(parcelas(manual)).containsExactly("1 " + dia(mes.plusMonths(1), 8) + " -10.00 " + mes.plusMonths(1));
        assertThat(parcelas(livraria)).containsExactly("1 " + dia(mes.plusMonths(2), 8) + " -59.90 " + mes.plusMonths(2));
    }
}
