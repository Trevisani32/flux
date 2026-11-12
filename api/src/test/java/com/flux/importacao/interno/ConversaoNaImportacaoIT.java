package com.flux.importacao.interno;

import java.util.List;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.flux.suporte.ApiDeTeste.lancamento;
import static com.flux.suporte.ApiDeTeste.texto;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A conversão e a importação (Fatia 04c): o resultado sugere os pares únicos sem converter nada (decisão
 * 35), o histórico diz as transferências de cada importação, desfazer a importação desfaz a conversão
 * junto e é recusado com o outro lado numa conta arquivada (decisão 34), e reimportar continua dando
 * "já importada" (decisão 29).
 */
class ConversaoNaImportacaoIT extends ImportacaoBase {

    @Test
    void oResultadoSugereSoOsParesUnicosENadaSeConverteSozinho() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte", "2026-10-01");
        long conjunta = novaConta(usuario, "Conta conjunta", "2026-10-01");
        long poupanca = novaConta(usuario, "Poupança", "2026-10-01");
        long reserva = manual(usuario, conjunta, "RECEITA", "1000", "2026-11-03", "Reserva", null);
        manual(usuario, poupanca, "RECEITA", "50", "2026-11-05", "Saque A", null);
        manual(usuario, conjunta, "RECEITA", "50", "2026-11-06", "Saque B", null);
        manual(usuario, conjunta, "RECEITA", "80", "2026-11-04", "Par de duas linhas", null);
        byte[] extrato = ofx("0341", "11111-1",
                transacao("t1", "2026-11-02", "-1000.00", "TED 341.0001DEV FICTICIO"),
                transacao("t2", "2026-11-05", "-50.00", "SAQUE 24H"),
                transacao("t3", "2026-11-03", "-80.00", "PIX A"),
                transacao("t4", "2026-11-05", "-80.00", "PIX B"),
                transacao("t5", "2026-11-05", "-6.00", "CAFE"));
        String hash = api.json(previa(usuario, horizonte, "extrato.ofx", extrato)).get("hash").asString();

        MvcTestResult resultado = confirmar(usuario, horizonte, "extrato.ofx", extrato, hash, escolhas(1, 2, 3, 4, 5));

        assertThat(resultado).hasStatus(201);
        long ted = lancamentoDaChave(usuario, "ID:" + horizonte + ":t1");
        assertThat(texto(resultado)).contains("\"pares\":[{\"lancamento\":{\"id\":" + ted + ",\"data\":\"2026-11-02\",\"descricao\":"
                + "\"TED 341.0001DEV FICTICIO\",\"valor\":-1000.00,\"contaId\":" + horizonte + ",\"contaNome\":\"Banco Horizonte\"},"
                + "\"par\":{\"id\":" + reserva + ",\"data\":\"2026-11-03\",\"descricao\":\"Reserva\",\"valor\":1000.00,\"contaId\":" + conjunta
                + ",\"contaNome\":\"Conta conjunta\"}}]");
        assertThat(jdbc.queryForObject("select count(*) from transferencias where usuario_id = ?", Integer.class, usuario.id())).isZero();
    }

    @Test
    void oHistoricoDizAsTransferenciasEDesfazerAImportacaoDesfazAConversao() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte", "2026-10-01");
        long conjunta = novaConta(usuario, "Conta conjunta", "2026-10-01");
        long reserva = manual(usuario, conjunta, "RECEITA", "1000", "2026-11-03", "Reserva", idDaCategoria(usuario, "Outras receitas"));
        byte[] extrato = ofx("0341", "11111-1",
                transacao("t1", "2026-11-02", "-1000.00", "TED 341.0001DEV FICTICIO"),
                transacao("t2", "2026-11-05", "-6.00", "CAFE"));
        long importacao = importar(usuario, horizonte, "extrato.ofx", extrato, 1, 2);
        long ted = lancamentoDaChave(usuario, "ID:" + horizonte + ":t1");
        assertThat(api.post(usuario, "/api/lancamentos/{id}/converter", "{\"parId\":" + reserva + ",\"contaId\":null,"
                + "\"descricao\":\"Reserva da viagem\"}", ted)).hasStatus(201);

        assertThat(texto(api.get(usuario, "/api/importacoes"))).contains("\"transferencias\":[{\"data\":\"2026-11-02\",\"contaId\":"
                + conjunta + ",\"contaNome\":\"Conta conjunta\",\"contaArquivada\":false,\"tipo\":\"RECEITA\"}]");

        MvcTestResult desfeita = api.acao(usuario, "/api/importacoes/{id}/desfazer", importacao);

        assertThat(desfeita).hasStatusOk().bodyJson().extractingPath("desfeitaEm").isNotNull();
        assertThat(texto(desfeita)).contains("\"transferencias\":[]");
        assertThat(jdbc.queryForObject("select count(*) from transferencias where usuario_id = ?", Integer.class, usuario.id())).isZero();
        assertThat(jdbc.queryForList("select tipo || ' ' || valor || ' ' || descricao || ' ' || coalesce(categoria_id::text, 'sem') || ' ' "
                + "|| coalesce(transferencia_id::text, 'solto') from lancamentos where usuario_id = ?", String.class, usuario.id()))
                .containsExactly("RECEITA 1000.00 Reserva da viagem sem solto");
        assertThat(api.json(previa(usuario, horizonte, "extrato.ofx", extrato)).get("linhas").valueStream()
                .map(linha -> linha.get("situacao").asString()).toList()).containsExactly("NOVA", "NOVA");
    }

    @Test
    void desfazerAImportacaoComOOutroLadoNumaContaArquivadaERecusado() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte", "2026-10-01");
        long conjunta = novaConta(usuario, "Conta conjunta", "2026-10-01");
        long reserva = manual(usuario, conjunta, "RECEITA", "1000", "2026-11-03", "Reserva", null);
        long importacao = importar(usuario, horizonte, "extrato.ofx", ofx("0341", "11111-1",
                transacao("t1", "2026-11-02", "-1000.00", "TED 341.0001DEV FICTICIO")), 1);
        long ted = lancamentoDaChave(usuario, "ID:" + horizonte + ":t1");
        assertThat(api.post(usuario, "/api/lancamentos/{id}/converter", "{\"parId\":" + reserva + ",\"contaId\":null,\"descricao\":null}",
                ted)).hasStatus(201);
        assertThat(api.acao(usuario, "/api/contas/{id}/arquivar", conjunta)).hasStatusOk();

        assertThat(api.get(usuario, "/api/importacoes")).bodyJson().extractingPath("$[0].transferencias[0].contaArquivada").isEqualTo(true);
        MvcTestResult recusado = api.acao(usuario, "/api/importacoes/{id}/desfazer", importacao);

        assertThat(recusado).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("CONTA_ARQUIVADA");
        assertThat(recusado).bodyJson().extractingPath("detail").isEqualTo("A transferência de 02/11 liga esta importação a Conta conjunta, "
                + "que está arquivada. Reative a conta para desfazer esta importação.");
        assertThat(jdbc.queryForObject("select count(*) from transferencias where usuario_id = ?", Integer.class, usuario.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from lancamentos where usuario_id = ? and transferencia_id is not null", Integer.class,
                usuario.id())).isEqualTo(2);
        assertThat(api.get(usuario, "/api/importacoes")).bodyJson().extractingPath("$[0].desfeitaEm").isNull();
    }

    @Test
    void desfazerUmaImportacaoSoltaOImportadoDaOutraComADescricaoDoExtratoEAChave() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte", "2026-10-01");
        long conjunta = novaConta(usuario, "Conta conjunta", "2026-10-01");
        long daSaida = importar(usuario, horizonte, "saida.ofx", ofx("0341", "11111-1",
                transacao("t1", "2026-11-02", "-1000.00", "TED 341.0001DEV FICTICIO")), 1);
        byte[] daConjunta = ofx("0260", "22222-2", transacao("r1", "2026-11-03", "1000.00", "TRANSFERENCIA RECEBIDA DEV"));
        long daEntrada = importar(usuario, conjunta, "entrada.ofx", daConjunta, 1);
        long ted = lancamentoDaChave(usuario, "ID:" + horizonte + ":t1");
        long recebida = lancamentoDaChave(usuario, "ID:" + conjunta + ":r1");
        assertThat(api.post(usuario, "/api/lancamentos/{id}/converter", "{\"parId\":" + recebida + ",\"contaId\":null,"
                + "\"descricao\":\"Reserva\"}", ted)).hasStatus(201);

        // Os dois lados são importados: cada importação lista a transferência, com a conta do outro lado, e conta o
        // seu lado como editado (converter muda atualizado_em).
        assertThat(texto(api.get(usuario, "/api/importacoes"))).contains("\"transferencias\":[{\"data\":\"2026-11-02\",\"contaId\":"
                + conjunta + ",\"contaNome\":\"Conta conjunta\",\"contaArquivada\":false,\"tipo\":\"RECEITA\"}]",
                "\"transferencias\":[{\"data\":\"2026-11-03\",\"contaId\":" + horizonte + ",\"contaNome\":\"Banco Horizonte\","
                + "\"contaArquivada\":false,\"tipo\":\"DESPESA\"}]");
        assertThat(api.get(usuario, "/api/importacoes")).bodyJson().extractingPath("$[?(@.id == " + daEntrada + ")].editados")
                .asArray().containsExactly(1);

        assertThat(api.acao(usuario, "/api/importacoes/{id}/desfazer", daSaida)).hasStatusOk();

        assertThat(jdbc.queryForList("select tipo || ' ' || valor || ' ' || descricao || ' ' || coalesce(categoria_id::text, 'sem') || ' ' "
                + "|| coalesce(transferencia_id::text, 'solto') || ' ' || chave_importacao from lancamentos where usuario_id = ?", String.class,
                usuario.id())).containsExactly("RECEITA 1000.00 TRANSFERENCIA RECEBIDA DEV sem solto ID:" + conjunta + ":r1");
        MvcTestResult historico = api.get(usuario, "/api/importacoes");
        assertThat(historico).bodyJson().extractingPath("$[?(@.id == " + daEntrada + ")].transferencias[*]").asArray().isEmpty();
        assertThat(historico).bodyJson().extractingPath("$[?(@.id == " + daEntrada + ")].editados").asArray().containsExactly(1);
        assertThat(api.json(previa(usuario, conjunta, "entrada.ofx", daConjunta)).get("linhas").valueStream()
                .map(linha -> linha.get("situacao").asString()).toList()).containsExactly("JA_IMPORTADA");
    }

    @Test
    void reimportarDepoisDeConverterContinuaJaImportada() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte", "2026-10-01");
        long carteira = novaConta(usuario, "Carteira", "2026-10-01");
        byte[] extrato = ofx("0341", "11111-1",
                transacao("t1", "2026-11-07", "-200.00", "SAQUE 24H 07/11 12:30"),
                transacao("t2", "2026-11-07", "-6.00", "CAFE"));
        importar(usuario, horizonte, "extrato.ofx", extrato, 1, 2);
        long saque = lancamentoDaChave(usuario, "ID:" + horizonte + ":t1");
        List<String> antes = jdbc.queryForList("select chave_importacao || '|' || descricao_banco from lancamentos where id = ?", String.class,
                saque);
        assertThat(api.post(usuario, "/api/lancamentos/{id}/converter", "{\"parId\":null,\"contaId\":" + carteira + ",\"descricao\":\"Saque\"}",
                saque)).hasStatus(201);

        MvcTestResult previa = previa(usuario, horizonte, "extrato.ofx", extrato);

        assertThat(api.json(previa).get("linhas").valueStream().map(linha -> linha.get("situacao").asString()).toList())
                .containsExactly("JA_IMPORTADA", "JA_IMPORTADA");
        assertThat(jdbc.queryForList("select chave_importacao || '|' || descricao_banco from lancamentos where id = ?", String.class, saque))
                .isEqualTo(antes);
    }

    /** Manual pela API ({@code valor} positivo; o tipo dá o sinal). */
    private long manual(UsuarioDeTeste usuario, long contaId, String tipo, String valor, String data, String descricao, Long categoriaId) {
        return api.idCriado(api.post(usuario, "/api/lancamentos", lancamento(tipo, valor, data, descricao, contaId, categoriaId)));
    }
}