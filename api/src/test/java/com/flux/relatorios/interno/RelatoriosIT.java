package com.flux.relatorios.interno;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static com.flux.suporte.ApiDeTeste.conta;
import static com.flux.suporte.ApiDeTeste.lancamento;
import static org.assertj.core.api.Assertions.assertThat;

class RelatoriosIT extends RelatoriosBase {

    @Test
    void oMesBateComALista() {
        fixarOMesAtual();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        MesDeReferencia referencia = montarOMesDeReferencia(usuario);

        JsonNode relatorio = relatorio(usuario, mes(0));
        assertThat(relatorio.get("mes").asString()).isEqualTo(mes(0));
        assertThat(relatorio.get("primeiroMes").asString()).isEqualTo(mes(4));
        assertThat(relatorio.get("receitas").toString()).isEqualTo(
                "{\"valor\":3000.00,\"quantidade\":1,\"noCartao\":0,\"media\":3130.00,"
                        + "\"comparacao\":{\"tipo\":\"ABAIXO\",\"percentual\":4}}");
        assertThat(relatorio.get("despesas").toString()).isEqualTo(
                "{\"valor\":1395.50,\"quantidade\":5,\"noCartao\":1,\"media\":1083.33,"
                        + "\"comparacao\":{\"tipo\":\"ACIMA\",\"percentual\":29}}");
        assertThat(relatorio.get("resultado").toString()).isEqualTo("1604.50");

        assertTotaisDaLista(usuario, mes(0), "3000.00", "-1395.50", 1, 5);
        assertTotaisDaLista(usuario, mes(1), "3340.00", "-1200.00", 2, 2);
        assertTotaisDaLista(usuario, mes(2), "3050.00", "-1050.00", 2, 3);
        assertTotaisDaLista(usuario, mes(3), "3000.00", "-1000.00", 1, 1);
        assertThat(totalDaLista(usuario, "tipo=receita", "entradas")).isEqualTo("3000.00");
        assertThat(totalDaLista(usuario, "tipo=despesa", "saidas")).isEqualTo("-1395.50");
        assertThat(totalDaLista(usuario, "categoria=" + referencia.moradia(), "saidas")).isEqualTo("-1260.50");
        assertThat(totalDaLista(usuario, "categoria=" + referencia.aluguel(), "saidas")).isEqualTo("-1200.00");
        assertThat(totalDaLista(usuario, "categoria=sem&tipo=despesa", "saidas")).isEqualTo("-25.00");
        assertThat(totalDaLista(usuario, "categoria=" + referencia.lazer(), "saidas")).isEqualTo("-10.00");
    }

    @Test
    void oSaldoDeUmMesPassadoUsaOSaldoInicialEOArquivamentoNoFusoDeSaoPaulo() {
        LocalDate hoje = fixarOMesAtual();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        montarOMesDeReferencia(usuario);

        List<String> saldos = new ArrayList<>();
        relatorio(usuario, mes(0)).get("meses").forEach(item -> saldos.add(
                item.get("mes").asString() + " " + item.get("saldo").toString()));
        assertThat(saldos).containsExactly(
                mes(4) + " 6000.00",
                mes(3) + " 8200.00",
                mes(2) + " 9950.00",
                mes(1) + " 12150.00",
                mes(0) + " 13864.50");
        assertThat(relatorio(usuario, mes(3)).get("saldo").get("total").toString()).isEqualTo("8200.00");
        assertThat(relatorio(usuario, mes(2)).get("saldo").toString()).isEqualTo(
                "{\"data\":\"" + m.minusMonths(2).atEndOfMonth() + "\",\"hoje\":false,\"total\":9950.00,"
                        + "\"investimentos\":5000.00,\"contas\":2,\"contasDeInvestimento\":1}");
        assertThat(relatorio(usuario, mes(0)).get("saldo").toString()).isEqualTo(
                "{\"data\":\"" + hoje + "\",\"hoje\":true,\"total\":13864.50,\"investimentos\":5300.00,"
                        + "\"contas\":2,\"contasDeInvestimento\":1}");
        assertThat(json(api.get(usuario, "/api/contas")).get("totais").get("geral").toString()).isEqualTo("13864.50");
    }

    @Test
    void aMediaUsaSoOsMesesComDadosEOPrimeiroMesNaoCompara() {
        fixarOMesAtual();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        montarOMesDeReferencia(usuario);

        JsonNode menosDois = relatorio(usuario, mes(2));
        assertThat(menosDois.get("mesesDaMedia").toString()).isEqualTo("[\"" + mes(4) + "\",\"" + mes(3) + "\"]");
        assertThat(menosDois.get("receitas").toString()).isEqualTo(
                "{\"valor\":3050.00,\"quantidade\":2,\"noCartao\":0,\"media\":1500.00,"
                        + "\"comparacao\":{\"tipo\":\"ACIMA\",\"percentual\":103}}");
        assertThat(menosDois.get("despesas").toString()).isEqualTo(
                "{\"valor\":1050.00,\"quantidade\":3,\"noCartao\":1,\"media\":500.00,"
                        + "\"comparacao\":{\"tipo\":\"ACIMA\",\"percentual\":110}}");
        JsonNode primeiro = relatorio(usuario, mes(4));
        assertThat(primeiro.get("mesesDaMedia").toString()).isEqualTo("[]");
        assertThat(primeiro.get("receitas").toString()).isEqualTo(
                "{\"valor\":0.00,\"quantidade\":0,\"noCartao\":0,\"media\":null,\"comparacao\":null}");
        assertThat(primeiro.get("despesas").toString()).isEqualTo(
                "{\"valor\":0.00,\"quantidade\":0,\"noCartao\":0,\"media\":null,\"comparacao\":null}");
        assertThat(primeiro.get("meses").size()).isEqualTo(1);
    }

    @Test
    void osGastosPorCategoriaComSubcategoriasDiretoSemCategoriaEArquivada() {
        fixarOMesAtual();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        montarOMesDeReferencia(usuario);

        assertThat(categorias(relatorio(usuario, mes(0)))).containsExactly(
                "Moradia false 1260.50 1016.67 {\"tipo\":\"ACIMA\",\"percentual\":24}",
                "  Aluguel false 1200.00 1000.00 {\"tipo\":\"ACIMA\",\"percentual\":20}",
                "  Direto em Moradia true 60.50 16.67 {\"tipo\":\"ACIMA\",\"percentual\":263}",
                "Alimentação false 100.00 66.67 {\"tipo\":\"ACIMA\",\"percentual\":50}",
                "  Restaurante false 100.00 66.67 {\"tipo\":\"ACIMA\",\"percentual\":50}",
                "Sem categoria false 25.00 0.00 {\"tipo\":\"NOVO\",\"percentual\":0}",
                "Lazer true 10.00 0.00 {\"tipo\":\"NOVO\",\"percentual\":0}");
        assertThat(categorias(relatorio(usuario, mes(2)))).containsExactly(
                "Moradia false 950.00 500.00 {\"tipo\":\"ACIMA\",\"percentual\":90}",
                "  Aluguel false 900.00 500.00 {\"tipo\":\"ACIMA\",\"percentual\":80}",
                "  Direto em Moradia true 50.00 0.00 {\"tipo\":\"NOVO\",\"percentual\":0}",
                "Alimentação false 100.00 0.00 {\"tipo\":\"NOVO\",\"percentual\":0}",
                "  Restaurante false 100.00 0.00 {\"tipo\":\"NOVO\",\"percentual\":0}");
    }

    @Test
    void oMesForaDaFaixaEOAtualEOMesInvalidoE400() {
        fixarOMesAtual();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        montarOMesDeReferencia(usuario);

        assertThat(relatorio(usuario, mes(5)).get("mes").asString()).isEqualTo(mes(0));
        assertThat(relatorio(usuario, m.plusMonths(1).toString()).get("mes").asString()).isEqualTo(mes(0));
        assertThat(api.get(usuario, "/api/relatorios/mes?mes=2026-13")).hasStatus(400);
        assertThat(mvc.get().uri("/api/relatorios/mes?mes=" + mes(0)).exchange()).hasStatus(401);
    }

    @Test
    void semContaOPrimeiroMesENuloEAntesDoPrimeiroSaldoInicialOSaldoTambem() {
        fixarOMesAtual();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        JsonNode vazio = relatorio(usuario);
        assertThat(vazio.get("primeiroMes").isNull()).isTrue();
        assertThat(vazio.get("meses").toString()).isEqualTo("[]");

        long conta = api.idCriado(api.post(usuario, "/api/contas", conta("Conta", "CORRENTE", "1000", d(1, 1), "null")));
        long cartao = api.idCriado(api.post(usuario, "/api/cartoes", cartao("Cartão", "1000", 5, 15, conta, "null")));
        assertThat(api.post(usuario, "/api/compras", compra(cartao, d(3, 1), "Antiga", "90", 3, 1, null))).hasStatus(201);

        JsonNode atual = relatorio(usuario, mes(0));
        assertThat(atual.get("primeiroMes").asString()).isEqualTo(mes(3));
        assertThat(saldosEDespesas(atual)).containsExactly(
                mes(3) + " null 30.00",
                mes(2) + " null 30.00",
                mes(1) + " 1000.00 30.00",
                mes(0) + " 1000.00 0.00");
        assertThat(relatorio(usuario, mes(3)).get("saldo").toString()).isEqualTo(
                "{\"data\":\"" + m.minusMonths(3).atEndOfMonth() + "\",\"hoje\":false,\"total\":null,"
                        + "\"investimentos\":null,\"contas\":0,\"contasDeInvestimento\":0}");
    }

    @Test
    void asDozeUltimasColunasEOsSaldosDoGrafico() {
        fixarOMesAtual();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = api.idCriado(api.post(usuario, "/api/contas", conta("Conta", "CORRENTE", "1000", d(14, 1), "null")));
        for (int mesesAtras = 14; mesesAtras >= 0; mesesAtras--) {
            assertThat(api.post(
                    usuario,
                    "/api/lancamentos",
                    lancamento("DESPESA", "10", d(mesesAtras, 5), "Mensal", conta, null))).hasStatus(201);
        }

        JsonNode atual = relatorio(usuario, mes(0));
        assertThat(atual.get("primeiroMes").asString()).isEqualTo(mes(14));
        assertThat(atual.get("meses").size()).isEqualTo(12);
        assertThat(atual.get("meses").get(0).get("saldo").toString()).isEqualTo("960.00");
        assertThat(atual.get("meses").get(11).get("saldo").toString()).isEqualTo("850.00");
        JsonNode menosTreze = relatorio(usuario, mes(13));
        assertThat(menosTreze.get("meses").get(0).get("saldo").toString()).isEqualTo("990.00");
        assertThat(menosTreze.get("meses").get(1).get("saldo").toString()).isEqualTo("980.00");
    }

    private void assertTotaisDaLista(
            UsuarioDeTeste usuario,
            String mes,
            String entradas,
            String saidas,
            int quantidadeEntradas,
            int quantidadeSaidas) {
        JsonNode totais = json(api.get(usuario, "/api/lancamentos?mes=" + mes)).get("totais");
        assertThat(totais.get("entradas").toString()).isEqualTo(entradas);
        assertThat(totais.get("saidas").toString()).isEqualTo(saidas);
        assertThat(totais.get("quantidadeEntradas").asInt()).isEqualTo(quantidadeEntradas);
        assertThat(totais.get("quantidadeSaidas").asInt()).isEqualTo(quantidadeSaidas);
    }

    private String totalDaLista(UsuarioDeTeste usuario, String filtro, String campo) {
        return json(api.get(usuario, "/api/lancamentos?mes=" + mes(0) + "&" + filtro))
                .get("totais")
                .get(campo)
                .toString();
    }

    private List<String> categorias(JsonNode relatorio) {
        List<String> linhas = new ArrayList<>();
        relatorio.get("categorias").forEach(categoria -> {
            linhas.add(categoria.get("nome").asString() + " " + categoria.get("arquivada") + " "
                    + categoria.get("valor") + " " + categoria.get("media") + " " + categoria.get("comparacao"));
            categoria.get("subcategorias").forEach(subcategoria -> linhas.add(
                    "  " + subcategoria.get("nome").asString() + " " + subcategoria.get("direto") + " "
                            + subcategoria.get("valor") + " " + subcategoria.get("media") + " "
                            + subcategoria.get("comparacao")));
        });
        return linhas;
    }

    private List<String> saldosEDespesas(JsonNode relatorio) {
        List<String> saldos = new ArrayList<>();
        relatorio.get("meses").forEach(item -> saldos.add(
                item.get("mes").asString() + " " + item.get("saldo") + " " + item.get("despesas")));
        return saldos;
    }
}
