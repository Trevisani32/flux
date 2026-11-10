package com.flux.categorias.interno;

import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.flux.suporte.ApiDeTeste.categoria;
import static org.assertj.core.api.Assertions.assertThat;

/** ADR 0009: excluir a conta de usuário apaga as categorias dele (padrão, criadas e arquivadas) e só as dele. */
class ExclusaoDoUsuarioComCategoriasIT extends IntegracaoBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void excluirOUsuarioApagaAsCategoriasDeleESoDele() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        UsuarioDeTeste outro = usuariosDeTeste.criar();
        long pets = api.idCriado(api.post(usuario, "/api/categorias", categoria("Pets", "DESPESA", null)));
        api.idCriado(api.post(usuario, "/api/categorias", categoria("Ração", "DESPESA", pets)));
        api.acao(usuario, "/api/categorias/{id}/arquivar", pets);
        assertThat(jdbc.queryForObject("select count(*) from categorias where usuario_id = ?", Integer.class, usuario.id())).isEqualTo(26);

        assertThat(mvc.delete().uri("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso())
                .contentType(MediaType.APPLICATION_JSON).content("{\"senhaAtual\":\"" + usuario.senha() + "\"}")).hasStatus(204);

        assertThat(jdbc.queryForObject("select count(*) from categorias where usuario_id = ?", Integer.class, usuario.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from categorias where usuario_id = ?", Integer.class, outro.id())).isEqualTo(24);
    }
}
