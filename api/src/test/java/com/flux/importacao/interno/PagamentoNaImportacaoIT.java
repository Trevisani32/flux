package com.flux.importacao.interno;

import java.time.LocalDate;
import java.time.YearMonth;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decisão 37 da Fatia 05 (ADR 0018) com a importação de verdade: o importado convertido em pagamento continua "já
 * importado" na reimportação; o histórico diz o pagamento, para a confirmação avisar antes; desfazer a importação apaga o
 * pagamento, e a fatura volta a ter o pendente; com o cartão dele arquivado, o desfazer é recusado.
 */
class PagamentoNaImportacaoIT extends ImportacaoBase {

    /** O cartão "fecha 5, vence 15" e a compra de R$ 265,20 do dia 1 de dois meses atrás, na fatura desse mês (fechada). */
    private long cartaoComFaturaFechada(UsuarioDeTeste usuario, long contaId, String nome) {
        long cartao = api.idCriado(api.post(usuario, "/api/cartoes", "{\"nome\":\"" + nome + "\",\"limite\":1000,\"diaFechamento\":5,"
                + "\"diaVencimento\":15,\"contaPagamentoId\":" + contaId + ",\"cor\":null}"));
        api.idCriado(api.post(usuario, "/api/compras", "{\"cartaoId\":" + cartao + ",\"data\":\"" + doisMesesAtras().atDay(1)
                + "\",\"descricao\":\"Livraria\",\"valorTotal\":265.20,\"parcelas\":1,\"parcelaInicial\":1,\"categoriaId\":null,"
                + "\"observacao\":null}"));
        return cartao;
    }

    private YearMonth doisMesesAtras() {
        return YearMonth.from(LocalDate.now(relogioDeTeste)).minusMonths(2);
    }

    private String situacao(UsuarioDeTeste usuario, long cartao) {
        return api.json(api.get(usuario, "/api/cartoes/{id}/faturas/{mes}", cartao, doisMesesAtras().toString())).get("situacao").asString();
    }

    @Test
    void reimportarDepoisDeConverterContinuaJaImportadaEDesfazerAImportacaoApagaOPagamento() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        LocalDate ontem = LocalDate.now(relogioDeTeste).minusDays(1);
        long conta = novaConta(usuario, "Banco Horizonte", ontem.minusDays(30).toString());
        long cartao = cartaoComFaturaFechada(usuario, conta, "Cartão Horizonte");
        byte[] extrato = ofx("0260", "12345-6", transacao("PG1", ontem.toString(), "-265.20", "PAG FATURA CARTAO HORIZONTE"),
                transacao("PG2", ontem.toString(), "-12.00", "PADARIA"));
        long importacao = importar(usuario, conta, "extrato.ofx", extrato, 1, 2);
        long despesa = jdbc.queryForObject("select id from lancamentos where conta_id = ? and valor = -265.20", Long.class, conta);

        assertThat(api.post(usuario, "/api/lancamentos/{id}/converter-em-pagamento", "{\"cartaoId\":" + cartao + ",\"mes\":\""
                + doisMesesAtras() + "\"}", despesa)).hasStatus(201);
        assertThat(situacao(usuario, cartao)).isEqualTo("PAGA");

        MvcTestResult reimportado = previa(usuario, conta, "extrato.ofx", extrato);
        assertThat(api.json(reimportado).get("linhas").valueStream().map(linha -> linha.get("situacao").asString()).toList())
                .containsExactly("JA_IMPORTADA", "JA_IMPORTADA");

        assertThat(api.acao(usuario, "/api/importacoes/{id}/desfazer", importacao)).hasStatusOk();
        assertThat(jdbc.queryForObject("select count(*) from lancamentos where usuario_id = ? and conta_id = ?", Integer.class,
                usuario.id(), conta)).isZero();
        assertThat(situacao(usuario, cartao)).isEqualTo("FECHADA");
    }

    @Test
    void oHistoricoDizOPagamentoConvertidoComOCartaoParaAvisarAntesDoDesfazer() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        LocalDate ontem = LocalDate.now(relogioDeTeste).minusDays(1);
        long conta = novaConta(usuario, "Banco Horizonte", ontem.minusDays(30).toString());
        long cartao = cartaoComFaturaFechada(usuario, conta, "Cartão Lua");
        long importacao = importar(usuario, conta, "extrato.ofx", ofx("0260", "12345-6",
                transacao("PG1", ontem.toString(), "-265.20", "PAG FATURA CARTAO LUA"), transacao("PG2", ontem.toString(), "-12.00", "PADARIA")),
                1, 2);
        assertThat(item(usuario, importacao).get("pagamentos").toString()).isEqualTo("[]");
        long despesa = jdbc.queryForObject("select id from lancamentos where conta_id = ? and valor = -265.20", Long.class, conta);
        assertThat(api.post(usuario, "/api/lancamentos/{id}/converter-em-pagamento", "{\"cartaoId\":" + cartao + ",\"mes\":\""
                + doisMesesAtras() + "\"}", despesa)).hasStatus(201);

        String pagamento = "[{\"data\":\"" + ontem + "\",\"cartaoId\":" + cartao + ",\"cartaoNome\":\"Cartão Lua\",\"cartaoArquivado\":%s,"
                + "\"faturaMes\":\"" + doisMesesAtras() + "\"}]";
        assertThat(item(usuario, importacao).get("pagamentos").toString()).isEqualTo(pagamento.formatted("false"));
        assertThat(item(usuario, importacao).get("transferencias").toString()).isEqualTo("[]");
        assertThat(api.acao(usuario, "/api/cartoes/{id}/arquivar", cartao)).hasStatusOk();
        assertThat(item(usuario, importacao).get("pagamentos").toString()).isEqualTo(pagamento.formatted("true"));
    }

    /** O item da importação no histórico (GET /api/importacoes). */
    private JsonNode item(UsuarioDeTeste usuario, long importacao) {
        return api.json(api.get(usuario, "/api/importacoes")).valueStream().filter(no -> no.get("id").asLong() == importacao).findFirst()
                .orElseThrow();
    }

    @Test
    void desfazerAImportacaoComOCartaoDoPagamentoArquivadoERecusado() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        LocalDate ontem = LocalDate.now(relogioDeTeste).minusDays(1);
        long conta = novaConta(usuario, "Banco Horizonte", ontem.minusDays(30).toString());
        long cartao = cartaoComFaturaFechada(usuario, conta, "Cartão Lua");
        byte[] extrato = ofx("0260", "12345-6", transacao("PG1", ontem.toString(), "-265.20", "PAG FATURA CARTAO LUA"));
        long importacao = importar(usuario, conta, "extrato.ofx", extrato, 1);
        long despesa = jdbc.queryForObject("select id from lancamentos where usuario_id = ? and conta_id = ?", Long.class, usuario.id(), conta);
        assertThat(api.post(usuario, "/api/lancamentos/{id}/converter-em-pagamento", "{\"cartaoId\":" + cartao + ",\"mes\":\""
                + doisMesesAtras() + "\"}", despesa)).hasStatus(201);
        assertThat(api.acao(usuario, "/api/cartoes/{id}/arquivar", cartao)).hasStatusOk();

        MvcTestResult recusado = api.acao(usuario, "/api/importacoes/{id}/desfazer", importacao);
        assertThat(recusado).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("O pagamento de "
                + ontem.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")) + " liga esta importação a Cartão Lua, que está "
                + "arquivado. Reative o cartão para desfazer esta importação.");
        assertThat(recusado).bodyJson().extractingPath("codigo").isEqualTo("CARTAO_ARQUIVADO");
        assertThat(situacao(usuario, cartao)).isEqualTo("PAGA");
    }
}
