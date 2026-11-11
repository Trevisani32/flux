package com.flux.categorizacao.interno;

import java.util.stream.Collectors;
import java.util.stream.LongStream;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.flux.suporte.ApiDeTeste.assertCampo;
import static org.assertj.core.api.Assertions.assertThat;

/** O cadastro das regras (decisões 5 a 11 da 04b): ordem, natureza pela categoria, validações e a prévia do diálogo. */
class RegrasIT extends RegrasBase {

    @Test
    void criaNoTopoListaEditaEExcluiRenumerando() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long app = idDaCategoria(usuario, "App de transporte");
        long salario = idDaCategoria(usuario, "Salário");
        long uber = criarRegra(usuario, "CONTEM", "  uber   ", null, app, "Uber");
        long sispag = criarRegra(usuario, "COMECA_COM", "SISPAG SALARIO", horizonte, salario, null);

        MvcTestResult lista = api.get(usuario, "/api/regras");
        assertThat(lista).hasStatusOk().bodyJson().extractingPath("$[*].id").asArray().containsExactly((int) sispag, (int) uber);
        assertThat(lista).bodyJson().extractingPath("$[*].posicao").asArray().containsExactly(1, 2);
        assertThat(lista).bodyJson().extractingPath("$[0].natureza").isEqualTo("RECEITA");
        assertThat(lista).bodyJson().extractingPath("$[0].contaNome").isEqualTo("Banco Horizonte");
        assertThat(lista).bodyJson().extractingPath("$[0].condicao").isEqualTo("COMECA_COM");
        assertThat(lista).bodyJson().extractingPath("$[1].texto").isEqualTo("uber");
        assertThat(lista).bodyJson().extractingPath("$[1].natureza").isEqualTo("DESPESA");
        assertThat(lista).bodyJson().extractingPath("$[1].categoriaNome").isEqualTo("App de transporte");
        assertThat(lista).bodyJson().extractingPath("$[1].categoriaMaeNome").isEqualTo("Transporte");
        assertThat(lista).bodyJson().extractingPath("$[1].contaId").isNull();
        assertThat(lista).bodyJson().extractingPath("$[1].descricao").isEqualTo("Uber");
        assertThat(lista).bodyJson().extractingPath("$[1].pausada").isEqualTo(false);

        MvcTestResult editada = api.put(usuario, "/api/regras/{id}", regra("IGUAL_A", "PAG*UBERTRIP", horizonte, app, "  "), uber);
        assertThat(editada).hasStatusOk().bodyJson().extractingPath("condicao").isEqualTo("IGUAL_A");
        assertThat(editada).bodyJson().extractingPath("posicao").isEqualTo(2);
        assertThat(editada).bodyJson().extractingPath("contaId").isEqualTo((int) horizonte);
        assertThat(editada).bodyJson().extractingPath("descricao").isNull();
        assertThat(jdbc.queryForObject("select texto_chave from regras_categorizacao where id = ?", String.class, uber))
                .isEqualTo("pag ubertrip");

        assertThat(api.delete(usuario, "/api/regras/{id}", sispag)).hasStatus(204);
        assertThat(api.get(usuario, "/api/regras")).bodyJson().extractingPath("$[*].posicao").asArray().containsExactly(1);
        assertThat(api.delete(usuario, "/api/regras/{id}", sispag)).hasStatus(404).bodyJson().extractingPath("detail")
                .isEqualTo("Regra não encontrada.");
    }

    @Test
    void validaTextoCategoriaContaERegraRepetida() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long poupanca = novaConta(usuario, "Poupança");
        long app = idDaCategoria(usuario, "App de transporte");
        long lazer = idDaCategoria(usuario, "Lazer");
        long reembolsos = idDaCategoria(usuario, "Reembolsos");

        assertCampo(api.post(usuario, "/api/regras", regra("CONTEM", "  ", null, app, null)), "texto", "Escreva o texto da regra.");
        assertCampo(api.post(usuario, "/api/regras", regra("CONTEM", "*", null, app, null)), "texto", "Use ao menos 2 letras ou números.");
        assertCampo(api.post(usuario, "/api/regras", regra("CONTEM", "a *", null, app, null)), "texto", "Use ao menos 2 letras ou números.");
        assertCampo(api.post(usuario, "/api/regras", regra(null, "uber", null, app, null)), "condicao", "Escolha a condição.");
        assertCampo(api.post(usuario, "/api/regras", regra("CONTEM", "uber", null, null, null)), "categoriaId", "Escolha a categoria.");
        assertCampo(api.post(usuario, "/api/regras", regra("CONTEM", "x".repeat(256), null, app, null)), "texto",
                "O texto pode ter até 255 caracteres.");
        assertCampo(api.post(usuario, "/api/regras", regra("CONTEM", "uber", null, app, "d".repeat(256))), "descricao",
                "A descrição pode ter até 255 caracteres.");
        assertThat(api.post(usuario, "/api/regras", regra("CONTEM", "uber", null, 999_999_999L, null))).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Categoria não encontrada.");
        assertThat(api.post(usuario, "/api/regras", regra("CONTEM", "uber", 999_999_999L, app, null))).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Conta não encontrada.");
        assertThat(api.acao(usuario, "/api/categorias/{id}/arquivar", lazer)).hasStatusOk();
        assertThat(api.post(usuario, "/api/regras", regra("CONTEM", "cinema", null, lazer, null))).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Escolha uma categoria ativa.");
        assertThat(api.acao(usuario, "/api/contas/{id}/arquivar", poupanca)).hasStatusOk();
        assertThat(api.post(usuario, "/api/regras", regra("CONTEM", "uber", poupanca, app, null))).hasStatus(422).bodyJson()
                .extractingPath("codigo").isEqualTo("CONTA_INVALIDA");

        criarRegra(usuario, "CONTEM", "UBER", null, app, null);
        MvcTestResult repetida = api.post(usuario, "/api/regras", regra("CONTEM", "uber*", null, app, null));
        assertThat(repetida).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Já existe uma regra igual: a 1ª, contém \"UBER\".");
        assertThat(repetida).bodyJson().extractingPath("codigo").isEqualTo("REGRA_REPETIDA");
        // Outra natureza, outra conta ou outra condição não repetem.
        criarRegra(usuario, "CONTEM", "uber", null, reembolsos, null);
        criarRegra(usuario, "CONTEM", "uber", horizonte, app, null);
        criarRegra(usuario, "COMECA_COM", "uber", null, app, null);
        assertThat(jdbc.queryForObject("select count(*) from regras_categorizacao where usuario_id = ?", Integer.class, usuario.id()))
                .isEqualTo(4);
    }

    @Test
    void categoriaOuContaArquivadaPausaARegraEAEdicaoMantemAArquivada() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long poupanca = novaConta(usuario, "Poupança");
        long lazer = idDaCategoria(usuario, "Lazer");
        long compras = idDaCategoria(usuario, "Compras");
        long rendimentos = idDaCategoria(usuario, "Rendimentos");
        long cinema = criarRegra(usuario, "CONTEM", "cinema", null, lazer, null);
        long rend = criarRegra(usuario, "COMECA_COM", "rend pago", poupanca, rendimentos, null);
        assertThat(api.acao(usuario, "/api/categorias/{id}/arquivar", lazer)).hasStatusOk();
        assertThat(api.acao(usuario, "/api/categorias/{id}/arquivar", compras)).hasStatusOk();
        assertThat(api.acao(usuario, "/api/contas/{id}/arquivar", poupanca)).hasStatusOk();

        MvcTestResult lista = api.get(usuario, "/api/regras");
        assertThat(lista).bodyJson().extractingPath("$[*].pausada").asArray().containsExactly(true, true);
        assertThat(lista).bodyJson().extractingPath("$[0].contaArquivada").isEqualTo(true);
        assertThat(lista).bodyJson().extractingPath("$[1].categoriaArquivada").isEqualTo(true);

        assertThat(api.put(usuario, "/api/regras/{id}", regra("CONTEM", "cinema e teatro", null, lazer, null), cinema)).hasStatusOk();
        assertThat(api.put(usuario, "/api/regras/{id}", regra("CONTEM", "cinema", null, compras, null), cinema)).hasStatus(422)
                .bodyJson().extractingPath("detail").isEqualTo("Escolha uma categoria ativa.");
        assertThat(api.put(usuario, "/api/regras/{id}", regra("COMECA_COM", "rend", poupanca, rendimentos, null), rend)).hasStatusOk();
        assertThat(api.acao(usuario, "/api/categorias/{id}/reativar", lazer)).hasStatusOk();
        assertThat(api.get(usuario, "/api/regras")).bodyJson().extractingPath("$[1].pausada").isEqualTo(false);
    }

    @Test
    void reordenaDuasVezesSeguidasERecusaListaQueNaoBate() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long app = idDaCategoria(usuario, "App de transporte");
        long a = criarRegra(usuario, "CONTEM", "aaa", null, app, null);
        long b = criarRegra(usuario, "CONTEM", "bbb", null, app, null);
        long c = criarRegra(usuario, "CONTEM", "ccc", null, app, null);

        MvcTestResult primeira = api.put(usuario, "/api/regras/ordem", "{\"ids\":[" + a + "," + b + "," + c + "]}");
        assertThat(primeira).hasStatusOk().bodyJson().extractingPath("$[*].id").asArray().containsExactly((int) a, (int) b, (int) c);
        MvcTestResult segunda = api.put(usuario, "/api/regras/ordem", "{\"ids\":[" + b + "," + a + "," + c + "]}");
        assertThat(segunda).hasStatusOk().bodyJson().extractingPath("$[*].id").asArray().containsExactly((int) b, (int) a, (int) c);
        assertThat(api.get(usuario, "/api/regras")).bodyJson().extractingPath("$[*].posicao").asArray().containsExactly(1, 2, 3);

        for (String ids : new String[] {"[" + a + "," + b + "]", "[" + a + "," + a + "," + b + "]", "[" + a + "," + b + "," + c + ",1]"}) {
            assertThat(api.put(usuario, "/api/regras/ordem", "{\"ids\":" + ids + "}")).hasStatus(422).bodyJson()
                    .extractingPath("codigo").isEqualTo("ORDEM_DESATUALIZADA");
        }
        assertThat(api.get(usuario, "/api/regras")).bodyJson().extractingPath("$[*].id").asArray().containsExactly((int) b, (int) a, (int) c);
        String duzentasEUma = LongStream.rangeClosed(1, 201).mapToObj(String::valueOf).collect(Collectors.joining(","));
        assertCampo(api.put(usuario, "/api/regras/ordem", "{\"ids\":[" + duzentasEUma + "]}"), "ids", "Envie no máximo 200 regras.");
    }

    @Test
    void limiteDe200Regras() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long app = idDaCategoria(usuario, "App de transporte");
        jdbc.update("insert into regras_categorizacao (usuario_id, posicao, condicao, texto, texto_chave, natureza, categoria_id) "
                + "select ?, n, 'CONTEM', 'loja ' || n, 'loja ' || n, 'DESPESA', ? from generate_series(1, 200) as n", usuario.id(), app);

        assertThat(api.post(usuario, "/api/regras", regra("CONTEM", "uber", null, app, null))).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Você chegou ao limite de 200 regras.");
    }

    @Test
    void previaContaOQueACondicaoPegaComExemplosDosMaisRecentes() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long poupanca = novaConta(usuario, "Poupança");
        long app = idDaCategoria(usuario, "App de transporte");
        long reembolsos = idDaCategoria(usuario, "Reembolsos");
        importado(usuario, horizonte, "2026-11-02", "-18.40", "PAG*UBERTRIP 1290");
        importado(usuario, horizonte, "2026-11-04", "-23.90", "UBER* TRIP 5521");
        importado(usuario, horizonte, "2026-11-06", "18.40", "ESTORNO UBER *TRIP");
        manual(usuario, horizonte, "DESPESA", "10.00", "Uber do aeroporto", app);
        importado(usuario, poupanca, "2026-11-03", "-300.00", "UBERLANDIA PARK HOTEL");
        long arquivada = novaConta(usuario, "Arquivada");
        importado(usuario, arquivada, "2026-11-03", "-1.00", "UBER ARQUIVADO");
        assertThat(api.acao(usuario, "/api/contas/{id}/arquivar", arquivada)).hasStatusOk();

        MvcTestResult todas = api.post(usuario, "/api/regras/previa", "{\"condicao\":\"CONTEM\",\"texto\":\"uber\",\"contaId\":null,"
                + "\"categoriaId\":null}");
        assertThat(todas).hasStatusOk().bodyJson().extractingPath("casam").isEqualTo(5);
        assertThat(todas).bodyJson().extractingPath("semCategoria").isEqualTo(4);
        assertThat(todas).bodyJson().extractingPath("$.exemplos[*].descricao").asArray()
                .containsExactly("ESTORNO UBER *TRIP", "Uber do aeroporto", "UBER* TRIP 5521");
        MvcTestResult despesas = api.post(usuario, "/api/regras/previa", "{\"condicao\":\"CONTEM\",\"texto\":\"uber trip\",\"contaId\":"
                + horizonte + ",\"categoriaId\":" + app + "}");
        assertThat(despesas).hasStatusOk().bodyJson().extractingPath("casam").isEqualTo(1);
        assertThat(despesas).bodyJson().extractingPath("$.exemplos[0].valor").isEqualTo(-23.90);
        MvcTestResult receitas = api.post(usuario, "/api/regras/previa", "{\"condicao\":\"COMECA_COM\",\"texto\":\"estorno\",\"contaId\":null,"
                + "\"categoriaId\":" + reembolsos + "}");
        assertThat(receitas).hasStatusOk().bodyJson().extractingPath("casam").isEqualTo(1);
        assertCampo(api.post(usuario, "/api/regras/previa", "{\"condicao\":\"CONTEM\",\"texto\":\"-\",\"contaId\":null,\"categoriaId\":null}"),
                "texto", "Use ao menos 2 letras ou números.");
        // A conta arquivada vale (a regra que se edita pode ter uma); a que não existe dá 422, como a categoria.
        assertThat(api.post(usuario, "/api/regras/previa", "{\"condicao\":\"CONTEM\",\"texto\":\"uber\",\"contaId\":" + arquivada
                + ",\"categoriaId\":null}")).hasStatusOk().bodyJson().extractingPath("casam").isEqualTo(0);
        assertThat(api.post(usuario, "/api/regras/previa", "{\"condicao\":\"CONTEM\",\"texto\":\"uber\",\"contaId\":999999999,"
                + "\"categoriaId\":null}")).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("CONTA_INVALIDA");
        assertThat(api.post(usuario, "/api/regras/previa", "{\"condicao\":\"CONTEM\",\"texto\":\"uber\",\"contaId\":null,"
                + "\"categoriaId\":999999999}")).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("CATEGORIA_INVALIDA");
    }
}
