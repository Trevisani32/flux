package com.flux.acesso.interno;

import java.time.Duration;
import java.util.UUID;

import com.flux.suporte.CaixaDeEntrada;
import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

class RedefinicaoDeSenhaIT extends IntegracaoBase {

    @Test
    void esqueciSenhaDeContaExistenteEnviaLinkDeRedefinicao() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();

        assertThat(esqueci(usuario.email())).hasStatus(202);

        var email = caixaDeEntrada.mensagensPara(usuario.email(), "Redefinir sua senha do Flux");
        assertThat(email).hasSize(1);
        assertThat(email.getFirst().texto()).contains("/redefinir-senha#token=");
    }

    @Test
    void esqueciSenhaDeEmailInexistenteNaoEnviaLink() {
        String email = "ausente-" + UUID.randomUUID() + "@teste.local";

        assertThat(esqueci(email)).hasStatus(202);

        assertThat(caixaDeEntrada.mensagensPara(email, "Redefinir sua senha do Flux")).isEmpty();
    }

    @Test
    void redefinirSenhaRevogaRefreshEPermiteNovaSenhaUmaVez() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        String refresh = cookie(entrar(usuario.email(), usuario.senha()));
        esqueci(usuario.email());
        String token = tokenDeRedefinicao(usuario.email());

        MvcTestResult resposta = redefinir(token, "senha-nova-de-teste-456");
        assertThat(resposta).hasStatus(204).containsHeader(HttpHeaders.SET_COOKIE);
        assertThat(resposta.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        assertThat(mvc.post().uri("/api/auth/renovar").cookie(new Cookie("flux_renovacao", refresh))).hasStatus(401);
        assertThat(entrar(usuario.email(), usuario.senha())).hasStatus(401);
        assertThat(entrar(usuario.email(), "senha-nova-de-teste-456")).hasStatusOk();
        assertThat(redefinir(token, "senha-mais-nova-456")).hasStatus(422)
                .bodyJson().extractingPath("codigo").isEqualTo("LINK_INVALIDO");
    }

    @Test
    void segundoPedidoInvalidaPrimeiroLink() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        esqueci(usuario.email());
        String primeiro = tokenDeRedefinicao(usuario.email());
        esqueci(usuario.email());
        String segundo = CaixaDeEntrada.token(caixaDeEntrada.mensagensPara(usuario.email(), "Redefinir sua senha do Flux").getFirst());

        assertThat(redefinir(primeiro, "senha-nova-de-teste-456")).hasStatus(422);
        assertThat(redefinir(segundo, "senha-nova-de-teste-456")).hasStatus(204);
    }

    @Test
    void rejeitaLinkVencido() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        esqueci(usuario.email());
        String token = tokenDeRedefinicao(usuario.email());
        relogioDeTeste.avancar(Duration.ofMinutes(31));

        assertThat(redefinir(token, "senha-nova-de-teste-456")).hasStatus(422);
    }

    @Test
    void rejeitaNovaSenhaComMaisDeSetentaEDoisBytes() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        esqueci(usuario.email());

        assertThat(redefinir(tokenDeRedefinicao(usuario.email()), "ç".repeat(40))).hasStatus(422)
                .bodyJson().extractingPath("detail").isEqualTo("A nova senha é longa demais.");
    }

    @Test
    void redefinirCancelaTrocaDeEmailPendente() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        String novoEmail = "novo-" + UUID.randomUUID() + "@teste.local";
        assertThat(trocarEmail(usuario, novoEmail, usuario.senha())).hasStatus(202);
        String tokenTroca = CaixaDeEntrada.token(caixaDeEntrada.mensagensPara(novoEmail, "Confirme seu novo e-mail no Flux").getFirst());
        esqueci(usuario.email());

        assertThat(redefinir(tokenDeRedefinicao(usuario.email()), "senha-nova-de-teste-456")).hasStatus(204);
        assertThat(me(usuario)).hasStatusOk().bodyJson().extractingPath("trocaDeEmailPendente").isNull();
        assertThat(confirmar(tokenTroca)).hasStatus(422);
    }

    @Test
    void redefinirSenhaTambemConfirmaContaNaoConfirmada() {
        String email = cadastrarNaoConfirmado();
        esqueci(email);

        assertThat(redefinir(tokenDeRedefinicao(email), "senha-nova-de-teste-456")).hasStatus(204);
        assertThat(entrar(email, "senha-nova-de-teste-456")).hasStatusOk();
    }

    @Test
    void validaTamanhoMinimoDaNovaSenha() {
        assertThat(redefinir(Tokens.novoValor(), "123456789")).hasStatus(400);
    }

    private MvcTestResult esqueci(String email) {
        return mvc.post().uri("/api/auth/esqueci-a-senha").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}").exchange();
    }

    private String tokenDeRedefinicao(String email) {
        return CaixaDeEntrada.token(caixaDeEntrada.mensagensPara(email, "Redefinir sua senha do Flux").getFirst());
    }

    private MvcTestResult redefinir(String token, String senha) {
        return mvc.post().uri("/api/auth/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"novaSenha\":\"" + senha + "\"}").exchange();
    }

    private MvcTestResult entrar(String email, String senha) {
        return mvc.post().uri("/api/auth/entrar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}").exchange();
    }

    private MvcTestResult trocarEmail(UsuarioDeTeste usuario, String novoEmail, String senha) {
        return mvc.post().uri("/api/usuarios/me/email").header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso())
                .contentType(MediaType.APPLICATION_JSON).content("{\"novoEmail\":\"" + novoEmail + "\",\"senhaAtual\":\"" + senha + "\"}").exchange();
    }

    private MvcTestResult me(UsuarioDeTeste usuario) {
        return mvc.get().uri("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso()).exchange();
    }

    private MvcTestResult confirmar(String token) {
        return mvc.post().uri("/api/auth/confirmar-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}").exchange();
    }

    private String cadastrarNaoConfirmado() {
        String email = "nao-confirmado-" + UUID.randomUUID() + "@teste.local";
        assertThat(mvc.post().uri("/api/auth/cadastrar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Ana\",\"email\":\"" + email + "\",\"senha\":\"senha-de-teste-123\",\"aceiteDosTermos\":true}"))
                .hasStatus(202);
        return email;
    }

    private static String cookie(MvcTestResult resposta) {
        String setCookie = resposta.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        return setCookie.substring("flux_renovacao=".length(), setCookie.indexOf(';'));
    }
}
