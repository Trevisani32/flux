package com.flux.exportacao.interno;

import java.time.Duration;
import java.util.HashSet;

import com.flux.acesso.interno.TokenAcessoService;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

class SenhaDaExportacaoIT extends ExportacaoBase {

    @Autowired
    private TokenAcessoService tokensDeAcesso;

    @Test
    void senhaErradaRespondeComoAExclusaoENaoMandaArquivo() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        MvcTestResult exportacao = exportar(usuario, "JSON", "senha-errada-123");
        MvcTestResult exclusao = mvc.delete().uri("/api/usuarios/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso())
                .contentType(MediaType.APPLICATION_JSON).content("{\"senhaAtual\":\"senha-errada-123\"}").exchange();

        assertThat(exportacao).hasStatus(422).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("title").isEqualTo("Operação não permitida");
        assertThat(exportacao).bodyJson().extractingPath("detail").isEqualTo("Senha atual incorreta.");
        assertThat(exportacao.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).isNull();
        JsonNode corpoDaExportacao = api.json(exportacao);
        JsonNode corpoDaExclusao = api.json(exclusao);
        assertThat(exclusao).hasStatus(422).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(corpoDaExclusao.get("title").asString()).isEqualTo(corpoDaExportacao.get("title").asString());
        assertThat(corpoDaExclusao.get("detail").asString()).isEqualTo(corpoDaExportacao.get("detail").asString());
        assertThat(chavesSemInstance(corpoDaExclusao)).isEqualTo(chavesSemInstance(corpoDaExportacao));
    }

    @Test
    void aExportacaoContaNoMesmoLimiteDaExclusaoEDaTrocaDeSenha() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        for (int tentativa = 0; tentativa < 5; tentativa++) {
            assertThat(exportar(usuario, "JSON", usuario.senha())).hasStatusOk();
        }
        for (int tentativa = 0; tentativa < 4; tentativa++) {
            assertThat(exportar(usuario, "CSV", "senha-errada-123")).hasStatus(422);
        }
        assertThat(mvc.delete().uri("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso())
                .contentType(MediaType.APPLICATION_JSON).content("{\"senhaAtual\":\"senha-errada-123\"}")).hasStatus(422);

        MvcTestResult bloqueada = exportar(usuario, "JSON", usuario.senha());
        assertThat(bloqueada).hasStatus(429).containsHeader(HttpHeaders.RETRY_AFTER)
                .bodyJson().extractingPath("detail").isEqualTo("Muitas tentativas. Tente de novo em alguns minutos.");
        assertThat(mvc.delete().uri("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso())
                .contentType(MediaType.APPLICATION_JSON).content("{\"senhaAtual\":\"" + usuario.senha() + "\"}")).hasStatus(429);
        assertThat(mvc.put().uri("/api/usuarios/me/senha").header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"senhaAtual\":\"" + usuario.senha() + "\",\"novaSenha\":\"senha-nova-de-teste-456\"}")).hasStatus(429);

        relogioDeTeste.avancar(Duration.ofMinutes(15).plusSeconds(1));
        String tokenNovo = tokensDeAcesso.emitir(usuario.id()).valor();
        assertThat(mvc.post().uri("/api/minha-conta/exportacao").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenNovo)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formato\":\"JSON\",\"senhaAtual\":\"" + usuario.senha() + "\"}")).hasStatusOk();
        assertThat(jdbc.queryForObject("select count(*) from usuarios where id = ?", Integer.class, usuario.id())).isEqualTo(1);
    }

    private static HashSet<String> chavesSemInstance(JsonNode corpo) {
        HashSet<String> chaves = new HashSet<>();
        chaves.addAll(corpo.propertyNames());
        chaves.remove("instance");
        return chaves;
    }
}
