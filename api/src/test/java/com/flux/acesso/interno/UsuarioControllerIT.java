package com.flux.acesso.interno;

import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

class UsuarioControllerIT extends IntegracaoBase {

    @Test
    void retornaApenasUsuarioAutenticado() {
        assertThat(mvc.get().uri("/api/usuarios/me")).hasStatus(401)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        UsuarioDeTeste primeiro = usuariosDeTeste.criar();
        UsuarioDeTeste segundo = usuariosDeTeste.criar();

        MvcTestResult respostaPrimeiro = me(primeiro);
        assertThat(respostaPrimeiro).hasStatusOk().bodyJson().extractingPath("id").isEqualTo(primeiro.id().intValue());
        assertThat(respostaPrimeiro).bodyJson().extractingPath("nome").isEqualTo("Usuário de Teste");
        assertThat(respostaPrimeiro).bodyJson().extractingPath("email").isEqualTo(primeiro.email());
        assertThat(respostaPrimeiro).bodyJson().extractingPath("trocaDeEmailPendente").isNull();
        assertThat(respostaPrimeiro).bodyJson().extractingPath("termos").isNull();
        MvcTestResult respostaSegundo = me(segundo);
        assertThat(respostaSegundo).hasStatusOk().bodyJson().extractingPath("id").isEqualTo(segundo.id().intValue());
        assertThat(respostaSegundo).bodyJson().extractingPath("nome").isEqualTo("Usuário de Teste");
        assertThat(respostaSegundo).bodyJson().extractingPath("email").isEqualTo(segundo.email());
        assertThat(respostaSegundo).bodyJson().extractingPath("trocaDeEmailPendente").isNull();
        assertThat(respostaSegundo).bodyJson().extractingPath("termos").isNull();
    }

    @Test
    void validaTrocaDeSenha() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();

        assertThat(trocar(usuario, "errada", "nova-senha-123"))
                .hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Senha atual incorreta.");
        assertThat(trocar(usuario, usuario.senha(), "123456789"))
                .hasStatus(400).bodyJson().extractingPath("campos").isNotNull();
        assertThat(trocar(usuario, usuario.senha(), "ç".repeat(40)))
                .hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("A nova senha é longa demais.");
    }

    @Test
    void trocaSenhaRevogaRefreshEExigeNovaSenha() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        String refresh = cookie(login(usuario));

        MvcTestResult troca = trocar(usuario, usuario.senha(), "nova-senha-123");
        assertThat(troca).hasStatus(204).containsHeader(HttpHeaders.SET_COOKIE);
        assertThat(troca.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        assertThat(mvc.post().uri("/api/auth/renovar").cookie(new Cookie("flux_renovacao", refresh)))
                .hasStatus(401);
        assertThat(login(usuario)).hasStatus(401);
        assertThat(login(usuario.email(), "nova-senha-123")).hasStatusOk();
    }

    private MvcTestResult me(UsuarioDeTeste usuario) {
        return mvc.get().uri("/api/usuarios/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso()).exchange();
    }

    private MvcTestResult trocar(UsuarioDeTeste usuario, String atual, String nova) {
        return mvc.put().uri("/api/usuarios/me/senha")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"senhaAtual\":\"" + atual + "\",\"novaSenha\":\"" + nova + "\"}")
                .exchange();
    }

    private MvcTestResult login(UsuarioDeTeste usuario) {
        return login(usuario.email(), usuario.senha());
    }

    private MvcTestResult login(String email, String senha) {
        return mvc.post().uri("/api/auth/entrar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}")
                .exchange();
    }

    private String cookie(MvcTestResult resultado) {
        String valor = resultado.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        return valor.substring("flux_renovacao=".length(), valor.indexOf(';'));
    }
}
