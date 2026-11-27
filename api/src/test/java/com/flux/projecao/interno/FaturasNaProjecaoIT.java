package com.flux.projecao.interno;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static com.flux.suporte.ApiDeTeste.conta;
import static com.flux.suporte.ApiDeTeste.categoria;
import static org.assertj.core.api.Assertions.assertThat;

class FaturasNaProjecaoIT extends ProjecaoBase {

    @Test
    void oAjusteDeFechamentoMudaAFaturaDaPrevista() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        ExemploDaProjecao exemplo = montarAProjecao(usuario);

        assertThat(api.put(usuario, "/api/cartoes/{id}/faturas/{mes}/fechamento", "{\"data\":\"2026-12-01\"}",
                exemplo.exemplo().cartaoHorizonte(), "2026-12")).hasStatus(200);
        JsonNode dezembro = projecao(usuario, "?contas=" + exemplo.exemplo().bancoHorizonte());
        assertThat(itens(dezembro, "2026-12-10")).containsExactly("FATURA Cartão Horizonte 2026-12 -1113.69");
        JsonNode faturaDezembro = dia(dezembro, "2026-12-10").get("itens").get(0);
        assertThat(faturaDezembro.get("pendente").decimalValue()).isEqualByComparingTo("1113.69");
        assertThat(faturaDezembro.get("previsto").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(faturaDezembro.get("previstos")).isEmpty();
        assertResumo(dezembro.get("resumo"), "1240.35", "-508.70", "731.65", "-2397.95", "2026-12-04", "1507.36", 4,
                new Trecho("2026-12-01", "2026-12-04"));
        JsonNode janeiro = projecao(usuario, "?ate=2027-01-31&contas=" + exemplo.exemplo().bancoHorizonte());
        assertThat(itens(janeiro, "2027-01-05")).containsExactly("PREVISTO Salário Fictício 8200.00",
                "FATURA Cartão Lua 2027-01 -55.90");
        JsonNode lua = dia(janeiro, "2027-01-05").get("itens").get(1);
        assertThat(lua.get("pendente").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(lua.get("previsto").decimalValue()).isEqualByComparingTo("55.90");
        assertThat(lua.get("previstos").valueStream().map(item -> item.get("descricao").asString() + " " + item.get("data").asString()
                + " " + decimal(item.get("valor")))
                .toList()).containsExactly("Netflix Fictícia 2026-11-28 55.90");
        assertThat(itens(janeiro, "2027-01-10")).containsExactly("FATURA Cartão Horizonte 2027-01 -43.80");
        JsonNode horizonte = dia(janeiro, "2027-01-10").get("itens").get(0);
        assertThat(horizonte.get("pendente").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(horizonte.get("previsto").decimalValue()).isEqualByComparingTo("43.80");
        assertThat(horizonte.get("previstos").valueStream().map(item -> item.get("descricao").asString() + " " + item.get("data").asString()
                + " " + decimal(item.get("valor")))
                .toList()).containsExactly("Spotify Fictício 2026-12-02 21.90",
                "Spotify Fictício 2027-01-02 21.90");
    }

    @Test
    void oPagoAMaisAbsorveAPrevista() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        ExemploDaProjecao exemplo = montarAProjecao(usuario);

        assertThat(api.post(usuario, "/api/cartoes/{id}/faturas/{mes}/pagamentos", pagamento(exemplo.exemplo().bancoHorizonte(),
                "2026-11-25", "1130.00"), exemplo.exemplo().cartaoHorizonte(), "2026-12")).hasStatus(201);
        JsonNode primeiro = projecao(usuario, "?contas=" + exemplo.exemplo().bancoHorizonte());
        assertResumo(primeiro.get("resumo"), "110.35", "-508.70", "-398.35", "-3527.95", "2026-12-04", "1485.46", 10,
                new Trecho("2026-11-25", "2026-12-04"));
        assertThat(itens(primeiro, "2026-12-10")).containsExactly("FATURA Cartão Horizonte 2026-12 -5.59");
        JsonNode fatura = dia(primeiro, "2026-12-10").get("itens").get(0);
        assertThat(fatura.get("valor").decimalValue()).isEqualByComparingTo("-5.59");
        assertThat(fatura.get("pendente").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(fatura.get("previsto").decimalValue()).isEqualByComparingTo("21.90");
        assertThat(api.post(usuario, "/api/cartoes/{id}/faturas/{mes}/pagamentos", pagamento(exemplo.exemplo().bancoHorizonte(),
                "2026-11-25", "70.00"), exemplo.exemplo().cartaoHorizonte(), "2026-12")).hasStatus(201);
        JsonNode segundo = projecao(usuario, "?contas=" + exemplo.exemplo().bancoHorizonte());
        assertResumo(segundo.get("resumo"), "40.35", "-508.70", "-468.35", "-3597.95", "2026-12-04", "1421.05", 10,
                new Trecho("2026-11-25", "2026-12-04"));
        assertThat(segundo.get("dias").valueStream().map(item -> item.get("data").asString()).toList()).doesNotContain("2026-12-10");
    }

    @Test
    void pagarOuMarcarAFaturaVencidaTiraAPendencia() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuarioPago = usuariosDeTeste.criar();
        ExemploDaProjecao pago = montarAProjecao(usuarioPago);
        assertThat(api.post(usuarioPago, "/api/cartoes/{id}/faturas/{mes}/pagamentos", pagamento(pago.exemplo().bancoHorizonte(),
                "2026-11-25", "412.30"), pago.exemplo().cartaoLua(), "2026-11")).hasStatus(201);
        JsonNode depoisDoPagamento = projecao(usuarioPago, "?contas=" + pago.exemplo().bancoHorizonte());
        assertThat(depoisDoPagamento.get("pendencias").valueStream().map(item -> item.get("descricao").asString()).toList())
                .containsExactly("Conta de água Fictícia");
        assertResumo(depoisDoPagamento.get("resumo"), "828.05", "-96.40", "731.65", "-2397.95", "2026-12-04", "1485.46", 4,
                new Trecho("2026-12-01", "2026-12-04"));

        UsuarioDeTeste usuarioFora = usuariosDeTeste.criar();
        ExemploDaProjecao fora = montarAProjecao(usuarioFora);
        assertThat(api.put(usuarioFora, "/api/cartoes/{id}/faturas/{mes}/paga-fora", "", fora.exemplo().cartaoLua(), "2026-11"))
                .hasStatus(200);
        JsonNode depoisDaMarca = projecao(usuarioFora, "?contas=" + fora.exemplo().bancoHorizonte());
        assertThat(depoisDaMarca.get("pendencias").valueStream().map(item -> item.get("descricao").asString()).toList())
                .containsExactly("Conta de água Fictícia");
        assertResumo(depoisDaMarca.get("resumo"), "1240.35", "-96.40", "1143.95", "-1985.65", "2026-12-04", "1897.76", 4,
                new Trecho("2026-12-01", "2026-12-04"));
    }

    @Test
    void aPrevistaAtrasadaNoCartaoEntraNaFaturaDela() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        ExemploDaProjecao exemplo = montarAProjecao(usuario);
        long revista = api.idCriado(api.post(usuario, "/api/recorrencias", recorrencia("DESPESA", "Revista Fictícia", "15.00", false,
                null, null, exemplo.exemplo().cartaoHorizonte(), "MENSAL", 1, "2026-11-01", null)));
        jdbc.update("update recorrencias set ocorrencias_desde = '2026-11-01' where id = ?", revista);

        JsonNode resposta = projecao(usuario, "?contas=" + exemplo.exemplo().bancoHorizonte());
        assertThat(resposta.get("pendencias").valueStream().map(item -> item.get("tipo").asString() + " " + textoDoItem(item) + " "
                + decimal(item.get("valor"))).toList()).containsExactly("FATURA Cartão Lua 2026-11 -412.30",
                "FATURA Cartão Horizonte 2026-11 -15.00", "PREVISTO Conta de água Fictícia -96.40");
        JsonNode novembro = pendencia(resposta, 1);
        assertThat(novembro.get("vencimento").asString()).isEqualTo("2026-11-10");
        assertThat(novembro.get("pendente").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(novembro.get("previsto").decimalValue()).isEqualByComparingTo("15.00");
        assertThat(novembro.get("previstos").size()).isEqualTo(1);
        JsonNode prevista = novembro.get("previstos").get(0);
        assertThat(prevista.get("descricao").asString()).isEqualTo("Revista Fictícia");
        assertThat(prevista.get("periodo").asString()).isEqualTo("2026-11-01");
        assertThat(prevista.get("data").asString()).isEqualTo("2026-11-01");
        assertThat(prevista.get("valor").decimalValue()).isEqualByComparingTo("15.00");
        assertThat(prevista.get("ocorrencia").get("periodo").asString()).isEqualTo("2026-11-01");
        assertThat(prevista.get("ocorrencia").get("atrasada").asBoolean()).isTrue();
        assertThat(prevista.get("ocorrencia").get("cartaoNome").asString()).isEqualTo("Cartão Horizonte");
        assertResumo(resposta.get("resumo"), "1240.35", "-523.70", "716.65", "-2412.95", "2026-12-04", "1455.46", 4,
                new Trecho("2026-12-01", "2026-12-04"));
        assertThat(itens(resposta, "2026-12-10")).containsExactly("FATURA Cartão Horizonte 2026-12 -1150.59");
        JsonNode dezembro = dia(resposta, "2026-12-10").get("itens").get(0);
        assertThat(dezembro.get("pendente").decimalValue()).isEqualByComparingTo("1113.69");
        assertThat(dezembro.get("previsto").decimalValue()).isEqualByComparingTo("36.90");
        assertThat(dezembro.get("previstos").valueStream().map(item -> item.get("descricao").asString() + " " + item.get("data").asString()
                + " " + decimal(item.get("valor")))
                .toList()).containsExactly("Revista Fictícia 2026-12-01 15.00",
                "Spotify Fictício 2026-12-02 21.90");
        dezembro.get("previstos").valueStream().forEach(item -> assertThat(item.get("ocorrencia").isNull()).isTrue());
        assertThat(pular(usuario, revista, "2026-11-01")).hasStatus(200);
        JsonNode depois = projecao(usuario, "?contas=" + exemplo.exemplo().bancoHorizonte());
        assertThat(depois.get("pendencias").valueStream().map(item -> item.get("tipo").asString() + " " + textoDoItem(item) + " "
                + decimal(item.get("valor"))).toList()).containsExactly("FATURA Cartão Lua 2026-11 -412.30",
                "PREVISTO Conta de água Fictícia -96.40");
        assertResumo(depois.get("resumo"), "1240.35", "-508.70", "731.65", "-2397.95", "2026-12-04", "1470.46", 4,
                new Trecho("2026-12-01", "2026-12-04"));
    }

    @Test
    void contaDePagamentoArquivadaECartaoArquivado() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        ExemploDaProjecao exemplo = montarAProjecao(usuario);
        long antiga = api.idCriado(api.post(usuario, "/api/contas", conta("Conta antiga", "CORRENTE", "0", "2026-10-01", "null")));
        long sol = novoCartao(usuario, "Cartão Sol", 25, 5, antiga);
        novaCompra(usuario, sol, "2026-11-20", "Compra Sol 1", "300.00", 1);
        novaCompra(usuario, sol, "2026-12-20", "Compra Sol 2", "150.00", 1);
        long velho = novoCartao(usuario, "Cartão Velho", 25, 5, exemplo.exemplo().bancoHorizonte());
        novaCompra(usuario, velho, "2026-11-20", "Compra Velha", "100.00", 1);
        long clube = api.idCriado(api.post(usuario, "/api/recorrencias", recorrencia("DESPESA", "Clube do livro Fictício", "40.00",
                false, null, null, velho, "MENSAL", 28, "2026-11-25", null)));
        long lazer = idDaCategoria(usuario, "Lazer");
        long cinema = api.idCriado(api.post(usuario, "/api/recorrencias", recorrencia("DESPESA", "Cinema Fictício", "60.00", false,
                lazer, exemplo.exemplo().bancoHorizonte(), null, "MENSAL", 27, "2026-11-25", null)));
        assertThat(aConfirmar(usuario).get("ocorrencias").valueStream()
                .map(item -> item.get("descricao").asString()).toList()).containsExactly(
                "Conta de água Fictícia", "Internet Fictícia", "Cinema Fictício", "Diarista Fictícia", "Academia Fictícia",
                "Clube do livro Fictício", "Netflix Fictícia", "Conta de luz Fictícia", "Aluguel Fictício", "Condomínio Fictício",
                "Spotify Fictício");
        assertThat(api.acao(usuario, "/api/contas/{id}/arquivar", antiga)).hasStatus(200);
        assertThat(api.acao(usuario, "/api/cartoes/{id}/arquivar", velho)).hasStatus(200);
        assertThat(api.acao(usuario, "/api/categorias/{id}/arquivar", lazer)).hasStatus(200);
        assertThat(aConfirmar(usuario).get("ocorrencias").valueStream()
                .map(item -> item.get("descricao").asString()).toList()).containsExactly(
                "Conta de água Fictícia", "Internet Fictícia", "Diarista Fictícia", "Academia Fictícia", "Netflix Fictícia",
                "Conta de luz Fictícia", "Aluguel Fictício", "Condomínio Fictício", "Spotify Fictício");
        JsonNode recorrencias = json(api.get(usuario, "/api/recorrencias"));
        assertThat(recorrencias.get("pausadas").valueStream().map(item -> item.get("descricao").asString() + " "
                + item.get("motivoDaPausa").asString()).toList()).containsExactly("Cinema Fictício a categoria Lazer está arquivada.",
                "Clube do livro Fictício Cartão Velho está arquivado.", "Mensalidade do clube Fictícia Banco Antigo está arquivada.");
        JsonNode padrao = projecao(usuario, "");
        assertThat(padrao.get("faturasFora").valueStream().map(item -> item.get("cartaoNome").asString() + " "
                + item.get("contaNome").asString() + " " + item.get("meses").valueStream().map(JsonNode::asString).toList() + " "
                + decimal(item.get("valor"))).toList()).containsExactly("Cartão Sol Conta antiga [2026-12] 300.00");
        assertResumo(padrao.get("resumo"), "22102.25", "-508.70", "21593.55", "18463.95", "2026-12-04", "22207.36", 0);
        JsonNode janeiro = projecao(usuario, "?ate=2027-01-31");
        assertThat(janeiro.get("faturasFora").valueStream().map(item -> item.get("cartaoNome").asString() + " "
                + item.get("meses").valueStream().map(JsonNode::asString).toList() + " " + decimal(item.get("valor"))).toList())
                .containsExactly("Cartão Sol [2026-12, 2027-01] 450.00");
        JsonNode horizonte = projecao(usuario, "?contas=" + exemplo.exemplo().bancoHorizonte());
        assertResumo(horizonte.get("resumo"), "1240.35", "-508.70", "731.65", "-2397.95", "2026-12-04", "1485.46", 4,
                new Trecho("2026-12-01", "2026-12-04"));
        assertThat(dias(horizonte)).containsExactly(
                "2026-11-26 -119.90 611.75", "2026-11-27 -180.00 431.75", "2026-11-28 -99.90 331.85",
                "2026-11-30 -279.80 52.05", "2026-12-01 -2270.00 -2217.95", "2026-12-04 -180.00 -2397.95",
                "2026-12-05 6915.10 4517.15", "2026-12-06 -500.00 4017.15", "2026-12-10 -1135.59 2881.56",
                "2026-12-11 -180.00 2701.56", "2026-12-15 -350.00 2351.56", "2026-12-18 -180.00 2171.56",
                "2026-12-20 -96.40 2075.16", "2026-12-25 -180.00 1895.16", "2026-12-26 -119.90 1775.26",
                "2026-12-28 -99.90 1675.36", "2026-12-30 -189.90 1485.46");
        assertThat(clube).isPositive();
        assertThat(cinema).isPositive();
    }

    private static String pagamento(long contaId, String data, String valor) {
        return "{\"contaId\":" + contaId + ",\"data\":\"" + data + "\",\"valor\":" + valor + ",\"observacao\":null}";
    }
}
