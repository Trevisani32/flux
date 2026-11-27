package com.flux.projecao.interno;

import java.util.List;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static com.flux.suporte.ApiDeTeste.assertCampo;
import static com.flux.suporte.ApiDeTeste.conta;
import static org.assertj.core.api.Assertions.assertThat;

class ProjecaoIT extends ProjecaoBase {

    @Test
    void oBancoHorizonteSozinho() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        ExemploDaProjecao exemplo = montarAProjecao(usuario);

        JsonNode resposta = projecao(usuario, "?contas=" + exemplo.exemplo().bancoHorizonte());
        assertThat(resposta.get("hoje").asString()).isEqualTo("2026-11-25");
        assertThat(resposta.get("ate").asString()).isEqualTo("2026-12-31");
        assertThat(resposta.get("diasDepoisDeHoje").asInt()).isEqualTo(36);
        assertThat(resposta.get("periodos").get("padrao").asString()).isEqualTo("2026-12-31");
        assertThat(resposta.get("periodos").get("tresMeses").asString()).isEqualTo("2027-02-25");
        assertThat(resposta.get("periodos").get("seisMeses").asString()).isEqualTo("2027-05-25");
        assertThat(resposta.get("periodos").get("dozeMeses").asString()).isEqualTo("2027-11-25");
        assertThat(resposta.get("contas").valueStream().map(conta -> conta.get("nome").asString()).toList()).containsExactly(
                "Banco Aurora", "Banco Horizonte", "Carteira", "Conta conjunta", "Investimentos Lua", "Poupança Horizonte");
        assertThat(resposta.get("contas").valueStream().map(conta -> conta.get("tipo").asString() + " " + texto(conta, "cor") + " "
                + conta.get("padrao").asBoolean() + " " + conta.get("escolhida").asBoolean()).toList()).containsExactly(
                "CORRENTE LARANJA true false", "CORRENTE OURO true true", "CARTEIRA CINZA true false", "CORRENTE ROSA true false",
                "INVESTIMENTO ROXO false false", "POUPANCA AZUL true false");
        assertThat(resposta.get("escolhidas").valueStream().map(JsonNode::asLong).toList())
                .containsExactly(exemplo.exemplo().bancoHorizonte());
        assertResumo(resposta.get("resumo"), "1240.35", "-508.70", "731.65", "-2397.95", "2026-12-04", "1485.46", 4,
                new Trecho("2026-12-01", "2026-12-04"));
        assertThat(resposta.get("porConta").size()).isEqualTo(1);
        assertResumo(resposta.get("porConta").get(0), "1240.35", "-508.70", "731.65", "-2397.95", "2026-12-04", "1485.46", 4,
                new Trecho("2026-12-01", "2026-12-04"));
        assertThat(resposta.get("porConta").get(0).get("contaId").asLong()).isEqualTo(exemplo.exemplo().bancoHorizonte());
        assertThat(resposta.get("porConta").get(0).get("nome").asString()).isEqualTo("Banco Horizonte");
        assertThat(resposta.get("porConta").get(0).get("cor").asString()).isEqualTo("OURO");
        assertThat(resposta.get("pendencias").size()).isEqualTo(2);
        JsonNode fatura = pendencia(resposta, 0);
        assertThat(fatura.get("tipo").asString()).isEqualTo("FATURA");
        assertThat(fatura.get("cartaoNome").asString()).isEqualTo("Cartão Lua");
        assertThat(fatura.get("cartaoCor").asString()).isEqualTo("ROXO");
        assertThat(fatura.get("mes").asString()).isEqualTo("2026-11");
        assertThat(fatura.get("vencimento").asString()).isEqualTo("2026-11-05");
        assertThat(fatura.get("valor").decimalValue()).isEqualByComparingTo("-412.30");
        assertThat(fatura.get("pendente").decimalValue()).isEqualByComparingTo("412.30");
        assertThat(fatura.get("previsto").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(fatura.get("previstos")).isEmpty();
        assertThat(fatura.get("contaNome").asString()).isEqualTo("Banco Horizonte");
        JsonNode agua = pendencia(resposta, 1);
        assertThat(agua.get("tipo").asString()).isEqualTo("PREVISTO");
        assertThat(agua.get("descricao").asString()).isEqualTo("Conta de água Fictícia");
        assertThat(agua.get("data").asString()).isEqualTo("2026-11-20");
        assertThat(agua.get("valor").decimalValue()).isEqualByComparingTo("-96.40");
        assertThat(agua.get("valorVaria").asBoolean()).isTrue();
        assertThat(agua.get("ocorrencia").get("periodo").asString()).isEqualTo("2026-11-01");
        assertThat(agua.get("ocorrencia").get("atrasada").asBoolean()).isTrue();
        assertThat(agua.get("ocorrencia").get("visivelAte").asString()).isEqualTo("2027-01-19");
        assertThat(agua.get("ocorrencia").get("categoriaNome").asString()).isEqualTo("Contas da casa");
        assertThat(dias(resposta)).containsExactly(
                "2026-11-26 -119.90 611.75", "2026-11-27 -180.00 431.75", "2026-11-28 -99.90 331.85",
                "2026-11-30 -279.80 52.05", "2026-12-01 -2270.00 -2217.95", "2026-12-04 -180.00 -2397.95",
                "2026-12-05 6915.10 4517.15", "2026-12-06 -500.00 4017.15", "2026-12-10 -1135.59 2881.56",
                "2026-12-11 -180.00 2701.56", "2026-12-15 -350.00 2351.56", "2026-12-18 -180.00 2171.56",
                "2026-12-20 -96.40 2075.16", "2026-12-25 -180.00 1895.16", "2026-12-26 -119.90 1775.26",
                "2026-12-28 -99.90 1675.36", "2026-12-30 -189.90 1485.46");
        assertThat(itens(resposta, "2026-11-30")).containsExactly("PREVISTO Conta de luz Fictícia -189.90",
                "LANCAMENTO Seguro Fictício -89.90");
        assertThat(itens(resposta, "2026-12-01")).containsExactly("PREVISTO Aluguel Fictício -1850.00",
                "PREVISTO Condomínio Fictício -420.00");
        assertThat(itens(resposta, "2026-12-05")).containsExactly("PREVISTO Salário Fictício 8200.00",
                "FATURA Cartão Lua 2026-12 -1284.90");
        assertThat(itens(resposta, "2026-12-06")).containsExactly("TRANSFERENCIA Reserva do mês -500.00");
        assertThat(dia(resposta, "2026-12-06").get("itens").get(0).get("outraContaNome").asString()).isEqualTo("Poupança Horizonte");
        assertThat(itens(resposta, "2026-12-10")).containsExactly("FATURA Cartão Horizonte 2026-12 -1135.59");
        JsonNode horizonte = dia(resposta, "2026-12-10").get("itens").get(0);
        assertThat(horizonte.get("pendente").decimalValue()).isEqualByComparingTo("1113.69");
        assertThat(horizonte.get("previsto").decimalValue()).isEqualByComparingTo("21.90");
        assertThat(horizonte.get("previstos").size()).isEqualTo(1);
        JsonNode spotify = horizonte.get("previstos").get(0);
        assertThat(spotify.get("recorrenciaId").asLong()).isEqualTo(exemplo.exemplo().spotify());
        assertThat(spotify.get("periodo").asString()).isEqualTo("2026-12-01");
        assertThat(spotify.get("data").asString()).isEqualTo("2026-12-02");
        assertThat(spotify.get("descricao").asString()).isEqualTo("Spotify Fictício");
        assertThat(spotify.get("valor").decimalValue()).isEqualByComparingTo("21.90");
        assertThat(spotify.get("ocorrencia").isNull()).isTrue();
        resposta.get("dias").valueStream().flatMap(dia -> dia.get("itens").valueStream()).forEach(item ->
                assertThat(item.get("ocorrencia").isNull()).isTrue());
        assertThat(resposta.get("faturasFora")).isEmpty();
    }

    @Test
    void oPadraoTemAsCincoContasSemInvestimento() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        ExemploDaProjecao exemplo = montarAProjecao(usuario);

        JsonNode resposta = projecao(usuario, "");
        assertThat(resposta.get("escolhidas").valueStream().map(JsonNode::asLong).toList()).containsExactly(exemplo.exemplo().bancoAurora(),
                exemplo.exemplo().bancoHorizonte(), exemplo.carteira(), exemplo.conjunta(), exemplo.poupanca());
        assertResumo(resposta.get("resumo"), "22102.25", "-508.70", "21593.55", "18463.95", "2026-12-04", "22207.36", 0);
        assertThat(resposta.get("porConta").valueStream().map(conta -> conta.get("nome").asString()).toList()).containsExactly(
                "Banco Aurora", "Banco Horizonte", "Carteira", "Conta conjunta", "Poupança Horizonte");
        assertResumo(resposta.get("porConta").get(0), "4310.20", "0.00", "4310.20", "3670.20", "2026-12-10", "3670.20", 0);
        assertResumo(resposta.get("porConta").get(1), "1240.35", "-508.70", "731.65", "-2397.95", "2026-12-04", "1485.46", 4,
                new Trecho("2026-12-01", "2026-12-04"));
        assertResumo(resposta.get("porConta").get(2), "182.30", "0.00", "182.30", "182.30", "2026-11-25", "182.30", 0);
        assertResumo(resposta.get("porConta").get(3), "1449.00", "0.00", "1449.00", "1449.00", "2026-11-25", "1449.00", 0);
        assertResumo(resposta.get("porConta").get(4), "14920.40", "0.00", "14920.40", "14920.40", "2026-11-25", "15420.40", 0);
        assertThat(dias(resposta)).containsExactly(
                "2026-11-26 -119.90 21473.65", "2026-11-27 -180.00 21293.65", "2026-11-28 -99.90 21193.75",
                "2026-11-30 -279.80 20913.95", "2026-12-01 -2270.00 18643.95", "2026-12-04 -180.00 18463.95",
                "2026-12-05 6915.10 25379.05", "2026-12-10 -1775.59 23603.46", "2026-12-11 -180.00 23423.46",
                "2026-12-15 -350.00 23073.46", "2026-12-18 -180.00 22893.46", "2026-12-20 -96.40 22797.06",
                "2026-12-25 -180.00 22617.06", "2026-12-26 -119.90 22497.16", "2026-12-28 -99.90 22397.26",
                "2026-12-30 -189.90 22207.36");
        assertThat(itens(resposta, "2026-12-10")).containsExactly("PREVISTO Plano de saúde Fictício -640.00",
                "FATURA Cartão Horizonte 2026-12 -1135.59");
        assertThat(dia(resposta, "2026-12-10").get("itens").get(0).get("contaNome").asString()).isEqualTo("Banco Aurora");
    }

    @Test
    void aTransferenciaComAOutraContaForaEntraSoDeUmLado() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        ExemploDaProjecao exemplo = montarAProjecao(usuario);

        JsonNode resposta = projecao(usuario, "?contas=" + exemplo.poupanca());
        assertResumo(resposta.get("resumo"), "14920.40", "0.00", "14920.40", "14920.40", "2026-11-25", "15420.40", 0);
        assertThat(dias(resposta)).containsExactly("2026-12-06 500.00 15420.40");
        assertThat(itens(resposta, "2026-12-06")).containsExactly("TRANSFERENCIA Reserva do mês 500.00");
        JsonNode transferencia = dia(resposta, "2026-12-06").get("itens").get(0);
        assertThat(transferencia.get("contaNome").asString()).isEqualTo("Poupança Horizonte");
        assertThat(transferencia.get("outraContaNome").asString()).isEqualTo("Banco Horizonte");
    }

    @Test
    void investimentoSoEntraQuandoEscolhido() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        ExemploDaProjecao exemplo = montarAProjecao(usuario);

        JsonNode resposta = projecao(usuario, "?contas=" + exemplo.exemplo().bancoAurora() + "," + exemplo.exemplo().bancoHorizonte()
                + "," + exemplo.carteira() + "," + exemplo.conjunta() + "," + exemplo.investimentos() + "," + exemplo.poupanca());
        assertThat(resposta.get("escolhidas").valueStream().map(JsonNode::asLong).toList()).containsExactly(exemplo.exemplo().bancoAurora(),
                exemplo.exemplo().bancoHorizonte(), exemplo.carteira(), exemplo.conjunta(), exemplo.investimentos(), exemplo.poupanca());
        assertThat(resposta.get("contas").get(4).get("nome").asString()).isEqualTo("Investimentos Lua");
        assertThat(resposta.get("contas").get(4).get("padrao").asBoolean()).isFalse();
        assertThat(resposta.get("contas").get(4).get("escolhida").asBoolean()).isTrue();
        assertResumo(resposta.get("resumo"), "52102.25", "-508.70", "51593.55", "48463.95", "2026-12-04", "52207.36", 0);
    }

    @Test
    void aContaDePagamentoForaDaSelecaoTiraAFatura() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        ExemploDaProjecao exemplo = montarAProjecao(usuario);

        JsonNode resposta = projecao(usuario, "?contas=" + exemplo.exemplo().bancoAurora());
        assertResumo(resposta.get("resumo"), "4310.20", "0.00", "4310.20", "3670.20", "2026-12-10", "3670.20", 0);
        assertThat(resposta.get("pendencias")).isEmpty();
        assertThat(dias(resposta)).containsExactly("2026-12-10 -640.00 3670.20");
        assertThat(itens(resposta, "2026-12-10")).containsExactly("PREVISTO Plano de saúde Fictício -640.00");
    }

    @Test
    void dozeMesesTemDoisTrechosNegativos() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        ExemploDaProjecao exemplo = montarAProjecao(usuario);

        JsonNode resposta = projecao(usuario, "?ate=2027-11-25&contas=" + exemplo.exemplo().bancoHorizonte());
        assertThat(resposta.get("diasDepoisDeHoje").asInt()).isEqualTo(365);
        assertResumo(resposta.get("resumo"), "1240.35", "-508.70", "731.65", "-2397.95", "2026-12-04", "48902.26", 8,
                new Trecho("2026-12-01", "2026-12-04"), new Trecho("2027-01-01", "2027-01-04"));
        assertThat(resposta.get("dias").size()).isEqualTo(130);
        assertThat(dias(resposta).getLast()).isEqualTo("2027-11-20 -96.40 48902.26");
        assertThat(itens(resposta, "2027-01-01")).containsExactly("PREVISTO Aluguel Fictício -1850.00",
                "PREVISTO Condomínio Fictício -420.00", "PREVISTO Diarista Fictícia -180.00");
        assertThat(dias(resposta)).contains("2027-01-01 -2450.00 -964.54", "2027-01-05 8144.10 7179.56",
                "2027-01-15 -1770.00 5207.66");
        assertThat(itens(resposta, "2027-01-05")).containsExactly("PREVISTO Salário Fictício 8200.00",
                "FATURA Cartão Lua 2027-01 -55.90");
        JsonNode lua = dia(resposta, "2027-01-05").get("itens").get(1);
        assertThat(lua.get("cartaoNome").asString()).isEqualTo("Cartão Lua");
        assertThat(lua.get("mes").asString()).isEqualTo("2027-01");
        assertThat(lua.get("valor").decimalValue()).isEqualByComparingTo("-55.90");
        assertThat(lua.get("pendente").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(lua.get("previsto").decimalValue()).isEqualByComparingTo("55.90");
        assertThat(lua.get("previstos").valueStream().map(item -> item.get("descricao").asString() + " " + item.get("data").asString()
                + " " + decimal(item.get("valor"))).toList()).containsExactly("Netflix Fictícia 2026-11-28 55.90");
        assertThat(itens(resposta, "2027-01-15")).containsExactly("PREVISTO Curso de inglês Fictício -350.00",
                "PREVISTO Diarista Fictícia -180.00", "PREVISTO IPVA Fictício -1240.00");
    }

    @Test
    void asPendenciasDeHoje() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        ExemploDaProjecao exemplo = montarAProjecao(usuario);
        long feira = api.idCriado(api.post(usuario, "/api/recorrencias", recorrencia("DESPESA", "Feira Fictícia", "30.00", false,
                null, exemplo.exemplo().bancoHorizonte(), null, "MENSAL", 25, "2026-11-25", null)));
        long cartaoHoje = novoCartao(usuario, "Cartão Hoje", 15, 25, exemplo.exemplo().bancoHorizonte());
        novaCompra(usuario, cartaoHoje, "2026-11-10", "Padaria Fictícia", "50.00", 1);

        JsonNode resposta = projecao(usuario, "?contas=" + exemplo.exemplo().bancoHorizonte());
        assertThat(resposta.get("pendencias").valueStream().map(item -> item.get("tipo").asString() + " " + textoDoItem(item) + " "
                + decimal(item.get("valor"))).toList()).containsExactly("FATURA Cartão Lua 2026-11 -412.30",
                "FATURA Cartão Hoje 2026-11 -50.00", "PREVISTO Conta de água Fictícia -96.40", "PREVISTO Feira Fictícia -30.00");
        assertThat(pendencia(resposta, 0).get("vencimento").asString()).isEqualTo("2026-11-05");
        assertThat(pendencia(resposta, 1).get("vencimento").asString()).isEqualTo("2026-11-25");
        assertThat(pendencia(resposta, 1).get("pendente").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(pendencia(resposta, 2).get("data").asString()).isEqualTo("2026-11-20");
        assertThat(pendencia(resposta, 3).get("data").asString()).isEqualTo("2026-11-25");
        assertThat(pendencia(resposta, 3).get("ocorrencia").get("atrasada").asBoolean()).isFalse();
        assertThat(pendencia(resposta, 3).get("ocorrencia").get("periodo").asString()).isEqualTo("2026-11-01");
        assertThat(pendencia(resposta, 3).get("ocorrencia").get("recorrenciaId").asLong()).isEqualTo(feira);
        assertResumo(resposta.get("resumo"), "1240.35", "-588.70", "651.65", "-2477.95", "2026-12-04", "1375.46", 5,
                new Trecho("2026-11-30", "2026-12-04"));
        assertThat(dias(resposta)).contains("2026-12-25 -210.00 1785.16");
        assertThat(itens(resposta, "2026-12-25")).containsExactly("PREVISTO Diarista Fictícia -180.00",
                "PREVISTO Feira Fictícia -30.00");
    }

    @Test
    void oPedidoQueNaoServe() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        ExemploDaProjecao exemplo = montarAProjecao(usuario);

        assertCampo(api.get(usuario, "/api/projecao?ate=2027-11-26"), "ate", "A projeção vai até 25/11/2027.");
        assertCampo(api.get(usuario, "/api/projecao?ate=2026-11-25"), "ate", "Escolha uma data depois de hoje.");
        assertCampo(api.get(usuario, "/api/projecao?ate=2026-13-01"), "ate", "Escolha uma data válida.");
        assertThat(projecao(usuario, "?ate=").get("ate").asString()).isEqualTo("2026-12-31");
        assertThat(projecao(usuario, "?contas=" + exemplo.exemplo().bancoHorizonte() + ",999999,abc," + exemplo.exemplo().bancoAntigo())
                .get("escolhidas").valueStream().map(JsonNode::asLong).toList()).containsExactly(exemplo.exemplo().bancoHorizonte());
        JsonNode vazia = projecao(usuario, "?contas=");
        assertThat(vazia.get("escolhidas")).isEmpty();
        assertResumo(vazia.get("resumo"), "0.00", "0.00", "0.00", "0.00", "2026-11-25", "0.00", 0);
        assertThat(vazia.get("porConta")).isEmpty();
        assertThat(vazia.get("pendencias")).isEmpty();
        assertThat(vazia.get("dias")).isEmpty();
        assertThat(vazia.get("contas").size()).isEqualTo(6);
        assertThat(mvc.get().uri("/api/projecao").exchange()).hasStatus(401);
    }
}
