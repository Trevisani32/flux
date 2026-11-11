package com.flux.importacao.interno;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

import static com.flux.suporte.ApiDeTeste.lancamento;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decisões 14 e 15 da 04b: a revisão traz a sugestão da primeira regra só nas linhas para importar (a nova
 * e a provável duplicada; a já importada e as de fora, não); a confirmação grava a descrição que a revisão
 * mostrou e guarda a do extrato, sem rodar as regras de novo.
 */
class RegrasNaImportacaoIT extends ImportacaoBase {

    private static final byte[] EXTRATO = ofx("0260", "12345678-0",
            transacao("r1", "2026-11-09", "-23.90", "PAG*UBERTRIP 8812"),
            transacao("r2", "2026-11-08", "-52.00", "UBER EATS*PEDIDO 991"),
            transacao("r3", "2026-11-07", "18.40", "ESTORNO UBER *TRIP"),
            transacao("r4", "2026-11-06", "-64.00", "CINEMA ARCOPLEX"),
            transacao("r5", "2026-11-05", "-9.00", "PADARIA CENTRAL"),
            transacao("r6", "2026-09-30", "-31.70", "UBER* TRIP 0001"));

    private static String regra(String texto, Long contaId, long categoriaId, String descricao) {
        return "{\"condicao\":\"CONTEM\",\"texto\":\"" + texto + "\",\"contaId\":" + contaId + ",\"categoriaId\":" + categoriaId
                + ",\"descricao\":" + (descricao == null ? "null" : "\"" + descricao + "\"") + "}";
    }

    private static JsonNode linha(JsonNode previa, int numero) {
        for (JsonNode linha : previa.get("linhas")) {
            if (linha.get("linha").asInt() == numero) {
                return linha;
            }
        }
        throw new IllegalArgumentException("sem a linha " + numero);
    }

    @Test
    void revisaoTrazAPrimeiraRegraSoNasLinhasParaImportar() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte", "2026-10-01");
        long poupanca = novaConta(usuario, "Poupança", "2026-10-01");
        long app = idDaCategoria(usuario, "App de transporte");
        long delivery = idDaCategoria(usuario, "Delivery");
        long lazer = idDaCategoria(usuario, "Lazer");
        assertThat(api.post(usuario, "/api/regras", regra("uber", null, app, "Uber"))).hasStatus(201);
        assertThat(api.post(usuario, "/api/regras", regra("uber eats", null, delivery, null))).hasStatus(201);
        assertThat(api.post(usuario, "/api/regras", regra("cinema", null, lazer, null))).hasStatus(201);
        assertThat(api.post(usuario, "/api/regras", regra("padaria", poupanca, delivery, null))).hasStatus(201);
        assertThat(api.acao(usuario, "/api/categorias/{id}/arquivar", lazer)).hasStatusOk();
        // Um lançamento à mão com a data e o valor da linha 2: ela vira provável duplicada, e a regra vale nela também.
        assertThat(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "52.00", "2026-11-08", "Pedido no app", horizonte, null)))
                .hasStatus(201);
        // Outro usuário com uma regra que pegaria tudo: não age aqui.
        UsuarioDeTeste outro = usuariosDeTeste.criar();
        assertThat(api.post(outro, "/api/regras", regra("pa", null, idDaCategoria(outro, "Compras"), null)))
                .hasStatus(201);

        MvcTestResult resposta = previa(usuario, horizonte, "extrato.ofx", EXTRATO);
        assertThat(resposta).hasStatusOk();
        JsonNode previa = api.json(resposta);

        JsonNode trip = linha(previa, 1).get("sugestao");
        assertThat(trip.get("posicao").asInt()).isEqualTo(4);
        assertThat(trip.get("condicao").asString()).isEqualTo("CONTEM");
        assertThat(trip.get("texto").asString()).isEqualTo("uber");
        assertThat(trip.get("categoriaId").asLong()).isEqualTo(app);
        assertThat(trip.get("descricao").asString()).isEqualTo("Uber");
        assertThat(linha(previa, 1).get("situacao").asString()).isEqualTo("NOVA");
        assertThat(linha(previa, 2).get("situacao").asString()).isEqualTo("PROVAVEL");
        JsonNode eats = linha(previa, 2).get("sugestao");
        assertThat(eats.isNull()).isFalse();
        assertThat(eats.get("posicao").asInt()).isEqualTo(3);
        assertThat(eats.get("categoriaId").asLong()).isEqualTo(delivery);
        assertThat(eats.get("descricao").isNull()).isTrue();
        // Receita (a regra é de despesa), regra pausada, regra de outra conta e linha antes do saldo inicial.
        assertThat(linha(previa, 3).get("sugestao").isNull()).isTrue();
        assertThat(linha(previa, 4).get("sugestao").isNull()).isTrue();
        assertThat(linha(previa, 5).get("sugestao").isNull()).isTrue();
        assertThat(linha(previa, 6).get("situacao").asString()).isEqualTo("ANTES_DO_SALDO_INICIAL");
        assertThat(linha(previa, 6).get("sugestao").isNull()).isTrue();
        // A descrição da linha continua a do extrato: a legível vem na sugestão.
        assertThat(linha(previa, 1).get("descricao").asString()).isEqualTo("PAG*UBERTRIP 8812");
    }

    @Test
    void confirmacaoGravaADescricaoDaRevisaoEGuardaADoExtrato() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte", "2026-10-01");
        long app = idDaCategoria(usuario, "App de transporte");
        String hash = api.json(previa(usuario, horizonte, "extrato.ofx", EXTRATO)).get("hash").asString();

        assertThat(confirmar(usuario, horizonte, "extrato.ofx", EXTRATO, hash, "[{\"linha\":1,\"categoriaId\":null,\"descricao\":\""
                + "d".repeat(256) + "\"}]")).hasStatus(400).bodyJson().extractingPath("campos[0].mensagem")
                .isEqualTo("A descrição pode ter até 255 caracteres.");
        MvcTestResult resultado = confirmar(usuario, horizonte, "extrato.ofx", EXTRATO, hash, "[{\"linha\":1,\"categoriaId\":" + app
                + ",\"descricao\":\"  Uber   do  trabalho \"},{\"linha\":2,\"categoriaId\":null,\"descricao\":\"   \"},"
                + "{\"linha\":5,\"categoriaId\":null}]");
        assertThat(resultado).hasStatus(201).bodyJson().extractingPath("importadas").isEqualTo(3);

        long trip = lancamentoDaChave(usuario, "ID:" + horizonte + ":r1");
        assertThat(jdbc.queryForObject("select descricao || '|' || descricao_chave || '|' || descricao_banco || '|' || categoria_id "
                + "from lancamentos where id = ?", String.class, trip)).isEqualTo("Uber do trabalho|uber do trabalho|PAG*UBERTRIP 8812|" + app);
        assertThat(jdbc.queryForObject("select descricao || '|' || descricao_banco from lancamentos where id = ?", String.class,
                lancamentoDaChave(usuario, "ID:" + horizonte + ":r2"))).isEqualTo("UBER EATS*PEDIDO 991|UBER EATS*PEDIDO 991");
        // Reimportar: a chave e a descrição do extrato continuam reconhecendo a linha. A já importada não recebe
        // a sugestão, nem com uma regra que casa com ela.
        assertThat(api.post(usuario, "/api/regras", regra("uber", null, app, "Uber"))).hasStatus(201);
        JsonNode reimportada = linha(api.json(previa(usuario, horizonte, "extrato.ofx", EXTRATO)), 1);
        assertThat(reimportada.get("situacao").asString()).isEqualTo("JA_IMPORTADA");
        assertThat(reimportada.get("sugestao").isNull()).isTrue();
    }
}
