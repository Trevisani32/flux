package com.flux.lancamentos.interno;

import java.time.LocalDate;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static com.flux.suporte.ApiDeTeste.conta;
import static com.flux.suporte.ApiDeTeste.texto;
import static org.assertj.core.api.Assertions.assertThat;

class ProximasFaturasIT extends PagamentosBase {

    private final JsonMapper jsonExato = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    @Test
    void asFechadasComPendenteEntramEmOrdemEAPagaOCreditoEAAbertaNao() {
        LocalDate hoje = fixarHojeNoDia10();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = api.idCriado(api.post(usuario, "/api/contas", conta("Conta", "CORRENTE", "5000", diaUmAtras(5), "null")));
        long cartao = cartaoQueFecha5(usuario, "Cartão", conta);

        assertThat(api.post(usuario, "/api/compras", compra(cartao, diaUmAtras(4), "Paga fora", "60", 1, 1, null)))
                .hasStatus(201);
        assertThat(api.put(usuario, "/api/cartoes/{id}/faturas/{mes}/paga-fora", "", cartao, mesAtras(4))).hasStatus(200);
        assertThat(api.post(usuario, "/api/compras", compra(cartao, diaUmAtras(3), "Paga", "200", 1, 1, null))).hasStatus(201);
        assertThat(pagar(usuario, cartao, mesAtras(3), conta, diaUmAtras(3), "200")).hasStatus(201);
        assertThat(api.post(usuario, "/api/compras", compra(cartao, diaUmAtras(2), "Parcial", "150", 1, 1, null)))
                .hasStatus(201);
        assertThat(pagar(usuario, cartao, mesAtras(2), conta, diaUmAtras(2), "50")).hasStatus(201);
        assertThat(api.post(usuario, "/api/compras", compra(cartao, diaUmAtras(1), "Crédito", "80", 1, 1, null))).hasStatus(201);
        assertThat(api.post(usuario, "/api/estornos", estorno(cartao, diaUmAtras(1), "Estorno", "100", null))).hasStatus(201);
        assertThat(api.post(usuario, "/api/compras", compra(cartao, diaUmAtras(0), "Fechada", "70", 1, 1, null))).hasStatus(201);
        assertThat(api.post(usuario, "/api/compras", compra(cartao, hoje.withDayOfMonth(8).toString(), "Aberta", "30", 1, 1, null)))
                .hasStatus(201);

        JsonNode cartaoJson = json(api.get(usuario, "/api/cartoes")).get("ativos").get(0);
        assertThat(cartaoJson.get("faturasAPagar").toString()).isEqualTo(
                "[{\"mes\":\"" + mesAtras(2) + "\",\"fechamento\":\"" + diaDeFechamento(2) + "\",\"vencimento\":\""
                        + diaDeVencimento(2) + "\",\"pendente\":100.00},{\"mes\":\"" + mesAtras(0)
                        + "\",\"fechamento\":\"" + diaDeFechamento(0) + "\",\"vencimento\":\"" + diaDeVencimento(0)
                        + "\",\"pendente\":70.00}]");
        assertThat(cartaoJson.get("faturaAberta").get("mes").asString()).isEqualTo(mesAtras(-1));
        assertThat(cartaoJson.get("faturaAberta").get("lancamentos").asInt()).isEqualTo(1);
    }

    @Test
    void semCompraAListaEVazia() {
        fixarHojeNoDia10();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Conta");
        long cartao = cartaoQueFecha5(usuario, "Cartão arquivado", conta);

        assertThat(json(api.get(usuario, "/api/cartoes")).get("ativos").get(0).get("faturasAPagar").toString()).isEqualTo("[]");
        assertThat(api.acao(usuario, "/api/cartoes/{id}/arquivar", cartao)).hasStatus(200);
        assertThat(json(api.get(usuario, "/api/cartoes")).get("arquivados").get(0).get("faturasAPagar").toString()).isEqualTo("[]");
    }

    private JsonNode json(org.springframework.test.web.servlet.assertj.MvcTestResult resposta) {
        return jsonExato.readTree(texto(resposta));
    }

    private String diaDeFechamento(int mesesAtras) {
        return mesAtras(mesesAtras) + "-05";
    }

    private String diaDeVencimento(int mesesAtras) {
        return mesAtras(mesesAtras) + "-15";
    }
}
