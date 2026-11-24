package com.flux.categorizacao.interno;

import java.time.LocalDate;
import java.time.YearMonth;

import com.flux.suporte.ApiDeTeste;
import com.flux.suporte.CartoesBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.flux.suporte.ApiDeTeste.assertCampo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fatia 05c (decisões 27 e 44): o filtro da regra é uma conta ou um cartão, nunca os dois; a regra do cartão só pega as
 * compras e os estornos dele, e a sem filtro pega contas e cartões; "Aplicar regras" categoriza a compra inteira, com
 * todas as parcelas; o cartão arquivado pausa a regra, e excluir o cartão tira as regras dele.
 */
class RegrasComCartaoIT extends CartoesBase {

    private static String regra(String texto, Long contaId, Long cartaoId, long categoriaId, String descricao) {
        return "{\"condicao\":\"CONTEM\",\"texto\":\"" + texto + "\",\"contaId\":" + contaId + ",\"cartaoId\":" + cartaoId
                + ",\"categoriaId\":" + categoriaId + ",\"descricao\":" + (descricao == null ? "null" : "\"" + descricao + "\"") + "}";
    }

    /** Uma compra do mês passado, dia 10. */
    private String data() {
        return YearMonth.from(LocalDate.now(relogioDeTeste)).minusMonths(1).atDay(10).toString();
    }

    @Test
    void aRegraTemUmaContaOuUmCartaoENuncaOsDois() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        UsuarioDeTeste outro = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, conta);
        long lua = novoCartao(usuario, "Cartão Lua", 25, 5, conta);
        long deOutro = novoCartao(outro, "Cartão Alheio", 3, 10, novaConta(outro, "Conta Alheia"));
        long educacao = idDaCategoria(usuario, "Educação");

        MvcTestResult criada = api.post(usuario, "/api/regras", regra("curso", null, horizonte, educacao, null));

        assertThat(criada).hasStatus(201).bodyJson().extractingPath("cartaoId").isEqualTo((int) horizonte);
        assertThat(criada).bodyJson().extractingPath("cartaoNome").isEqualTo("Cartão Horizonte");
        assertThat(criada).bodyJson().extractingPath("contaId").isNull();
        assertThat(criada).bodyJson().extractingPath("pausada").isEqualTo(false);
        assertCampo(api.post(usuario, "/api/regras", regra("curso", conta, horizonte, educacao, null)), "cartaoId",
                "Escolha uma conta ou um cartão, não os dois.");
        MvcTestResult alheio = api.post(usuario, "/api/regras", regra("curso", null, deOutro, educacao, null));
        assertThat(alheio).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Cartão não encontrado.");
        assertThat(alheio).bodyJson().extractingPath("codigo").isEqualTo("CARTAO_INVALIDO");
        // A mesma condição em outro cartão, numa conta ou em todas é outra regra; no mesmo cartão, é a mesma.
        assertThat(api.post(usuario, "/api/regras", regra("curso", null, lua, educacao, null))).hasStatus(201);
        assertThat(api.post(usuario, "/api/regras", regra("curso", conta, null, educacao, null))).hasStatus(201);
        assertThat(api.post(usuario, "/api/regras", regra("curso", null, null, educacao, null))).hasStatus(201);
        assertThat(api.post(usuario, "/api/regras", regra("curso", null, horizonte, educacao, null))).hasStatus(422).bodyJson()
                .extractingPath("codigo").isEqualTo("REGRA_REPETIDA");
        assertThat(api.post(usuario, "/api/regras/previa", "{\"condicao\":\"CONTEM\",\"texto\":\"curso\",\"contaId\":null,\"cartaoId\":"
                + deOutro + ",\"categoriaId\":null}")).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Cartão não encontrado.");
    }

    @Test
    void aplicarRegrasCategorizaACompraInteiraDoCartaoAtivo() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, conta);
        long lua = novoCartao(usuario, "Cartão Lua", 25, 5, conta);
        long educacao = idDaCategoria(usuario, "Educação");
        long reembolsos = idDaCategoria(usuario, "Reembolsos");
        long curso = novaCompra(usuario, horizonte, data(), "Curso Online Ficticio", "300", 3);
        long daLua = novaCompra(usuario, lua, data(), "Curso de violao", "90", 1);
        long estorno = api.idCriado(api.post(usuario, "/api/estornos", estorno(horizonte, data(), "Estorno do curso", "50", null)));
        api.idCriado(api.post(usuario, "/api/regras", regra("curso", null, horizonte, educacao, null)));
        api.idCriado(api.post(usuario, "/api/regras", regra("estorno", null, null, reembolsos, null)));
        long daConta = api.idCriado(api.post(usuario, "/api/lancamentos", ApiDeTeste.lancamento("RECEITA", "10", data(), "Estorno da tarifa",
                conta, null)));

        // A prévia da regra conta a compra uma vez, com a data da compra e o valor total; a do cartão não pega a compra do outro.
        MvcTestResult previa = api.post(usuario, "/api/regras/previa", "{\"condicao\":\"CONTEM\",\"texto\":\"curso\",\"contaId\":null,"
                + "\"cartaoId\":" + horizonte + ",\"categoriaId\":" + educacao + "}");
        assertThat(previa).hasStatusOk().bodyJson().extractingPath("casam").isEqualTo(1);
        assertThat(previa).bodyJson().extractingPath("exemplos[0].valor").isEqualTo(-300.0);
        assertThat(previa).bodyJson().extractingPath("exemplos[0].data").isEqualTo(data());

        // Em "Aplicar regras", o exemplo da compra diz o cartão, a data da compra e o valor total; cada compra conta 1.
        MvcTestResult aplicacao = api.get(usuario, "/api/regras/aplicacao");
        assertThat(aplicacao).hasStatusOk().bodyJson().extractingPath("total").isEqualTo(3);
        assertThat(aplicacao).bodyJson().extractingPath("grupos[?(@.texto == 'curso')].quantidade").asArray().containsExactly(1);
        assertThat(aplicacao).bodyJson().extractingPath("grupos[?(@.texto == 'curso')].exemplos[0].contaNome").asArray()
                .containsExactly("Cartão Horizonte");
        assertThat(aplicacao).bodyJson().extractingPath("grupos[?(@.texto == 'curso')].exemplos[0].valor").asArray().containsExactly(-300.0);
        assertThat(aplicacao).bodyJson().extractingPath("grupos[?(@.texto == 'curso')].exemplos[0].data").asArray()
                .containsExactly(data());
        assertThat(aplicacao).bodyJson().extractingPath("grupos[?(@.texto == 'estorno')].exemplos[*].contaNome").asArray()
                .containsExactlyInAnyOrder("Cartão Horizonte", "Banco Horizonte");
        long regraDoCurso = jdbc.queryForObject("select id from regras_categorizacao where usuario_id = ? and texto = 'curso'", Long.class,
                usuario.id());
        long regraDoEstorno = jdbc.queryForObject("select id from regras_categorizacao where usuario_id = ? and texto = 'estorno'", Long.class,
                usuario.id());

        MvcTestResult aplicados = api.post(usuario, "/api/regras/aplicacao", "{\"regras\":[" + regraDoCurso + "," + regraDoEstorno + "]}");

        assertThat(aplicados).hasStatusOk().bodyJson().extractingPath("categorizados").isEqualTo(3);
        assertThat(aplicados).bodyJson().extractingPath("compras").isEqualTo(2);
        assertThat(jdbc.queryForObject("select categoria_id from lancamentos where id = ?", Long.class, daConta)).isEqualTo(reembolsos);
        assertThat(jdbc.queryForList("select categoria_id from lancamentos where compra_id = ?", Long.class, curso))
                .containsExactly(educacao, educacao, educacao);
        assertThat(jdbc.queryForList("select categoria_id from lancamentos where compra_id = ?", Long.class, estorno)).containsExactly(reembolsos);
        assertThat(jdbc.queryForObject("select count(*) from lancamentos where compra_id = ? and categoria_id is null", Integer.class, daLua))
                .isEqualTo(1);

        // O cartão arquivado pausa a regra dele, e a compra dele fica de fora do "Aplicar".
        novaCompra(usuario, horizonte, data(), "Curso extra", "40", 1);
        assertThat(api.get(usuario, "/api/regras/aplicacao")).bodyJson().extractingPath("total").isEqualTo(1);
        api.acao(usuario, "/api/cartoes/{id}/arquivar", horizonte);
        assertThat(api.get(usuario, "/api/regras")).bodyJson().extractingPath("[?(@.texto == 'curso')].pausada").asArray()
                .containsExactly(true);
        assertThat(api.get(usuario, "/api/regras/aplicacao")).bodyJson().extractingPath("total").isEqualTo(0);
    }

    @Test
    void excluirOCartaoTiraAsRegrasDeleERenumeraAsOutras() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, conta);
        long educacao = idDaCategoria(usuario, "Educação");
        api.idCriado(api.post(usuario, "/api/regras", regra("padaria", null, null, educacao, null)));
        api.idCriado(api.post(usuario, "/api/regras", regra("curso", null, horizonte, educacao, null)));
        api.idCriado(api.post(usuario, "/api/regras", regra("livro", conta, null, educacao, null)));

        assertThat(api.delete(usuario, "/api/cartoes/{id}", horizonte)).hasStatus(204);

        assertThat(jdbc.queryForList("select posicao || ' ' || texto from regras_categorizacao where usuario_id = ? order by posicao",
                String.class, usuario.id())).containsExactly("1 livro", "2 padaria");
    }

    @Test
    void asSugestoesDeRegraAceitamOCartao() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        UsuarioDeTeste outro = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, conta);
        long deOutro = novoCartao(outro, "Cartão Alheio", 3, 10, novaConta(outro, "Conta Alheia"));
        long educacao = idDaCategoria(usuario, "Educação");

        MvcTestResult sugestoes = api.post(usuario, "/api/regras/sugestoes", "{\"itens\":[{\"contaId\":null,\"cartaoId\":" + horizonte
                + ",\"descricaoBanco\":\"CURSO ONLINE FICTICIO\",\"descricao\":null,\"categoriaId\":" + educacao + "}]}");

        assertThat(sugestoes).hasStatusOk().bodyJson().extractingPath("[0].texto").isEqualTo("CURSO ONLINE FICTICIO");
        // Com uma regra ativa do cartão que já casa, não há sugestão.
        api.idCriado(api.post(usuario, "/api/regras", regra("curso", null, horizonte, educacao, null)));
        assertThat(api.post(usuario, "/api/regras/sugestoes", "{\"itens\":[{\"contaId\":null,\"cartaoId\":" + horizonte
                + ",\"descricaoBanco\":\"CURSO ONLINE FICTICIO\",\"descricao\":null,\"categoriaId\":" + educacao + "}]}"))
                .bodyJson().extractingPath("$.length()").isEqualTo(0);
        assertThat(api.post(usuario, "/api/regras/sugestoes", "{\"itens\":[{\"contaId\":null,\"cartaoId\":" + deOutro
                + ",\"descricaoBanco\":\"CURSO\",\"descricao\":null,\"categoriaId\":" + educacao + "}]}"))
                .hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Cartão não encontrado.");
    }
}
