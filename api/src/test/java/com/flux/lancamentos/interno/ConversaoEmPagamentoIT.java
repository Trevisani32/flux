package com.flux.lancamentos.interno;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.flux.suporte.ApiDeTeste.lancamento;
import static com.flux.suporte.ApiDeTeste.texto;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decisão 37 da Fatia 05 (ADR 0018): a despesa importada vira o pagamento da fatura escolhida, sem categoria e com a
 * data, o valor, a conta e a chave de antes; "Desfazer" volta a despesa sem categoria. O pagamento não vira
 * transferência nem serve de par (Fatia 04c).
 */
class ConversaoEmPagamentoIT extends PagamentosBase {

    private static String conversao(long cartaoId, String mes) {
        return "{\"cartaoId\":" + cartaoId + ",\"mes\":\"" + mes + "\"}";
    }

    @Test
    void asFaturasParaPagamentoSugeremAFechadaComOMesmoValorEVencimentoPerto() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = cartaoQueFecha5(usuario, "Cartão Horizonte", conta);
        cartaoQueFecha5(usuario, "Cartão Lua", conta);
        novaCompra(usuario, horizonte, diaUmAtras(3), "Mercado", "100", 1);
        novaCompra(usuario, horizonte, diaUmAtras(2), "Livraria", "265.20", 1);
        String mes = mesAtras(2);
        // O débito do extrato no dia do vencimento da fatura de dois meses atrás (dia 15).
        long despesa = importado(usuario, conta, java.time.YearMonth.parse(mes).atDay(15).toString(), "-265.20", "PAG FATURA CARTAO HORIZONTE");

        MvcTestResult opcoes = api.get(usuario, "/api/lancamentos/{id}/faturas-para-pagamento", despesa);
        assertThat(opcoes).hasStatus(200).bodyJson().extractingPath("lancamento.id").isEqualTo((int) despesa);
        assertThat(opcoes).bodyJson().extractingPath("cartoes[*].nome").asArray().containsExactly("Cartão Horizonte", "Cartão Lua");
        assertThat(opcoes).bodyJson().extractingPath("cartoes[0].faturas[0].mes").isEqualTo(mes);
        assertThat(opcoes).bodyJson().extractingPath("cartoes[0].faturas[0].sugerida").isEqualTo(true);
        assertThat(opcoes).bodyJson().extractingPath("cartoes[0].faturas[0].situacao").isEqualTo("FECHADA");
        assertThat(opcoes).bodyJson().extractingPath("cartoes[0].faturas[*].sugerida").asArray().containsOnlyOnce(true);
        assertThat(opcoes).bodyJson().extractingPath("cartoes[0].faturas[*].mes").asArray().contains(mesAtras(3));
        assertThat(opcoes).bodyJson().extractingPath("cartoes[1].faturas[*].sugerida").asArray().doesNotContain(true);
    }

    @Test
    void converterTiraACategoriaPagaAFaturaEDesfazerVoltaADespesa() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long cartao = cartaoQueFecha5(usuario, "Cartão Horizonte", conta);
        novaCompra(usuario, cartao, diaUmAtras(2), "Livraria", "265.20", 1);
        String mes = mesAtras(2);
        String data = hoje().minusDays(1).toString();
        long despesa = importado(usuario, conta, data, "-265.20", "PAG FATURA CARTAO HORIZONTE");
        jdbc.update("update lancamentos set categoria_id = ? where id = ?", idDaCategoria(usuario, "Compras"), despesa);
        String chave = jdbc.queryForObject("select chave_importacao from lancamentos where id = ?", String.class, despesa);

        MvcTestResult convertido = api.post(usuario, "/api/lancamentos/{id}/converter-em-pagamento", conversao(cartao, mes), despesa);
        assertThat(convertido).hasStatus(201).hasHeader("Location", "/api/pagamentos/" + despesa);
        assertThat(texto(convertido)).contains("\"origem\":\"IMPORTADO\"").contains("\"descricaoBanco\":\"PAG FATURA CARTAO HORIZONTE\"")
                .contains("\"valor\":265.20").contains("\"faturaMes\":\"" + mes + "\"");
        assertThat(lancamentoNoBanco(despesa)).isEqualTo("PAGAMENTO_FATURA -265.20 " + data + " PAG FATURA CARTAO HORIZONTE sem " + mes);
        assertThat(jdbc.queryForObject("select chave_importacao from lancamentos where id = ?", String.class, despesa)).isEqualTo(chave);
        assertThat(fatura(usuario, cartao, mes)).bodyJson().extractingPath("situacao").isEqualTo("PAGA");
        assertThat(fatura(usuario, cartao, mes)).bodyJson().extractingPath("pagamentos[0].origem").isEqualTo("IMPORTADO");

        // No importado, só a observação muda; ele não se exclui: se desfaz.
        assertThat(api.put(usuario, "/api/pagamentos/{id}", pagamento(conta, data, "200"), despesa)).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Conta, data e valor de um pagamento importado não mudam.");
        assertThat(api.put(usuario, "/api/pagamentos/{id}", "{\"contaId\":" + conta + ",\"data\":\"" + data
                + "\",\"valor\":265.20,\"observacao\":\"débito automático\"}", despesa)).hasStatus(200);
        assertThat(api.delete(usuario, "/api/pagamentos/{id}", despesa)).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Este pagamento veio do extrato: desfaça a conversão para ele voltar a ser despesa.");

        MvcTestResult desfeito = api.acao(usuario, "/api/pagamentos/{id}/desfazer", despesa);
        assertThat(desfeito).hasStatus(200).bodyJson().extractingPath("tipo").isEqualTo("DESPESA");
        assertThat(desfeito).bodyJson().extractingPath("observacao").isEqualTo("débito automático");
        assertThat(lancamentoNoBanco(despesa)).isEqualTo("DESPESA -265.20 " + data + " PAG FATURA CARTAO HORIZONTE sem solto");
        assertThat(fatura(usuario, cartao, mes)).bodyJson().extractingPath("situacao").isEqualTo("FECHADA");

        long manual = pagarHoje(usuario, cartao, mes, conta, "1");
        assertThat(api.acao(usuario, "/api/pagamentos/{id}/desfazer", manual)).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Este pagamento foi lançado à mão: para tirá-lo, exclua o pagamento.");
    }

    @Test
    void soADespesaImportadaDeContaAtivaComDataAteHojeViraPagamentoDeUmaFaturaQueComecou() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        UsuarioDeTeste outro = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long investimento = novaConta(usuario, "Corretora", "INVESTIMENTO");
        long cartao = cartaoQueFecha5(usuario, "Cartão Horizonte", conta);
        long cartaoDoOutro = cartaoQueFecha5(outro, "Cartão do outro", novaConta(outro, "Conta do outro"));
        String mes = mesAtras(2);
        String ontem = hoje().minusDays(1).toString();
        long manual = api.idCriado(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "10", ontem, "Manual", conta, null)));
        long receita = importado(usuario, conta, ontem, "10", "Estorno do banco");
        long futura = importado(usuario, conta, hoje().plusDays(3).toString(), "-10", "Agendado");
        long naCorretora = importado(usuario, investimento, ontem, "-10", "Taxa");
        long despesa = importado(usuario, conta, ontem, "-10", "PAG FATURA");

        assertThat(api.post(usuario, "/api/lancamentos/{id}/converter-em-pagamento", conversao(cartao, mes), manual)).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Só um lançamento importado vira pagamento de fatura.");
        assertThat(api.post(usuario, "/api/lancamentos/{id}/converter-em-pagamento", conversao(cartao, mes), receita)).hasStatus(422)
                .bodyJson().extractingPath("detail").isEqualTo("Só uma despesa importada vira pagamento de fatura.");
        assertThat(api.post(usuario, "/api/lancamentos/{id}/converter-em-pagamento", conversao(cartao, mes), futura)).hasStatus(422)
                .bodyJson().extractingPath("detail").isEqualTo("Este lançamento tem data futura: ele só vira pagamento de fatura quando a data chegar.");
        assertThat(api.post(usuario, "/api/lancamentos/{id}/converter-em-pagamento", conversao(cartao, mes), naCorretora)).hasStatus(422)
                .bodyJson().extractingPath("detail").isEqualTo("Pagamento de fatura sai de uma conta corrente, poupança ou carteira.");
        String futuraDoCartao = java.time.YearMonth.from(hoje()).plusMonths(3).toString();
        MvcTestResult naFutura = api.post(usuario, "/api/lancamentos/{id}/converter-em-pagamento", conversao(cartao, futuraDoCartao), despesa);
        assertThat(naFutura).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Escolha uma fatura deste cartão que já fechou ou está aberta.");
        assertThat(naFutura).bodyJson().extractingPath("campo").isEqualTo("mes");
        MvcTestResult doOutro = api.post(usuario, "/api/lancamentos/{id}/converter-em-pagamento", conversao(cartaoDoOutro, mes), despesa);
        assertThat(doOutro).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Cartão não encontrado.");
        assertThat(doOutro).bodyJson().extractingPath("campo").isEqualTo("cartaoId");

        long pagamento = api.json(api.post(usuario, "/api/lancamentos/{id}/converter-em-pagamento", conversao(cartao, mes), despesa))
                .get("id").asLong();
        assertThat(api.post(usuario, "/api/lancamentos/{id}/converter-em-pagamento", conversao(cartao, mes), pagamento)).hasStatus(422)
                .bodyJson().extractingPath("detail").isEqualTo("Este lançamento já é um pagamento de fatura.");
        assertThat(api.acao(usuario, "/api/contas/{id}/arquivar", conta)).hasStatus(200);
        assertThat(api.acao(usuario, "/api/pagamentos/{id}/desfazer", pagamento)).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Banco Horizonte está arquivada. Reative a conta para desfazer este pagamento.");
    }

    @Test
    void oPagamentoNaoViraTransferenciaNemServeDePar() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long poupanca = novaConta(usuario, "Poupança", "POUPANCA");
        long cartao = cartaoQueFecha5(usuario, "Cartão Horizonte", conta);
        String ontem = hoje().minusDays(1).toString();
        long pagamento = importado(usuario, conta, ontem, "-500", "PAG FATURA");
        assertThat(api.post(usuario, "/api/lancamentos/{id}/converter-em-pagamento", conversao(cartao, mesAtras(2)), pagamento)).hasStatus(201);
        long entrada = importado(usuario, poupanca, ontem, "500", "TED RECEBIDA");

        assertThat(api.get(usuario, "/api/lancamentos/{id}/pares", pagamento)).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Este lançamento é um pagamento de fatura: desfaça o pagamento antes de convertê-lo em transferência.");
        assertThat(api.get(usuario, "/api/lancamentos/{id}/pares", entrada)).hasStatus(200).bodyJson().extractingPath("pares").asArray().isEmpty();
        assertThat(api.post(usuario, "/api/lancamentos/{id}/converter", "{\"parId\":" + pagamento + ",\"contaId\":null,\"descricao\":null}",
                entrada)).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("PAR_INVALIDO");
    }
}
