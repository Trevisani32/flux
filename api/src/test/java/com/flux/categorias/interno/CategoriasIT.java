package com.flux.categorias.interno;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

import static com.flux.suporte.ApiDeTeste.assertCampo;
import static com.flux.suporte.ApiDeTeste.categoria;
import static com.flux.suporte.ApiDeTeste.edicaoDeCategoria;
import static org.assertj.core.api.Assertions.assertThat;

class CategoriasIT extends IntegracaoBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void listaAsDuasNaturezasEmOrdemComAsSubcategoriasDentro() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();

        JsonNode lista = api.json(api.get(usuario, "/api/categorias"));

        JsonNode despesas = lista.get("despesas").get("ativas");
        assertThat(nomes(despesas)).containsExactly("Alimentação", "Assinaturas", "Compras", "Educação", "Impostos e taxas",
                "Lazer", "Moradia", "Outras despesas", "Saúde", "Transporte");
        assertThat(nomes(despesas.get(0).get("subcategorias"))).containsExactly("Delivery", "Mercado", "Restaurante");
        assertThat(nomes(despesas.get(6).get("subcategorias"))).containsExactly("Aluguel", "Condomínio", "Contas da casa");
        assertThat(nomes(despesas.get(9).get("subcategorias"))).containsExactly("App de transporte", "Combustível", "Transporte público");
        assertThat(nomes(lista.get("receitas").get("ativas")))
                .containsExactly("Freelance", "Outras receitas", "Reembolsos", "Rendimentos", "Salário");
        assertThat(lista.get("despesas").get("arquivadas").size()).isZero();
        assertThat(lista.get("receitas").get("arquivadas").size()).isZero();
        JsonNode mercado = despesas.get(0).get("subcategorias").get(1);
        assertThat(mercado.get("categoriaMaeId").asLong()).isEqualTo(despesas.get(0).get("id").asLong());
        assertThat(mercado.get("nomeDaMae").asString()).isEqualTo("Alimentação");
        assertThat(mercado.get("natureza").asString()).isEqualTo("DESPESA");
        assertThat(mercado.get("subcategorias").size()).isZero();
        assertThat(despesas.get(0).get("nomeDaMae").isNull()).isTrue();
        assertThat(despesas.get(0).get("categoriaMaeId").isNull()).isTrue();
    }

    @Test
    void criaPrincipalESubcategoria() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long alimentacao = id(usuario, "Alimentação");

        MvcTestResult pets = api.post(usuario, "/api/categorias", categoria("  Pets ", "DESPESA", null));
        assertThat(pets).hasStatus(201);
        assertThat(pets.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/categorias/" + api.json(pets).get("id").asLong());
        assertThat(api.json(pets).get("nome").asString()).isEqualTo("Pets");
        assertThat(api.json(pets).get("categoriaMaeId").isNull()).isTrue();

        MvcTestResult padaria = api.post(usuario, "/api/categorias", categoria("Padaria", "DESPESA", alimentacao));
        assertThat(padaria).hasStatus(201);
        assertThat(api.json(padaria).get("categoriaMaeId").asLong()).isEqualTo(alimentacao);
        assertThat(api.json(padaria).get("nomeDaMae").asString()).isEqualTo("Alimentação");
        assertThat(nomes(api.json(api.get(usuario, "/api/categorias/{id}", alimentacao)).get("subcategorias")))
                .containsExactly("Delivery", "Mercado", "Padaria", "Restaurante");
    }

    @Test
    void validaOsCampos() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();

        assertCampo(api.post(usuario, "/api/categorias", categoria("  ", "DESPESA", null)), "nome", "Informe o nome.");
        assertCampo(api.post(usuario, "/api/categorias", categoria("a".repeat(61), "DESPESA", null)), "nome", "Use no máximo 60 caracteres.");
        assertCampo(api.post(usuario, "/api/categorias", "{\"nome\":\"Sem natureza\"}"), "natureza", "Escolha a natureza.");
        assertThat(api.post(usuario, "/api/categorias", categoria("Errada", "XPTO", null))).hasStatus(400)
                .bodyJson().extractingPath("title").isEqualTo("Requisição inválida");
        assertCampo(api.put(usuario, "/api/categorias/{id}", edicaoDeCategoria("", null), id(usuario, "Lazer")), "nome", "Informe o nome.");
    }

    @Test
    void conferemACategoriaPrincipalEscolhida() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long educacao = id(usuario, "Educação");
        api.acao(usuario, "/api/categorias/{id}/arquivar", educacao);

        assertRegra(api.post(usuario, "/api/categorias", categoria("X", "DESPESA", Long.MAX_VALUE)), "Categoria não encontrada.");
        assertRegra(api.post(usuario, "/api/categorias", categoria("X", "DESPESA", id(usuario, "Mercado"))),
                "Uma subcategoria não pode ter subcategorias.");
        assertRegra(api.post(usuario, "/api/categorias", categoria("X", "DESPESA", id(usuario, "Salário"))),
                "A natureza precisa ser a mesma de Salário.");
        assertRegra(api.post(usuario, "/api/categorias", categoria("X", "DESPESA", educacao)), "Escolha uma categoria principal ativa.");
    }

    @Test
    void nomeUnicoEntreAsIrmasAtivas() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long alimentacao = id(usuario, "Alimentação");

        MvcTestResult repetida = api.post(usuario, "/api/categorias", categoria("  MERCADO ", "DESPESA", alimentacao));
        assertThat(repetida).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("NOME_EM_USO");
        assertThat(repetida).bodyJson().extractingPath("detail").isEqualTo("Já existe uma categoria ativa com este nome em Alimentação.");
        assertThat(api.post(usuario, "/api/categorias", categoria("Mercado", "DESPESA", null))).hasStatus(201);
        assertThat(api.post(usuario, "/api/categorias", categoria("mercado", "DESPESA", null))).hasStatus(422)
                .bodyJson().extractingPath("detail").isEqualTo("Já existe uma categoria ativa com este nome entre as categorias principais.");
        assertThat(api.post(usuario, "/api/categorias", categoria("Mercado", "RECEITA", null))).hasStatus(201);
        assertThat(api.post(usuario, "/api/categorias", categoria("Outros", "DESPESA", id(usuario, "Moradia")))).hasStatus(201);
        assertThat(api.post(usuario, "/api/categorias", categoria("Outros", "DESPESA", id(usuario, "Lazer")))).hasStatus(201);
    }

    @Test
    void editarRenomeiaEMudaDePrincipalDentroDaMesmaNatureza() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long restaurante = id(usuario, "Restaurante");
        long delivery = id(usuario, "Delivery");
        long lazer = id(usuario, "Lazer");
        long alimentacao = id(usuario, "Alimentação");

        assertThat(api.put(usuario, "/api/categorias/{id}", edicaoDeCategoria("Restaurantes", alimentacao), restaurante))
                .hasStatusOk().bodyJson().extractingPath("nome").isEqualTo("Restaurantes");
        assertThat(api.put(usuario, "/api/categorias/{id}", edicaoDeCategoria("mercado", alimentacao), restaurante)).hasStatus(422)
                .bodyJson().extractingPath("detail").isEqualTo("Já existe uma categoria ativa com este nome em Alimentação.");
        MvcTestResult movida = api.put(usuario, "/api/categorias/{id}", edicaoDeCategoria("Delivery", lazer), delivery);
        assertThat(movida).hasStatusOk();
        assertThat(api.json(movida).get("nomeDaMae").asString()).isEqualTo("Lazer");
        assertThat(api.put(usuario, "/api/categorias/{id}", edicaoDeCategoria("Delivery", null), delivery)).hasStatusOk()
                .bodyJson().extractingPath("categoriaMaeId").isNull();
        assertThat(api.put(usuario, "/api/categorias/{id}", edicaoDeCategoria("Saúde", id(usuario, "Moradia")), id(usuario, "Saúde")))
                .hasStatusOk();

        assertRegra(api.put(usuario, "/api/categorias/{id}", edicaoDeCategoria("Alimentação", lazer), alimentacao),
                "Uma categoria com subcategorias não pode ficar dentro de outra.");
        assertRegra(api.put(usuario, "/api/categorias/{id}", edicaoDeCategoria("Lazer", lazer), lazer),
                "Uma categoria não pode ficar dentro dela mesma.");
        assertRegra(api.put(usuario, "/api/categorias/{id}", edicaoDeCategoria("Lazer", id(usuario, "Salário")), lazer),
                "A natureza precisa ser a mesma de Salário.");

        long viagem = api.idCriado(api.post(usuario, "/api/categorias", categoria("Viagem", "DESPESA", null)));
        long hotel = api.idCriado(api.post(usuario, "/api/categorias", categoria("Hotel", "DESPESA", viagem)));
        api.acao(usuario, "/api/categorias/{id}/arquivar", hotel);
        assertRegra(api.put(usuario, "/api/categorias/{id}", edicaoDeCategoria("Viagem", lazer), viagem),
                "Uma categoria com subcategorias não pode ficar dentro de outra.");

        api.acao(usuario, "/api/categorias/{id}/arquivar", viagem);
        MvcTestResult arquivada = api.put(usuario, "/api/categorias/{id}", edicaoDeCategoria("Viagens", null), viagem);
        assertThat(arquivada).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("CATEGORIA_ARQUIVADA");
        assertThat(arquivada).bodyJson().extractingPath("detail").isEqualTo("Reative a categoria antes de editar.");
    }

    @Test
    void arquivarAPrincipalLevaAsAtivasEReativarTrazSoElas() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long transporte = id(usuario, "Transporte");
        long app = id(usuario, "App de transporte");
        long combustivel = id(usuario, "Combustível");
        api.acao(usuario, "/api/categorias/{id}/arquivar", app);

        MvcTestResult arquivada = api.acao(usuario, "/api/categorias/{id}/arquivar", transporte);

        assertThat(arquivada).hasStatusOk();
        assertThat(nomes(api.json(arquivada).get("subcategorias")))
                .containsExactly("App de transporte", "Combustível", "Transporte público");
        assertThat(jdbc.queryForList("select nome || ':' || arquivada_com_a_mae from categorias where categoria_mae_id = ? order by nome",
                String.class, transporte)).containsExactly("App de transporte:false", "Combustível:true", "Transporte público:true");
        assertThat(jdbc.queryForObject("select count(distinct arquivada_em) from categorias where id = ? or (categoria_mae_id = ? "
                + "and arquivada_com_a_mae)", Integer.class, transporte, transporte)).isEqualTo(1);
        JsonNode despesas = api.json(api.get(usuario, "/api/categorias")).get("despesas");
        assertThat(nomes(despesas.get("ativas"))).doesNotContain("Transporte");
        assertThat(nomes(despesas.get("arquivadas"))).containsExactly("Transporte");

        MvcTestResult filhaSozinha = api.acao(usuario, "/api/categorias/{id}/reativar", combustivel);
        assertThat(filhaSozinha).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("MAE_ARQUIVADA");
        assertThat(filhaSozinha).bodyJson().extractingPath("detail").isEqualTo("Reative antes a categoria Transporte.");

        MvcTestResult reativada = api.acao(usuario, "/api/categorias/{id}/reativar", transporte);
        assertThat(reativada).hasStatusOk();
        assertThat(nomes(api.json(reativada).get("subcategorias"))).containsExactly("Combustível", "Transporte público");
        despesas = api.json(api.get(usuario, "/api/categorias")).get("despesas");
        assertThat(nomes(despesas.get("arquivadas"))).containsExactly("App de transporte");
        assertThat(despesas.get("arquivadas").get(0).get("nomeDaMae").asString()).isEqualTo("Transporte");
        assertThat(api.acao(usuario, "/api/categorias/{id}/reativar", app)).hasStatusOk();
        assertThat(jdbc.queryForObject("select count(*) from categorias where usuario_id = ? and (arquivada_em is not null "
                + "or arquivada_com_a_mae)", Integer.class, usuario.id())).isZero();
    }

    @Test
    void reativarComONomeEmUsoNaoReativa() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long saude = id(usuario, "Saúde");
        api.acao(usuario, "/api/categorias/{id}/arquivar", saude);
        assertThat(api.post(usuario, "/api/categorias", categoria("saude", "DESPESA", null))).hasStatus(201);

        MvcTestResult conflito = api.acao(usuario, "/api/categorias/{id}/reativar", saude);

        assertThat(conflito).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("NOME_EM_USO");
        assertThat(conflito).bodyJson().extractingPath("detail").isEqualTo(
                "Já existe uma categoria ativa com este nome entre as categorias principais. Renomeie a categoria ativa e tente de novo.");
        assertThat(jdbc.queryForObject("select arquivada_em is not null from categorias where id = ?", Boolean.class, saude)).isTrue();
    }

    @Test
    void excluirAPrincipalLevaAsSubcategorias() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long moradia = id(usuario, "Moradia");
        long aluguel = id(usuario, "Aluguel");
        long educacao = id(usuario, "Educação");
        api.acao(usuario, "/api/categorias/{id}/arquivar", educacao);

        assertThat(api.delete(usuario, "/api/categorias/{id}", moradia)).hasStatus(204);
        assertThat(api.delete(usuario, "/api/categorias/{id}", id(usuario, "Mercado"))).hasStatus(204);
        assertThat(api.delete(usuario, "/api/categorias/{id}", educacao)).hasStatus(204);

        assertThat(api.get(usuario, "/api/categorias/{id}", aluguel)).hasStatus(404)
                .bodyJson().extractingPath("detail").isEqualTo("Categoria não encontrada.");
        assertThat(api.delete(usuario, "/api/categorias/{id}", moradia)).hasStatus(404);
        assertThat(jdbc.queryForObject("select count(*) from categorias where usuario_id = ?", Integer.class, usuario.id()))
                .isEqualTo(24 - 4 - 1 - 1);
        assertThat(nomes(api.json(api.get(usuario, "/api/categorias/{id}", id(usuario, "Alimentação"))).get("subcategorias")))
                .containsExactly("Delivery", "Restaurante");
    }

    @Test
    void limiteDe200CategoriasContaAsArquivadas() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        for (int i = 1; i <= 175; i++) {
            jdbc.update("insert into categorias (usuario_id, natureza, nome, nome_chave, arquivada_em) "
                    + "values (?, 'DESPESA', ?, ?, case when ? % 2 = 0 then now() end)", usuario.id(), "Extra " + i, "extra " + i, i);
        }

        assertThat(api.post(usuario, "/api/categorias", categoria("Ducentésima", "RECEITA", null))).hasStatus(201);
        MvcTestResult excedeu = api.post(usuario, "/api/categorias", categoria("Mais uma", "RECEITA", null));
        assertThat(excedeu).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("LIMITE_DE_CATEGORIAS");
        assertThat(excedeu).bodyJson().extractingPath("detail").isEqualTo("Você chegou ao limite de 200 categorias.");
    }

    @Test
    void doisPedidosSimultaneosComOMesmoNomeCriamUmaCategoriaSo() throws Exception {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> pedidos = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                pedidos.add(executor.submit(() -> {
                    largada.await();
                    return api.post(usuario, "/api/categorias", categoria("Simultânea", "DESPESA", null)).getResponse().getStatus();
                }));
            }
            largada.countDown();
            List<Integer> status = new ArrayList<>();
            for (Future<Integer> pedido : pedidos) {
                status.add(pedido.get());
            }
            Collections.sort(status);
            assertThat(status).containsExactly(201, 422);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from categorias where usuario_id = ? and nome = 'Simultânea'",
                Integer.class, usuario.id())).isEqualTo(1);
    }

    @Test
    void semTokenDa401() {
        assertThat(mvc.get().uri("/api/categorias")).hasStatus(401);
    }

    private long id(UsuarioDeTeste usuario, String nome) {
        return jdbc.queryForObject("select id from categorias where usuario_id = ? and nome = ?", Long.class, usuario.id(), nome);
    }

    private static void assertRegra(MvcTestResult resposta, String mensagem) {
        assertThat(resposta).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo(mensagem);
    }

    private static List<String> nomes(JsonNode categorias) {
        List<String> nomes = new ArrayList<>();
        categorias.forEach(categoria -> nomes.add(categoria.get("nome").asString()));
        return nomes;
    }
}
