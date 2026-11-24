package com.flux.relatorios.interno;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import static com.flux.suporte.ApiDeTeste.conta;
import static com.flux.suporte.ApiDeTeste.lancamento;
import static org.assertj.core.api.Assertions.assertThat;

class IsolamentoDosRelatoriosIT extends RelatoriosBase {

    @Test
    void oUsuarioBNaoVeNadaDoUsuarioA() {
        fixarOMesAtual();
        UsuarioDeTeste usuarioA = usuariosDeTeste.criar();
        montarOMesDeReferencia(usuarioA);
        JsonNode relatorioDeAAntes = relatorio(usuarioA, mes(0));

        UsuarioDeTeste usuarioB = criarUsuarioB();
        JsonNode relatorioDeB = relatorio(usuarioB, mes(0));
        assertThat(relatorioDeB.get("saldo").get("total").toString()).isEqualTo("-222.00");
        assertThat(relatorioDeB.get("despesas").get("valor").toString()).isEqualTo("999.00");
        assertThat(relatorioDeB.get("categorias").toString()).doesNotContain("Moradia");
        assertThat(relatorio(usuarioA, mes(0))).isEqualTo(relatorioDeAAntes);
    }

    @Test
    void excluirAContaDeANaoMudaORelatorioDeB() {
        fixarOMesAtual();
        UsuarioDeTeste usuarioA = usuariosDeTeste.criar();
        montarOMesDeReferencia(usuarioA);
        UsuarioDeTeste usuarioB = criarUsuarioB();
        JsonNode antes = relatorio(usuarioB, mes(0));

        assertThat(mvc.delete()
                .uri("/api/usuarios/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + usuarioA.tokenAcesso())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"senhaAtual\":\"" + usuarioA.senha() + "\"}")
                .exchange()).hasStatus(204);
        assertThat(relatorio(usuarioB, mes(0))).isEqualTo(antes);
    }

    private UsuarioDeTeste criarUsuarioB() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = api.idCriado(api.post(usuario, "/api/contas", conta("Conta B", "CORRENTE", "777", d(4, 1), "null")));
        assertThat(api.post(
                usuario,
                "/api/lancamentos",
                lancamento("DESPESA", "999", d(0, 6), "Despesa de B", conta, null))).hasStatus(201);
        return usuario;
    }
}
