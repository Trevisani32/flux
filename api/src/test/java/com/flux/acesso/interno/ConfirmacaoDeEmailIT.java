package com.flux.acesso.interno;

import java.time.Duration;
import java.util.UUID;

import com.flux.suporte.CaixaDeEntrada;
import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

class ConfirmacaoDeEmailIT extends IntegracaoBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void rejeitaTokenVencido() {
        String email = cadastrar();
        String token = tokenConfirmacao(email);
        relogioDeTeste.avancar(Duration.ofHours(24).plusSeconds(1));

        assertThat(confirmar(token)).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("LINK_INVALIDO");
    }

    @Test
    void rejeitaTokenDeRedefinicaoNaConfirmacao() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        pedirRedefinicao(usuario.email());
        String token = CaixaDeEntrada.token(caixaDeEntrada.mensagensPara(usuario.email(), "Redefinir sua senha do Flux").getFirst());

        assertThat(confirmar(token)).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("LINK_INVALIDO");
    }

    @Test
    void rejeitaTokenAleatorio() {
        assertThat(confirmar(Tokens.novoValor())).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("LINK_INVALIDO");
    }

    @Test
    void validaCorpoDaConfirmacao() {
        assertThat(mvc.post().uri("/api/auth/confirmar-email").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .hasStatus(400);
    }

    @Test
    void reenvioDeContaNaoConfirmadaInvalidaPrimeiroLink() {
        String email = cadastrar();
        String primeiro = tokenConfirmacao(email);

        assertThat(reenviar(email)).hasStatus(202);
        var emails = caixaDeEntrada.mensagensPara(email, "Confirme seu e-mail no Flux");
        assertThat(emails).hasSize(2);
        String segundo = CaixaDeEntrada.token(emails.getFirst());
        assertThat(confirmar(primeiro)).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("LINK_INVALIDO");
        assertThat(confirmar(segundo)).hasStatusOk();
    }

    @Test
    void reenvioParaContaConfirmadaNaoEnviaEmail() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();

        assertThat(reenviar(usuario.email())).hasStatus(202);

        assertThat(caixaDeEntrada.mensagensPara(usuario.email(), "Confirme seu e-mail no Flux")).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from tokens_email where usuario_id = ?", Integer.class, usuario.id())).isZero();
    }

    @Test
    void reenvioParaEmailInexistenteNaoEnviaEmail() {
        String email = "ausente-" + UUID.randomUUID() + "@teste.local";

        assertThat(reenviar(email)).hasStatus(202);

        assertThat(caixaDeEntrada.mensagensPara(email, "Confirme seu e-mail no Flux")).isEmpty();
    }

    private String cadastrar() {
        String email = "confirmacao-" + UUID.randomUUID() + "@teste.local";
        assertThat(mvc.post().uri("/api/auth/cadastrar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Ana\",\"email\":\"" + email + "\",\"senha\":\"senha-de-teste-123\",\"aceiteDosTermos\":true}"))
                .hasStatus(202);
        return email;
    }

    private String tokenConfirmacao(String email) {
        return CaixaDeEntrada.token(caixaDeEntrada.mensagensPara(email, "Confirme seu e-mail no Flux").getFirst());
    }

    private MvcTestResult confirmar(String token) {
        return mvc.post().uri("/api/auth/confirmar-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}").exchange();
    }

    private MvcTestResult reenviar(String email) {
        return mvc.post().uri("/api/auth/reenviar-confirmacao").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}").exchange();
    }

    private void pedirRedefinicao(String email) {
        assertThat(mvc.post().uri("/api/auth/esqueci-a-senha").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}")).hasStatus(202);
    }
}
