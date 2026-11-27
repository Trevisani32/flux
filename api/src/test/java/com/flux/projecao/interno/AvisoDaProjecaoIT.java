package com.flux.projecao.interno;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static com.flux.suporte.ApiDeTeste.lancamento;
import static org.assertj.core.api.Assertions.assertThat;

class AvisoDaProjecaoIT extends ProjecaoBase {

    @Test
    void oInicioAvisaAsContasDoPadraoQueFicamNegativas() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        ExemploDaProjecao exemplo = montarAProjecao(usuario);
        assertThat(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "2000.00", "2026-11-28", "Reforma Fictícia",
                exemplo.conjunta(), null))).hasStatus(201);
        assertThat(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "500.00", "2026-11-25", "Feira Fictícia",
                exemplo.carteira(), null))).hasStatus(201);
        assertThat(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "31000.00", "2026-11-26", "Aporte Fictício",
                exemplo.investimentos(), null))).hasStatus(201);

        JsonNode resposta = aviso(usuario);
        assertThat(resposta.get("contas").valueStream().map(conta -> conta.get("contaNome").asString() + " "
                + decimal(conta.get("hoje")) + " " + conta.get("primeiroNegativo").asString() + " " + decimal(conta.get("menor"))
                + " " + conta.get("diaDoMenor").asString()).toList()).containsExactly(
                "Carteira -317.70 2026-11-25 -317.70 2026-11-25", "Conta conjunta 1449.00 2026-11-28 -551.00 2026-11-28",
                "Banco Horizonte 1240.35 2026-12-01 -2397.95 2026-12-04");
        assertThat(resposta.get("contas").valueStream().map(item -> item.get("contaId").asLong()).toList())
                .containsExactly(exemplo.carteira(),
                exemplo.conjunta(), exemplo.exemplo().bancoHorizonte());
        assertThat(resposta.get("contas").valueStream().map(item -> item.get("contaNome").asString()).toList())
                .doesNotContain("Investimentos Lua");
    }

    @Test
    void semContaNegativaOAvisoVemVazio() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        ExemploDaProjecao exemplo = montarAProjecao(usuario);
        assertThat(api.post(usuario, "/api/lancamentos", lancamento("RECEITA", "3000.00", "2026-11-25", "Bônus Fictício",
                exemplo.exemplo().bancoHorizonte(), null))).hasStatus(201);

        assertThat(aviso(usuario).get("contas")).isEmpty();
        JsonNode projecao = projecao(usuario, "?contas=" + exemplo.exemplo().bancoHorizonte());
        assertThat(projecao.get("resumo").get("menor").decimalValue()).isEqualByComparingTo("602.05");
        assertThat(projecao.get("resumo").get("diaDoMenor").asString()).isEqualTo("2026-12-04");
    }
}
