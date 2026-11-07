package com.flux.acesso.interno;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.flux.suporte.CaixaDeEntrada;
import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

class MinhaContaIT extends IntegracaoBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UsuarioService usuarioService;

    @Test
    void meDeUsuarioDeTesteNaoTemTrocaNemTermos() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();

        assertThat(me(usuario)).hasStatusOk().bodyJson().extractingPath("trocaDeEmailPendente").isNull();
        assertThat(me(usuario)).bodyJson().extractingPath("termos").isNull();
    }

    @Test
    void renomeiaComEspacosNormalizados() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();

        assertThat(mvc.put().uri("/api/usuarios/me/nome").header(HttpHeaders.AUTHORIZATION, bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"  Novo Nome  \"}"))
                .hasStatusOk().bodyJson().extractingPath("nome").isEqualTo("Novo Nome");
    }

    @Test
    void validaNomeVazioELongo() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();

        assertThat(mvc.put().uri("/api/usuarios/me/nome").header(HttpHeaders.AUTHORIZATION, bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"\"}")).hasStatus(400);
        assertThat(mvc.put().uri("/api/usuarios/me/nome").header(HttpHeaders.AUTHORIZATION, bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"" + "a".repeat(121) + "\"}")).hasStatus(400);
    }

    @Test
    void trocaDeEmailComSenhaErradaDaRegraCorreta() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();

        assertThat(trocar(usuario, emailUnico(), "errada")).hasStatus(422)
                .bodyJson().extractingPath("detail").isEqualTo("Senha atual incorreta.");
    }

    @Test
    void trocaDeEmailIgualAoAtualDaRegraCorreta() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();

        assertThat(trocar(usuario, usuario.email(), usuario.senha())).hasStatus(422)
                .bodyJson().extractingPath("detail").isEqualTo("O novo e-mail é igual ao atual.");
    }

    @Test
    void confereSenhaAntesDeRejeitarEmailIgualAoAtual() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();

        assertThat(trocar(usuario, usuario.email(), "errada")).hasStatus(422)
                .bodyJson().extractingPath("detail").isEqualTo("Senha atual incorreta.");
    }

    @Test
    void trocaParaEmailDeOutraContaMantemPendenciaSemLinkNoDestino() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        UsuarioDeTeste dono = usuariosDeTeste.criar();

        assertThat(trocar(usuario, dono.email(), usuario.senha())).hasStatus(202)
                .bodyJson().extractingPath("trocaDeEmailPendente.email").isEqualTo(dono.email());
        assertThat(caixaDeEntrada.mensagensPara(dono.email())).isEmpty();
        assertThat(caixaDeEntrada.mensagensPara(usuario.email(), "Pedido de troca do e-mail da sua conta")).hasSize(1);
        assertThat(me(usuario)).bodyJson().extractingPath("trocaDeEmailPendente.email").isEqualTo(dono.email());
        Long tokenId = jdbc.queryForObject("select id from tokens_email where usuario_id = ? and finalidade = 'TROCA_DE_EMAIL'", Long.class, usuario.id());
        assertThat(jdbc.queryForObject("select token_hash is null from tokens_email where id = ?", Boolean.class, tokenId)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from event_publication where serialized_event = ?", Integer.class,
                "{\"tokenId\":" + tokenId + "}")).isZero();

        String livre = emailUnico();
        assertThat(trocar(usuario, livre, usuario.senha())).hasStatus(202);
        assertThat(jdbc.queryForObject("select invalidado_em is not null from tokens_email where id = ?", Boolean.class, tokenId)).isTrue();
        assertThat(caixaDeEntrada.mensagensPara(livre, "Confirme seu novo e-mail no Flux")).hasSize(1);
    }

    @Test
    void trocaValidaEnviaOsDoisEmailsEMostraPendencia() throws Exception {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        String novo = emailUnico();

        Instant antes = Instant.now(relogioDeTeste);
        MvcTestResult resposta = trocar(usuario, novo, usuario.senha());
        Instant depois = Instant.now(relogioDeTeste);

        assertThat(resposta).hasStatus(202)
                .bodyJson().extractingPath("trocaDeEmailPendente.email").isEqualTo(novo);
        String expiraEm = JsonPath.read(resposta.getResponse().getContentAsString(), "$.trocaDeEmailPendente.expiraEm");
        assertThat(Instant.parse(expiraEm)).isBetween(antes.plus(Duration.ofHours(24)).minusSeconds(1),
                depois.plus(Duration.ofHours(24)).plusSeconds(1));
        assertThat(caixaDeEntrada.mensagensPara(novo, "Confirme seu novo e-mail no Flux")).hasSize(1);
        assertThat(caixaDeEntrada.mensagensPara(usuario.email(), "Pedido de troca do e-mail da sua conta").getFirst().texto())
                .contains(novo.charAt(0) + "•••@teste.local");
    }

    @Test
    void segundoPedidoInvalidaLinkAnterior() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        String primeiroEmail = emailUnico();
        trocar(usuario, primeiroEmail, usuario.senha());
        String primeiro = CaixaDeEntrada.token(caixaDeEntrada.mensagensPara(primeiroEmail, "Confirme seu novo e-mail no Flux").getFirst());
        String segundoEmail = emailUnico();

        assertThat(trocar(usuario, segundoEmail, usuario.senha())).hasStatus(202);
        assertThat(confirmar(primeiro)).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("LINK_INVALIDO");
        assertThat(me(usuario)).bodyJson().extractingPath("trocaDeEmailPendente.email").isEqualTo(segundoEmail);
    }

    @Test
    void confirmarTrocaMudaLoginEMantemRefreshAnterior() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        String refresh = cookie(entrar(usuario.email(), usuario.senha()));
        String novo = emailUnico();
        trocar(usuario, novo, usuario.senha());
        String token = CaixaDeEntrada.token(caixaDeEntrada.mensagensPara(novo, "Confirme seu novo e-mail no Flux").getFirst());

        MvcTestResult confirmacao = confirmar(token);
        assertThat(confirmacao).hasStatusOk().bodyJson().extractingPath("tipo").isEqualTo("TROCA_DE_EMAIL");
        assertThat(confirmacao).bodyJson().extractingPath("email").isEqualTo(novo);
        assertThat(entrar(novo, usuario.senha())).hasStatusOk();
        assertThat(entrar(usuario.email(), usuario.senha())).hasStatus(401);
        assertThat(mvc.post().uri("/api/auth/renovar").cookie(new Cookie("flux_renovacao", refresh))).hasStatusOk();
        assertThat(me(usuario)).bodyJson().extractingPath("email").isEqualTo(novo);
        assertThat(me(usuario)).bodyJson().extractingPath("trocaDeEmailPendente").isNull();
    }

    @Test
    void rejeitaConfirmacaoQuandoOutroUsuarioOcupaEmailPendente() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        String novo = emailUnico();
        assertThat(trocar(usuario, novo, usuario.senha())).hasStatus(202);
        String token = CaixaDeEntrada.token(caixaDeEntrada.mensagensPara(novo, "Confirme seu novo e-mail no Flux").getFirst());
        usuarioService.criar("Outra Pessoa", novo, "senha-de-teste-123");

        assertThat(confirmar(token)).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Este e-mail já está em uso por outra conta.");
    }

    private MvcTestResult me(UsuarioDeTeste usuario) {
        return mvc.get().uri("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, bearer(usuario)).exchange();
    }

    private MvcTestResult trocar(UsuarioDeTeste usuario, String email, String senha) {
        return mvc.post().uri("/api/usuarios/me/email").header(HttpHeaders.AUTHORIZATION, bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"novoEmail\":\"" + email + "\",\"senhaAtual\":\"" + senha + "\"}").exchange();
    }

    private MvcTestResult confirmar(String token) {
        return mvc.post().uri("/api/auth/confirmar-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}").exchange();
    }

    private MvcTestResult entrar(String email, String senha) {
        return mvc.post().uri("/api/auth/entrar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}").exchange();
    }

    private static String cookie(MvcTestResult resposta) {
        String setCookie = resposta.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        return setCookie.substring("flux_renovacao=".length(), setCookie.indexOf(';'));
    }

    private static String bearer(UsuarioDeTeste usuario) {
        return "Bearer " + usuario.tokenAcesso();
    }

    private static String emailUnico() {
        return "troca-" + UUID.randomUUID() + "@teste.local";
    }
}
