package com.flux.acesso.interno;

import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

class ExclusaoDaContaIT extends IntegracaoBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void senhaErradaNaoApagaConta() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();

        assertThat(excluir(usuario, "errada")).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Senha atual incorreta.");
        assertThat(jdbc.queryForObject("select count(*) from usuarios where id = ?", Integer.class, usuario.id())).isEqualTo(1);
    }

    @Test
    void senhaCertaApagaContaTokensEEnviaAviso() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        assertThat(entrar(usuario)).hasStatusOk();
        assertThat(mvc.post().uri("/api/auth/esqueci-a-senha").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + usuario.email() + "\"}")).hasStatus(202);
        caixaDeEntrada.aguardarEnvios();
        assertThat(jdbc.queryForObject("select count(*) from tokens_renovacao where usuario_id = ?", Integer.class, usuario.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from tokens_email where usuario_id = ?", Integer.class, usuario.id())).isEqualTo(1);

        MvcTestResult resposta = excluir(usuario, usuario.senha());

        assertThat(resposta).hasStatus(204).containsHeader(HttpHeaders.SET_COOKIE);
        assertThat(resposta.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        assertThat(jdbc.queryForObject("select count(*) from usuarios where id = ?", Integer.class, usuario.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from tokens_renovacao where usuario_id = ?", Integer.class, usuario.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from tokens_email where usuario_id = ?", Integer.class, usuario.id())).isZero();
        assertThat(caixaDeEntrada.mensagensPara(usuario.email(), "Sua conta do Flux foi excluída").getFirst().texto())
                .contains("apagados em", "fale com privacidade@teste.local");
        assertThat(jdbc.queryForObject("select count(*) from event_publication where serialized_event like ?", Integer.class,
                "%" + usuario.email() + "%")).isZero();
        assertThat(entrar(usuario)).hasStatus(401);
        assertThat(mvc.get().uri("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso()))
                .hasStatus(404).bodyJson().extractingPath("detail").isEqualTo("Usuário não encontrado.");
    }

    private MvcTestResult excluir(UsuarioDeTeste usuario, String senha) {
        return mvc.delete().uri("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso())
                .contentType(MediaType.APPLICATION_JSON).content("{\"senhaAtual\":\"" + senha + "\"}").exchange();
    }

    private MvcTestResult entrar(UsuarioDeTeste usuario) {
        return mvc.post().uri("/api/auth/entrar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + usuario.email() + "\",\"senha\":\"" + usuario.senha() + "\"}").exchange();
    }
}
