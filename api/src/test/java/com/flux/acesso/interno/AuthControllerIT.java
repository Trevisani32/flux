package com.flux.acesso.interno;

import java.time.Duration;

import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerIT extends IntegracaoBase {

    @Test
    void entraComCookieSeguroENormalizaEmail() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();

        MvcTestResult resultado = login("  " + usuario.email().toUpperCase() + " ", usuario.senha());

        assertThat(resultado).hasStatusOk()
                .bodyJson().extractingPath("usuario.email").isEqualTo(usuario.email());
        assertThat(resultado).bodyJson().extractingPath("tokenAcesso").isNotNull();
        String cookie = resultado.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie).contains("flux_renovacao=", "HttpOnly", "SameSite=Strict", "Path=/api/auth",
                "Max-Age=2592000", "Secure");
    }

    @Test
    void credenciaisInvalidasTemMesmoCorpoEValidacaoFunciona() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();

        MvcTestResult senhaErrada = login(usuario.email(), "senha-errada");
        MvcTestResult emailInexistente = login("ausente@teste.local", usuario.senha());

        assertThat(senhaErrada).hasStatus(401).bodyJson().extractingPath("detail")
                .isEqualTo("E-mail ou senha inválidos");
        assertThat(emailInexistente).hasStatus(401).bodyJson().extractingPath("detail")
                .isEqualTo("E-mail ou senha inválidos");
        assertThat(mvc.post().uri("/api/auth/entrar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"\",\"senha\":\"\"}"))
                .hasStatus(400).bodyJson().extractingPath("campos").isNotNull();
    }

    @Test
    void rotacionaDetectaReusoERevogaFamilia() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        String cookieA = cookie(login(usuario.email(), usuario.senha()));

        MvcTestResult renovacao = renovar(cookieA);
        assertThat(renovacao).hasStatusOk();
        String cookieB = cookie(renovacao);
        assertThat(renovar(cookieA)).hasStatus(401);
        assertThat(renovar(cookieB)).hasStatus(401);
    }

    @Test
    void trataCookieAusenteExpiradoESaida() {
        MvcTestResult renovacaoSemCookie = mvc.post().uri("/api/auth/renovar").exchange();
        assertThat(renovacaoSemCookie).hasStatus(401).containsHeader(HttpHeaders.SET_COOKIE);
        assertThat(renovacaoSemCookie.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        String cookie = cookie(login(usuario.email(), usuario.senha()));
        relogioDeTeste.avancar(Duration.ofDays(31));
        assertThat(renovar(cookie)).hasStatus(401);

        UsuarioDeTeste outro = usuariosDeTeste.criar();
        String cookieAtivo = cookie(login(outro.email(), outro.senha()));
        MvcTestResult saida = sair(cookieAtivo);
        assertThat(saida).hasStatus(204).containsHeader(HttpHeaders.SET_COOKIE);
        assertThat(saida.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        assertThat(renovar(cookieAtivo)).hasStatus(401);
        assertThat(mvc.post().uri("/api/auth/sair")).hasStatus(204);
    }

    @Test
    void jwtExpiradoRetornaProblemaSemWwwAuthenticate() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        relogioDeTeste.avancar(Duration.ofMinutes(16));

        assertThat(mvc.get().uri("/api/usuarios/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso()))
                .hasStatus(401)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .doesNotContainHeader(HttpHeaders.WWW_AUTHENTICATE);
    }

    private MvcTestResult login(String email, String senha) {
        return mvc.post().uri("/api/auth/entrar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}")
                .exchange();
    }

    private MvcTestResult renovar(String token) {
        return mvc.post().uri("/api/auth/renovar").cookie(new Cookie("flux_renovacao", token)).exchange();
    }

    private MvcTestResult sair(String token) {
        return mvc.post().uri("/api/auth/sair").cookie(new Cookie("flux_renovacao", token)).exchange();
    }

    private String cookie(MvcTestResult resultado) {
        String valor = resultado.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        return valor.substring("flux_renovacao=".length(), valor.indexOf(';'));
    }
}
