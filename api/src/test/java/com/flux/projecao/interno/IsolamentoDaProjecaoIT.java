package com.flux.projecao.interno;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static com.flux.suporte.ApiDeTeste.conta;
import static org.assertj.core.api.Assertions.assertThat;

class IsolamentoDaProjecaoIT extends ProjecaoBase {

    @Test
    void oUsuarioBNaoVeNadaDeA() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuarioA = usuariosDeTeste.criar();
        ExemploDaProjecao exemplo = montarAProjecao(usuarioA);
        UsuarioDeTeste usuarioB = usuariosDeTeste.criar();
        long contaB = api.idCriado(api.post(usuarioB, "/api/contas", conta("Conta B", "CORRENTE", "100.00", "2026-11-01", "null")));

        JsonNode deB = projecao(usuarioB, "");
        assertThat(deB.get("contas").valueStream().map(item -> item.get("nome").asString()).toList()).containsExactly("Conta B");
        assertThat(deB.get("escolhidas").valueStream().map(JsonNode::asLong).toList()).containsExactly(contaB);
        assertResumo(deB.get("resumo"), "100.00", "0.00", "100.00", "100.00", "2026-11-25", "100.00", 0);
        assertThat(deB.get("pendencias")).isEmpty();
        assertThat(deB.get("dias")).isEmpty();
        assertThat(deB.get("faturasFora")).isEmpty();
        assertThat(projecao(usuarioB, "?contas=" + exemplo.exemplo().bancoHorizonte()).get("escolhidas")).isEmpty();
        assertThat(projecao(usuarioB, "?contas=" + exemplo.exemplo().bancoHorizonte() + "," + contaB).get("escolhidas")
                .valueStream().map(JsonNode::asLong).toList()).containsExactly(contaB);
        assertThat(aviso(usuarioB).get("contas")).isEmpty();
        JsonNode deA = projecao(usuarioA, "?contas=" + exemplo.exemplo().bancoHorizonte());
        assertResumo(deA.get("resumo"), "1240.35", "-508.70", "731.65", "-2397.95", "2026-12-04", "1485.46", 4,
                new Trecho("2026-12-01", "2026-12-04"));
        assertThat(dias(deA)).containsExactly(
                "2026-11-26 -119.90 611.75", "2026-11-27 -180.00 431.75", "2026-11-28 -99.90 331.85",
                "2026-11-30 -279.80 52.05", "2026-12-01 -2270.00 -2217.95", "2026-12-04 -180.00 -2397.95",
                "2026-12-05 6915.10 4517.15", "2026-12-06 -500.00 4017.15", "2026-12-10 -1135.59 2881.56",
                "2026-12-11 -180.00 2701.56", "2026-12-15 -350.00 2351.56", "2026-12-18 -180.00 2171.56",
                "2026-12-20 -96.40 2075.16", "2026-12-25 -180.00 1895.16", "2026-12-26 -119.90 1775.26",
                "2026-12-28 -99.90 1675.36", "2026-12-30 -189.90 1485.46");
    }

    @Test
    void semContaNenhumaAProjecaoVemVazia() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();

        JsonNode resposta = projecao(usuario, "");
        assertThat(resposta.get("contas")).isEmpty();
        assertThat(resposta.get("escolhidas")).isEmpty();
        assertResumo(resposta.get("resumo"), "0.00", "0.00", "0.00", "0.00", "2026-11-25", "0.00", 0);
        assertThat(resposta.get("porConta")).isEmpty();
        assertThat(resposta.get("pendencias")).isEmpty();
        assertThat(resposta.get("dias")).isEmpty();
        assertThat(resposta.get("faturasFora")).isEmpty();
        assertThat(aviso(usuario).get("contas")).isEmpty();
    }
}
