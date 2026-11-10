package com.flux.contas.interno;

import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.flux.suporte.ApiDeTeste.conta;
import static org.assertj.core.api.Assertions.assertThat;

/** ADR 0009: excluir a conta de usuário apaga as contas dele (ativas e arquivadas) e só as dele. */
class ExclusaoDoUsuarioComContasIT extends IntegracaoBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void excluirOUsuarioApagaAsContasDeleESoDele() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        UsuarioDeTeste outro = usuariosDeTeste.criar();
        api.idCriado(api.post(usuario, "/api/contas", conta("Ativa", "CORRENTE", "10", "2026-10-01", "null")));
        long arquivada = api.idCriado(api.post(usuario, "/api/contas", conta("Arquivada", "POUPANCA", "0", "2026-10-01", "null")));
        api.acao(usuario, "/api/contas/{id}/arquivar", arquivada);
        api.idCriado(api.post(outro, "/api/contas", conta("Do outro", "CARTEIRA", "5", "2026-10-01", "null")));

        assertThat(mvc.delete().uri("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso())
                .contentType(MediaType.APPLICATION_JSON).content("{\"senhaAtual\":\"" + usuario.senha() + "\"}")).hasStatus(204);

        assertThat(jdbc.queryForObject("select count(*) from contas where usuario_id = ?", Integer.class, usuario.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from contas where usuario_id = ?", Integer.class, outro.id())).isEqualTo(1);
    }
}
