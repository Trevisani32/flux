package com.flux.categorias.interno;

import java.util.List;
import java.util.function.Function;

import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.flux.suporte.ApiDeTeste.categoria;
import static com.flux.suporte.ApiDeTeste.edicaoDeCategoria;
import static com.flux.suporte.ApiDeTeste.texto;
import static org.assertj.core.api.Assertions.assertThat;

/** ADR 0007: a categoria de outro usuário é tratada como inexistente, na URL (404) e no corpo (422). */
class IsolamentoDeCategoriasIT extends IntegracaoBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void outroUsuarioNaoListaNaoLeNaoEditaNaoArquivaNaoReativaNemExclui() {
        UsuarioDeTeste a = usuariosDeTeste.criar();
        UsuarioDeTeste b = usuariosDeTeste.criar();
        long alimentacaoDaA = id(a, "Alimentação");
        long mercadoDaA = id(a, "Mercado");

        String listaDeB = texto(api.get(b, "/api/categorias"));
        assertThat(listaDeB).doesNotContain("\"id\":" + alimentacaoDaA + ",", "\"id\":" + mercadoDaA + ",");
        List<Function<Long, MvcTestResult>> pedidosDeB = List.of(
                id -> api.get(b, "/api/categorias/{id}", id),
                id -> api.put(b, "/api/categorias/{id}", edicaoDeCategoria("Invadida", null), id),
                id -> api.acao(b, "/api/categorias/{id}/arquivar", id),
                id -> api.acao(b, "/api/categorias/{id}/reativar", id),
                id -> api.delete(b, "/api/categorias/{id}", id));
        for (Function<Long, MvcTestResult> pedido : pedidosDeB) {
            MvcTestResult deOutro = pedido.apply(alimentacaoDaA);
            MvcTestResult inexistente = pedido.apply(Long.MAX_VALUE);
            assertThat(deOutro).hasStatus(404).bodyJson().extractingPath("detail").isEqualTo("Categoria não encontrada.");
            assertThat(api.json(deOutro).get("title")).isEqualTo(api.json(inexistente).get("title"));
            assertThat(api.json(deOutro).get("detail")).isEqualTo(api.json(inexistente).get("detail"));
        }

        MvcTestResult comMaeDeOutro = api.post(b, "/api/categorias", categoria("Invasora", "DESPESA", alimentacaoDaA));
        MvcTestResult comMaeInexistente = api.post(b, "/api/categorias", categoria("Invasora", "DESPESA", Long.MAX_VALUE));
        assertThat(comMaeDeOutro).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Categoria não encontrada.");
        assertThat(api.json(comMaeDeOutro).get("detail")).isEqualTo(api.json(comMaeInexistente).get("detail"));
        assertThat(api.put(b, "/api/categorias/{id}", edicaoDeCategoria("Lazer", alimentacaoDaA), id(b, "Lazer"))).hasStatus(422)
                .bodyJson().extractingPath("detail").isEqualTo("Categoria não encontrada.");

        assertThat(jdbc.queryForObject("select count(*) from categorias where usuario_id = ? and arquivada_em is null",
                Integer.class, a.id())).isEqualTo(24);
        assertThat(jdbc.queryForObject("select nome from categorias where id = ?", String.class, alimentacaoDaA)).isEqualTo("Alimentação");
        assertThat(jdbc.queryForObject("select count(*) from categorias where categoria_mae_id = ?", Integer.class, alimentacaoDaA))
                .isEqualTo(3);
    }

    private long id(UsuarioDeTeste usuario, String nome) {
        return jdbc.queryForObject("select id from categorias where usuario_id = ? and nome = ?", Long.class, usuario.id(), nome);
    }
}
