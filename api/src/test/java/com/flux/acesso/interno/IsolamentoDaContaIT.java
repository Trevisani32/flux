package com.flux.acesso.interno;

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

class IsolamentoDaContaIT extends IntegracaoBase {

    @Test
    void trocaPendenteDeUmaContaNaoApareceNaOutra() {
        UsuarioDeTeste a = usuariosDeTeste.criar();
        UsuarioDeTeste b = usuariosDeTeste.criar();
        assertThat(trocar(a, emailUnico(), a.senha())).hasStatus(202);

        assertThat(me(b)).hasStatusOk().bodyJson().extractingPath("trocaDeEmailPendente").isNull();
    }

    @Test
    void linkDaTrocaDeUmaContaNaoMudaAOutra() {
        UsuarioDeTeste a = usuariosDeTeste.criar();
        UsuarioDeTeste b = usuariosDeTeste.criar();
        String novo = emailUnico();
        trocar(a, novo, a.senha());
        String token = CaixaDeEntrada.token(caixaDeEntrada.mensagensPara(novo, "Confirme seu novo e-mail no Flux").getFirst());

        assertThat(confirmar(token)).hasStatusOk();
        assertThat(me(a)).bodyJson().extractingPath("email").isEqualTo(novo);
        assertThat(me(b)).bodyJson().extractingPath("email").isEqualTo(b.email());
    }

    @Test
    void renomearOutraContaNaoAlteraNomeDaPrimeira() {
        UsuarioDeTeste a = usuariosDeTeste.criar();
        UsuarioDeTeste b = usuariosDeTeste.criar();
        assertThat(mvc.put().uri("/api/usuarios/me/nome").header(HttpHeaders.AUTHORIZATION, bearer(b))
                .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Nome do B\"}")).hasStatusOk();

        assertThat(me(a)).bodyJson().extractingPath("nome").isEqualTo("Usuário de Teste");
        assertThat(me(b)).bodyJson().extractingPath("nome").isEqualTo("Nome do B");
    }

    @Test
    void excluirOutraContaNaoRevogaSessaoDaPrimeiraNemSeusLinks() {
        UsuarioDeTeste a = usuariosDeTeste.criar();
        UsuarioDeTeste b = usuariosDeTeste.criar();
        String refresh = cookie(entrar(a));
        assertThat(mvc.post().uri("/api/auth/esqueci-a-senha").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + a.email() + "\"}")).hasStatus(202);
        String token = CaixaDeEntrada.token(caixaDeEntrada.mensagensPara(a.email(), "Redefinir sua senha do Flux").getFirst());
        assertThat(mvc.delete().uri("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, bearer(b))
                .contentType(MediaType.APPLICATION_JSON).content("{\"senhaAtual\":\"" + b.senha() + "\"}")).hasStatus(204);

        assertThat(entrar(a)).hasStatusOk();
        assertThat(me(a)).hasStatusOk();
        assertThat(mvc.post().uri("/api/auth/renovar").cookie(new Cookie("flux_renovacao", refresh))).hasStatusOk();
        assertThat(mvc.post().uri("/api/auth/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"novaSenha\":\"senha-nova-de-teste-456\"}")).hasStatus(204);
    }

    @Test
    void esqueciASenhaParaUmaContaNaoEnviaParaOutra() {
        UsuarioDeTeste a = usuariosDeTeste.criar();
        UsuarioDeTeste b = usuariosDeTeste.criar();
        assertThat(mvc.post().uri("/api/auth/esqueci-a-senha").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + a.email() + "\"}")).hasStatus(202);

        assertThat(caixaDeEntrada.mensagensPara(a.email(), "Redefinir sua senha do Flux")).hasSize(1);
        assertThat(caixaDeEntrada.mensagensPara(b.email(), "Redefinir sua senha do Flux")).isEmpty();
    }

    private MvcTestResult me(UsuarioDeTeste usuario) { return mvc.get().uri("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, bearer(usuario)).exchange(); }
    private MvcTestResult trocar(UsuarioDeTeste usuario, String email, String senha) { return mvc.post().uri("/api/usuarios/me/email").header(HttpHeaders.AUTHORIZATION, bearer(usuario)).contentType(MediaType.APPLICATION_JSON).content("{\"novoEmail\":\"" + email + "\",\"senhaAtual\":\"" + senha + "\"}").exchange(); }
    private MvcTestResult confirmar(String token) { return mvc.post().uri("/api/auth/confirmar-email").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}").exchange(); }
    private MvcTestResult entrar(UsuarioDeTeste usuario) { return mvc.post().uri("/api/auth/entrar").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + usuario.email() + "\",\"senha\":\"" + usuario.senha() + "\"}").exchange(); }
    private static String bearer(UsuarioDeTeste usuario) { return "Bearer " + usuario.tokenAcesso(); }
    private static String cookie(MvcTestResult resposta) { String setCookie = resposta.getResponse().getHeader(HttpHeaders.SET_COOKIE); return setCookie.substring("flux_renovacao=".length(), setCookie.indexOf(';')); }
    private static String emailUnico() { return "isolamento-" + UUID.randomUUID() + "@teste.local"; }
}
