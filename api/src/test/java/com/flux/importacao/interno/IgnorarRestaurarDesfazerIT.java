package com.flux.importacao.interno;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.flux.suporte.ApiDeTeste.categoria;
import static com.flux.suporte.ApiDeTeste.conta;
import static com.flux.suporte.ApiDeTeste.lancamento;
import static org.assertj.core.api.Assertions.assertThat;

/** Decisões 29 a 32: editar só o que pode, excluir = ignorar, restaurar e desfazer a importação. */
class IgnorarRestaurarDesfazerIT extends ImportacaoBase {

    private static final byte[] EXTRATO = ofx("0260", "12345678-0",
            transacao("c1", "2026-11-05", "-64.00", "CINEMA ARCOPLEX"),
            transacao("c2", "2026-11-06", "-23.90", "UBER TRIP"),
            transacao("c3", "2026-11-07", "250.00", "PIX RECEBIDO MARIA"));

    @Test
    void excluirImportadoIgnoraTiraDaListaDoSaldoEDoUsoEDeixaExcluirACategoria() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte", "2026-10-01");
        long cinema = api.idCriado(api.post(usuario, "/api/categorias", categoria("Cinema", "DESPESA", null)));
        importar(usuario, horizonte, "extrato.ofx", EXTRATO, 1, 2);
        long cinemaArcoplex = lancamentoDaChave(usuario, "ID:" + horizonte + ":c1");
        assertThat(api.put(usuario, "/api/lancamentos/{id}", lancamento("DESPESA", "64", "2026-11-05", "Cinema", horizonte, cinema),
                cinemaArcoplex)).hasStatusOk();

        assertThat(api.delete(usuario, "/api/lancamentos/{id}", cinemaArcoplex)).hasStatus(204);

        assertThat(jdbc.queryForList("select (ignorado_em is not null) || '/' || coalesce(categoria_id::text, 'sem') from lancamentos "
                + "where id = ?", String.class, cinemaArcoplex)).containsExactly("true/sem");
        assertThat(api.get(usuario, "/api/lancamentos/{id}", cinemaArcoplex)).hasStatus(404);
        assertThat(api.get(usuario, "/api/lancamentos?mes=2026-11&conta={conta}", horizonte)).bodyJson()
                .extractingPath("quantidadeLancamentos").isEqualTo(1);
        assertThat(api.get(usuario, "/api/contas/{id}", horizonte)).bodyJson().extractingPath("saldoAtual").isEqualTo(976.10);
        assertThat(api.delete(usuario, "/api/categorias/{id}", cinema)).hasStatus(204);
        assertThat(api.delete(usuario, "/api/contas/{id}", horizonte)).hasStatus(422).bodyJson().extractingPath("uso")
                .isEqualTo("1 lançamento");
    }

    @Test
    void restaurarVoltaComACategoriaQueServeERecusaDataAntesDoSaldoInicialEContaArquivada() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte", "2026-10-01");
        long lazer = idDaCategoria(usuario, "Lazer");
        long salario = idDaCategoria(usuario, "Salário");
        importar(usuario, horizonte, "extrato.ofx", EXTRATO, 1);
        long id = lancamentoDaChave(usuario, "ID:" + horizonte + ":c1");
        assertThat(api.delete(usuario, "/api/lancamentos/{id}", id)).hasStatus(204);

        MvcTestResult restaurado = api.post(usuario, "/api/lancamentos/{id}/restaurar", "{\"categoriaId\":" + lazer + "}", id);
        assertThat(restaurado).hasStatusOk().bodyJson().extractingPath("categoriaId").isEqualTo((int) lazer);
        assertThat(restaurado).bodyJson().extractingPath("origem").isEqualTo("IMPORTADO");
        assertThat(api.post(usuario, "/api/lancamentos/{id}/restaurar", "{}", id)).hasStatus(404);

        assertThat(api.delete(usuario, "/api/lancamentos/{id}", id)).hasStatus(204);
        assertThat(api.post(usuario, "/api/lancamentos/{id}/restaurar", "{\"categoriaId\":" + salario + "}", id))
                .hasStatusOk().bodyJson().extractingPath("categoriaId").isNull();

        assertThat(api.delete(usuario, "/api/lancamentos/{id}", id)).hasStatus(204);
        assertThat(api.put(usuario, "/api/contas/{id}", conta("Banco Horizonte", "CORRENTE", "1000", "2026-11-06", "null"), horizonte))
                .hasStatusOk();
        assertThat(api.post(usuario, "/api/lancamentos/{id}/restaurar", "{}", id)).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Não dá para restaurar: a data, 05/11/2026, é anterior ao saldo inicial de Banco Horizonte (06/11/2026).");

        assertThat(api.acao(usuario, "/api/contas/{id}/arquivar", horizonte)).hasStatusOk();
        assertThat(api.post(usuario, "/api/lancamentos/{id}/restaurar", "{}", id)).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Banco Horizonte está arquivada. Reative a conta para restaurar este lançamento.");
    }

    @Test
    void editarImportadoSoMudaDescricaoCategoriaEObservacao() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte", "2026-10-01");
        long poupanca = novaConta(usuario, "Poupança", "2026-10-01");
        long transporte = idDaCategoria(usuario, "App de transporte");
        importar(usuario, horizonte, "extrato.ofx", EXTRATO, 2);
        long uber = lancamentoDaChave(usuario, "ID:" + horizonte + ":c2");
        // Abrir e salvar sem mudar nada não conta como "editado" no desfazer.
        assertThat(api.put(usuario, "/api/lancamentos/{id}", lancamento("DESPESA", "23.90", "2026-11-06", "UBER TRIP", horizonte, null),
                uber)).hasStatusOk();
        assertThat(api.get(usuario, "/api/importacoes")).bodyJson().extractingPath("$[0].editados").isEqualTo(0);

        MvcTestResult editado = api.put(usuario, "/api/lancamentos/{id}",
                lancamento("DESPESA", "23.90", "2026-11-06", "Uber", horizonte, transporte), uber);

        assertThat(editado).hasStatusOk().bodyJson().extractingPath("descricao").isEqualTo("Uber");
        assertThat(editado).bodyJson().extractingPath("descricaoBanco").isEqualTo("UBER TRIP");
        assertThat(editado).bodyJson().extractingPath("categoriaId").isEqualTo((int) transporte);
        assertThat(api.get(usuario, "/api/importacoes")).bodyJson().extractingPath("$[0].editados").isEqualTo(1);
        for (String mudanca : new String[] {
                lancamento("DESPESA", "24", "2026-11-06", "Uber", horizonte, null),
                lancamento("DESPESA", "23.90", "2026-11-07", "Uber", horizonte, null),
                lancamento("RECEITA", "23.90", "2026-11-06", "Uber", horizonte, null),
                lancamento("DESPESA", "23.90", "2026-11-06", "Uber", poupanca, null)}) {
            assertThat(api.put(usuario, "/api/lancamentos/{id}", mudanca, uber)).hasStatus(422).bodyJson().extractingPath("codigo")
                    .isEqualTo("IMPORTADO_TRAVADO");
        }
        assertThat(jdbc.queryForList("select descricao || '/' || valor from lancamentos where id = ?", String.class, uber))
                .containsExactly("Uber/-23.90");
    }

    @Test
    void desfazerApagaTudoInclusiveEditadosEExcluidosEDeixaReimportar() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte", "2026-10-01");
        long importacao = importar(usuario, horizonte, "extrato.ofx", EXTRATO, 1, 2, 3);
        long cinema = lancamentoDaChave(usuario, "ID:" + horizonte + ":c1");
        long uber = lancamentoDaChave(usuario, "ID:" + horizonte + ":c2");
        assertThat(api.put(usuario, "/api/lancamentos/{id}", lancamento("DESPESA", "64", "2026-11-05", "Cinema", horizonte, null), cinema))
                .hasStatusOk();
        assertThat(api.delete(usuario, "/api/lancamentos/{id}", uber)).hasStatus(204);

        MvcTestResult historico = api.get(usuario, "/api/importacoes");
        assertThat(historico).bodyJson().extractingPath("$[0].importadas").isEqualTo(3);
        assertThat(historico).bodyJson().extractingPath("$[0].editados").isEqualTo(1);
        assertThat(historico).bodyJson().extractingPath("$[0].excluidos").isEqualTo(1);
        assertThat(api.get(usuario, "/api/importacoes/{id}/excluidos", importacao)).bodyJson().extractingPath("$[0].descricao")
                .isEqualTo("UBER TRIP");

        assertThat(api.acao(usuario, "/api/importacoes/{id}/desfazer", importacao)).hasStatusOk().bodyJson()
                .extractingPath("desfeitaEm").isNotNull();

        assertThat(jdbc.queryForObject("select count(*) from lancamentos where usuario_id = ?", Integer.class, usuario.id())).isZero();
        assertThat(api.acao(usuario, "/api/importacoes/{id}/desfazer", importacao)).hasStatus(422).bodyJson()
                .extractingPath("codigo").isEqualTo("IMPORTACAO_DESFEITA");
        MvcTestResult previa = previa(usuario, horizonte, "extrato.ofx", EXTRATO);
        assertThat(api.json(previa).get("arquivoRepetido").isNull()).isTrue();
        assertThat(api.json(previa).get("linhas").valueStream().map(linha -> linha.get("situacao").asString()).toList())
                .containsExactly("NOVA", "NOVA", "NOVA");
        importar(usuario, horizonte, "extrato.ofx", EXTRATO, 1, 2, 3);
        assertThat(api.get(usuario, "/api/importacoes")).bodyJson().extractingPath("$.length()").isEqualTo(2);
    }

    @Test
    void desfazerComAContaArquivadaERecusado() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long poupanca = novaConta(usuario, "Poupança", "2026-10-01");
        long importacao = importar(usuario, poupanca, "extrato.ofx", EXTRATO, 1);
        assertThat(api.acao(usuario, "/api/contas/{id}/arquivar", poupanca)).hasStatusOk();

        assertThat(api.acao(usuario, "/api/importacoes/{id}/desfazer", importacao)).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Poupança está arquivada. Reative a conta para desfazer esta importação.");
        assertThat(jdbc.queryForObject("select count(*) from lancamentos where usuario_id = ?", Integer.class, usuario.id())).isEqualTo(1);
    }
}
