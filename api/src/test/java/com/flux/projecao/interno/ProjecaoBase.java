package com.flux.projecao.interno;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.flux.recorrencias.interno.RecorrenciasBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

import static com.flux.suporte.ApiDeTeste.conta;
import static com.flux.suporte.ApiDeTeste.lancamento;
import static com.flux.suporte.ApiDeTeste.transferencia;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class ProjecaoBase extends RecorrenciasBase {

    protected JsonNode projecao(UsuarioDeTeste usuario, String parametros) {
        MvcTestResult resposta = api.get(usuario, "/api/projecao" + parametros);
        assertThat(resposta).hasStatus(200);
        return json(resposta);
    }

    protected JsonNode aviso(UsuarioDeTeste usuario) {
        MvcTestResult resposta = api.get(usuario, "/api/projecao/aviso");
        assertThat(resposta).hasStatus(200);
        return json(resposta);
    }

    protected void assertResumo(JsonNode json, String hoje, String pendencias, String inicio, String menor, String diaDoMenor,
                                String fim, int diasNegativos, Trecho... trechos) {
        assertThat(json.get("hoje").decimalValue()).isEqualByComparingTo(hoje);
        assertThat(json.get("pendencias").decimalValue()).isEqualByComparingTo(pendencias);
        assertThat(json.get("inicio").decimalValue()).isEqualByComparingTo(inicio);
        assertThat(json.get("menor").decimalValue()).isEqualByComparingTo(menor);
        assertThat(json.get("diaDoMenor").asString()).isEqualTo(diaDoMenor);
        assertThat(json.get("fim").decimalValue()).isEqualByComparingTo(fim);
        assertThat(json.get("diasNegativos").asInt()).isEqualTo(diasNegativos);
        assertThat(json.get("trechosNegativos").valueStream().map(item -> new Trecho(item.get("de").asString(),
                item.get("ate").asString())).toList()).containsExactly(trechos);
    }

    protected List<String> dias(JsonNode json) {
        return json.get("dias").valueStream().map(dia -> dia.get("data").asString() + " " + decimal(dia.get("movimento")) + " "
                + decimal(dia.get("saldo"))).toList();
    }

    protected List<String> itens(JsonNode json, String data) {
        return dia(json, data).get("itens").valueStream().map(item -> item.get("tipo").asString() + " " + textoDoItem(item) + " "
                + decimal(item.get("valor"))).toList();
    }

    protected JsonNode dia(JsonNode json, String data) {
        return json.get("dias").valueStream().filter(dia -> data.equals(dia.get("data").asString())).findFirst()
                .orElseThrow(() -> new AssertionError("Dia não encontrado: " + data));
    }

    protected JsonNode pendencia(JsonNode json, int indice) {
        return json.get("pendencias").get(indice);
    }

    protected ExemploDaProjecao montarAProjecao(UsuarioDeTeste usuario) {
        Exemplo exemplo = montarOExemplo(usuario);
        jdbc.update("update contas set saldo_inicial = 1590.35, cor = 'OURO' where id = ?", exemplo.bancoHorizonte());
        jdbc.update("update contas set saldo_inicial = 4310.20, cor = 'LARANJA' where id = ?", exemplo.bancoAurora());
        long poupanca = api.idCriado(api.post(usuario, "/api/contas", conta("Poupança Horizonte", "POUPANCA", "14920.40",
                "2026-10-01", "\"AZUL\"")));
        long carteira = api.idCriado(api.post(usuario, "/api/contas", conta("Carteira", "CARTEIRA", "182.30", "2026-10-01",
                "\"CINZA\"")));
        long conjunta = api.idCriado(api.post(usuario, "/api/contas", conta("Conta conjunta", "CORRENTE", "1449.00", "2026-10-01",
                "\"ROSA\"")));
        long investimentos = api.idCriado(api.post(usuario, "/api/contas", conta("Investimentos Lua", "INVESTIMENTO", "30000.00",
                "2026-10-01", "\"ROXO\"")));
        novaCompra(usuario, exemplo.cartaoLua(), "2026-10-10", "Loja Fictícia", "412.30", 1);
        novaCompra(usuario, exemplo.cartaoLua(), "2026-11-20", "Mercado do mês Fictício", "1284.90", 1);
        novaCompra(usuario, exemplo.cartaoHorizonte(), "2026-11-15", "Eletrônicos Fictícios", "1113.69", 1);
        assertThat(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "89.90", "2026-11-30", "Seguro Fictício",
                exemplo.bancoHorizonte(), null))).hasStatus(201);
        assertThat(api.post(usuario, "/api/transferencias", transferencia("500.00", "2026-12-06", exemplo.bancoHorizonte(), poupanca,
                "Reserva do mês"))).hasStatus(201);
        return new ExemploDaProjecao(exemplo, poupanca, carteira, conjunta, investimentos);
    }

    protected static String decimal(JsonNode no) {
        return no.decimalValue().setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    protected static String texto(JsonNode no, String campo) {
        return no.get(campo).isNull() ? null : no.get(campo).asString();
    }

    protected static String textoDoItem(JsonNode item) {
        return "FATURA".equals(item.get("tipo").asString()) ? item.get("cartaoNome").asString() + " " + item.get("mes").asString()
                : item.get("descricao").asString();
    }

    protected record Trecho(String de, String ate) {
    }

    protected record ExemploDaProjecao(Exemplo exemplo, long poupanca, long carteira, long conjunta, long investimentos) {
    }
}
