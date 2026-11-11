package com.flux.categorizacao.interno;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decisões 23 a 25 da 04b: a regra é uso da categoria (RESTRICT, com o 422 que oferece arquivar); a regra
 * só de uma conta sai com ela, e as outras são renumeradas; excluir o usuário apaga as regras dele.
 */
class ExclusaoComRegrasIT extends RegrasBase {

    @Test
    void categoriaComRegraNaoSeExcluiEDizQuantasRegras() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long transporte = idDaCategoria(usuario, "Transporte");
        long app = idDaCategoria(usuario, "App de transporte");
        long lazer = idDaCategoria(usuario, "Lazer");
        manual(usuario, horizonte, "DESPESA", "10.00", "Uber", app);
        criarRegra(usuario, "CONTEM", "uber", null, app, null);
        criarRegra(usuario, "CONTEM", "posto", null, transporte, null);
        long cinema = criarRegra(usuario, "CONTEM", "cinema", null, lazer, null);

        MvcTestResult transporteBloqueado = api.delete(usuario, "/api/categorias/{id}", transporte);
        assertThat(transporteBloqueado).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("CATEGORIA_EM_USO");
        assertThat(transporteBloqueado).bodyJson().extractingPath("uso").isEqualTo("1 lançamento e 2 regras");
        MvcTestResult lazerBloqueado = api.delete(usuario, "/api/categorias/{id}", lazer);
        assertThat(lazerBloqueado).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Esta categoria não pode ser excluída porque tem 1 regra. Arquive a categoria para tirá-la dos seletores.");

        assertThat(api.delete(usuario, "/api/regras/{id}", cinema)).hasStatus(204);
        assertThat(api.delete(usuario, "/api/categorias/{id}", lazer)).hasStatus(204);
    }

    @Test
    void excluirAContaLevaAsRegrasSoDelaERenumeraAsOutras() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long poupanca = novaConta(usuario, "Poupança");
        long app = idDaCategoria(usuario, "App de transporte");
        long rendimentos = idDaCategoria(usuario, "Rendimentos");
        long uber = criarRegra(usuario, "CONTEM", "uber", null, app, null);
        criarRegra(usuario, "COMECA_COM", "rend pago", poupanca, rendimentos, null);
        long taxi = criarRegra(usuario, "CONTEM", "taxi", horizonte, app, null);

        assertThat(api.delete(usuario, "/api/contas/{id}", poupanca)).hasStatus(204);

        MvcTestResult lista = api.get(usuario, "/api/regras");
        assertThat(lista).bodyJson().extractingPath("$[*].id").asArray().containsExactly((int) taxi, (int) uber);
        assertThat(lista).bodyJson().extractingPath("$[*].posicao").asArray().containsExactly(1, 2);
    }

    @Test
    void excluirOUsuarioApagaAsRegrasDeleESoDele() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        UsuarioDeTeste outro = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long app = idDaCategoria(usuario, "App de transporte");
        criarRegra(usuario, "CONTEM", "uber", null, app, null);
        criarRegra(usuario, "CONTEM", "taxi", horizonte, app, null);
        manual(usuario, horizonte, "DESPESA", "10.00", "Uber", app);
        criarRegra(outro, "CONTEM", "uber", null, idDaCategoria(outro, "App de transporte"), null);

        assertThat(mvc.delete().uri("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso())
                .contentType(MediaType.APPLICATION_JSON).content("{\"senhaAtual\":\"" + usuario.senha() + "\"}")).hasStatus(204);

        assertThat(jdbc.queryForObject("select count(*) from regras_categorizacao where usuario_id = ?", Integer.class, usuario.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from regras_categorizacao where usuario_id = ?", Integer.class, outro.id())).isEqualTo(1);
    }
}
