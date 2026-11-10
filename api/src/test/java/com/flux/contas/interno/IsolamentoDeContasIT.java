package com.flux.contas.interno;

import java.util.List;
import java.util.function.Function;

import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

import static com.flux.suporte.ApiDeTeste.conta;
import static com.flux.suporte.ApiDeTeste.texto;
import static org.assertj.core.api.Assertions.assertThat;

/** ADR 0007: a conta de outro usuário é tratada como inexistente, com o mesmo corpo. */
class IsolamentoDeContasIT extends IntegracaoBase {

    @Test
    void outroUsuarioNaoListaNaoLeNaoEditaNaoArquivaNaoReativaNemExclui() {
        UsuarioDeTeste a = usuariosDeTeste.criar();
        UsuarioDeTeste b = usuariosDeTeste.criar();
        long daA = api.idCriado(api.post(a, "/api/contas", conta("Banco Horizonte", "CORRENTE", "4218.40", "2026-10-01", "\"ROXO\"")));
        long arquivadaDaA = api.idCriado(api.post(a, "/api/contas", conta("Antiga", "CORRENTE", "0", "2026-10-01", "null")));
        api.acao(a, "/api/contas/{id}/arquivar", arquivadaDaA);

        assertThat(texto(api.get(b, "/api/contas"))).isEqualTo(
                "{\"ativas\":[],\"arquivadas\":[],\"totais\":{\"geral\":0.00,\"disponivel\":0.00,\"investimentos\":0.00}}");
        List<Function<Long, MvcTestResult>> pedidosDeB = List.of(
                id -> api.get(b, "/api/contas/{id}", id),
                id -> api.put(b, "/api/contas/{id}", conta("Invadida", "CARTEIRA", "1", "2026-10-01", "null"), id),
                id -> api.acao(b, "/api/contas/{id}/arquivar", id),
                id -> api.acao(b, "/api/contas/{id}/reativar", id),
                id -> api.delete(b, "/api/contas/{id}", id));
        for (Function<Long, MvcTestResult> pedido : pedidosDeB) {
            MvcTestResult contaDeOutro = pedido.apply(daA);
            MvcTestResult inexistente = pedido.apply(Long.MAX_VALUE);
            assertThat(contaDeOutro).hasStatus(404).bodyJson().extractingPath("detail").isEqualTo("Conta não encontrada.");
            assertThat(api.json(contaDeOutro).get("title")).isEqualTo(api.json(inexistente).get("title"));
            assertThat(api.json(contaDeOutro).get("detail")).isEqualTo(api.json(inexistente).get("detail"));
            assertThat(pedido.apply(arquivadaDaA)).hasStatus(404);
        }

        JsonNode contaDaA = api.json(api.get(a, "/api/contas/{id}", daA));
        assertThat(contaDaA.get("nome").asString()).isEqualTo("Banco Horizonte");
        assertThat(contaDaA.get("tipo").asString()).isEqualTo("CORRENTE");
        assertThat(contaDaA.get("arquivadaEm").isNull()).isTrue();
        assertThat(api.json(api.get(a, "/api/contas/{id}", arquivadaDaA)).get("arquivadaEm").isNull()).isFalse();
        assertThat(api.post(b, "/api/contas", conta("Banco Horizonte", "CORRENTE", "0", "2026-10-01", "null"))).hasStatus(201);
        assertThat(api.json(api.get(a, "/api/contas")).get("ativas").size()).isEqualTo(1);
    }
}
