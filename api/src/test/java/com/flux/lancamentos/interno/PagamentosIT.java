package com.flux.lancamentos.interno;

import java.time.format.DateTimeFormatter;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.flux.suporte.ApiDeTeste.assertCampo;
import static com.flux.suporte.ApiDeTeste.conta;
import static com.flux.suporte.ApiDeTeste.lancamento;
import static com.flux.suporte.ApiDeTeste.texto;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decisões 30 a 34, 36 e 38 da Fatia 05 (ADR 0018): o pagamento é um lançamento da conta ligado à fatura, fora de
 * receitas e despesas e dentro do saldo; a situação paga, o pago a mais, a paga fora do Flux e o limite disponível.
 */
class PagamentosIT extends PagamentosBase {

    private static final DateTimeFormatter DATA_CURTA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Test
    void pagarAFaturaFechadaDeixaAPagaTiraDoSaldoDaContaEDevolveOLimite() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long cartao = cartaoQueFecha5(usuario, "Cartão Horizonte", conta);
        novaCompra(usuario, cartao, diaUmAtras(2), "Livraria", "265.20", 1);
        String mes = mesAtras(2);

        MvcTestResult antes = fatura(usuario, cartao, mes);
        assertThat(antes).bodyJson().extractingPath("situacao").isEqualTo("FECHADA");
        assertThat(texto(antes)).contains("\"pago\":0.00").contains("\"quantidadePagamentos\":0").contains("\"pendente\":265.20")
                .contains("\"pagoAMais\":0.00").contains("\"pagaFora\":false").contains("\"limite\":1000.00")
                .contains("\"disponivel\":734.80").contains("\"contaPagamentoId\":" + conta);

        MvcTestResult pago = pagar(usuario, cartao, mes, conta, hoje().toString(), "265.20");
        assertThat(pago).hasStatus(201).hasHeader("Location", "/api/pagamentos/" + api.json(pago).get("id").asLong());
        assertThat(texto(pago)).contains("\"valor\":265.20").contains("\"faturaMes\":\"" + mes + "\"").contains("\"contaNome\":\"Banco Horizonte\"")
                .contains("\"cartaoNome\":\"Cartão Horizonte\"").contains("\"origem\":\"MANUAL\"").contains("\"somenteLeitura\":false");
        long pagamento = api.json(pago).get("id").asLong();
        assertThat(lancamentoNoBanco(pagamento)).isEqualTo("PAGAMENTO_FATURA -265.20 " + hoje() + " Pagamento da fatura de "
                + PagamentoService.nomeDoMes(java.time.YearMonth.parse(mes)) + " sem " + mes);

        MvcTestResult depois = fatura(usuario, cartao, mes);
        assertThat(depois).bodyJson().extractingPath("situacao").isEqualTo("PAGA");
        assertThat(texto(depois)).contains("\"pago\":265.20").contains("\"quantidadePagamentos\":1").contains("\"pendente\":0.00")
                .contains("\"disponivel\":1000.00");
        assertThat(depois).bodyJson().extractingPath("pagamentos[0].id").isEqualTo((int) pagamento);
        assertThat(depois).bodyJson().extractingPath("pagamentos[0].contaNome").isEqualTo("Banco Horizonte");
        assertThat(depois).bodyJson().extractingPath("pagamentos[0].origem").isEqualTo("MANUAL");
        assertThat(texto(api.get(usuario, "/api/contas"))).contains("\"saldoAtual\":-265.20");
        assertThat(texto(api.get(usuario, "/api/cartoes"))).contains("\"pendente\":0.00").contains("\"disponivel\":1000.00")
                .contains("\"usoDoLimite\":0");
    }

    @Test
    void parcialAntecipadoEPagoAMais() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long cartao = cartaoQueFecha5(usuario, "Cartão Horizonte", conta);
        novaCompra(usuario, cartao, diaUmAtras(3), "Mercado", "312.00", 1);
        novaCompra(usuario, cartao, diaUmAtras(2), "Farmácia", "410.00", 1);
        novaCompra(usuario, cartao, hoje().toString(), "Posto", "100.00", 1);
        String aberta = api.json(api.get(usuario, "/api/cartoes")).get("ativos").get(0).get("faturaAberta").get("mes").asString();

        // Parcial: continua fechada, com o pendente.
        pagarHoje(usuario, cartao, mesAtras(3), conta, "200");
        MvcTestResult parcial = fatura(usuario, cartao, mesAtras(3));
        assertThat(parcial).bodyJson().extractingPath("situacao").isEqualTo("FECHADA");
        assertThat(texto(parcial)).contains("\"pago\":200.00").contains("\"pendente\":112.00").contains("\"pagoAMais\":0.00");

        // Pago a mais (decisão 31): paga, com o que passou do total, e pendente zero no disponível.
        pagarHoje(usuario, cartao, mesAtras(2), conta, "460");
        MvcTestResult aMais = fatura(usuario, cartao, mesAtras(2));
        assertThat(aMais).bodyJson().extractingPath("situacao").isEqualTo("PAGA");
        assertThat(texto(aMais)).contains("\"pago\":460.00").contains("\"pendente\":0.00").contains("\"pagoAMais\":50.00");

        // Antecipado (decisão 34): a aberta continua aberta, com o pago.
        pagarHoje(usuario, cartao, aberta, conta, "300");
        MvcTestResult antecipada = fatura(usuario, cartao, aberta);
        assertThat(antecipada).bodyJson().extractingPath("situacao").isEqualTo("ABERTA");
        assertThat(texto(antecipada)).contains("\"pago\":300.00").contains("\"pendente\":0.00").contains("\"pagoAMais\":200.00");

        // O disponível: 1.000,00 − 112,00 (o resto da parcial); as outras não têm pendente.
        assertThat(texto(antecipada)).contains("\"disponivel\":888.00");

        // A fechada sem pagamento, só com crédito, não é paga: o pendente é zero, e o estorno não trava.
        long devolucao = api.idCriado(api.post(usuario, "/api/estornos", estorno(cartao, diaUmAtras(4), "Estorno", "30", null)));
        MvcTestResult soCredito = fatura(usuario, cartao, mesAtras(4));
        assertThat(soCredito).bodyJson().extractingPath("situacao").isEqualTo("FECHADA");
        assertThat(texto(soCredito)).contains("\"pendente\":0.00").contains("\"pago\":0.00").contains("\"pagoAMais\":0.00");
        assertThat(api.delete(usuario, "/api/estornos/{id}", devolucao)).hasStatus(204);
    }

    @Test
    void validaOPagamentoComAsMensagensDaTela() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long cartao = cartaoQueFecha5(usuario, "Cartão Horizonte", conta);
        novaCompra(usuario, cartao, diaUmAtras(2), "Livraria", "100", 1);
        String mes = mesAtras(2);
        String amanha = hoje().plusDays(1).toString();
        String inicioRecente = hoje().minusDays(10).toString();
        long recente = api.idCriado(api.post(usuario, "/api/contas", conta("Conta recente", "CORRENTE", "0", inicioRecente, "null")));
        long investimento = novaConta(usuario, "Corretora", "INVESTIMENTO");
        long arquivada = novaConta(usuario, "Poupança antiga", "POUPANCA");
        assertThat(api.acao(usuario, "/api/contas/{id}/arquivar", arquivada)).hasStatus(200);

        assertCampo(api.post(usuario, "/api/cartoes/{id}/faturas/{mes}/pagamentos", "{\"contaId\":" + conta + ",\"data\":\"" + hoje()
                + "\",\"valor\":null,\"observacao\":null}", cartao, mes), "valor", "Informe o valor.");
        assertCampo(pagar(usuario, cartao, mes, conta, hoje().toString(), "0"), "valor", "O valor precisa ser maior que zero.");
        assertCampo(pagar(usuario, cartao, mes, conta, amanha, "10"), "data", "A data pode ir até hoje, " + hoje().format(DATA_CURTA) + ".");
        assertCampo(pagar(usuario, cartao, mes, recente, hoje().minusDays(11).toString(), "10"), "data", "A data precisa ser "
                + hoje().minusDays(10).format(DATA_CURTA) + " ou depois, a data do saldo inicial de Conta recente.");
        assertThat(pagar(usuario, cartao, mes, investimento, hoje().toString(), "10")).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Escolha uma conta corrente, poupança ou carteira.");
        assertThat(pagar(usuario, cartao, mes, arquivada, hoje().toString(), "10")).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Poupança antiga está arquivada. Escolha uma conta ativa.");
        assertThat(pagar(usuario, cartao, mes, 999_999_999L, hoje().toString(), "10")).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Conta não encontrada.");
        String futura = java.time.YearMonth.from(hoje()).plusMonths(3).toString();
        MvcTestResult naFutura = pagar(usuario, cartao, futura, conta, hoje().toString(), "10");
        assertThat(naFutura).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("FATURA_FUTURA");
        assertThat(naFutura).bodyJson().extractingPath("detail").asString().endsWith(" ainda não começou: pague a aberta ou uma que já fechou.");
        assertCampo(pagar(usuario, cartao, "2026-13", conta, hoje().toString(), "10"), "mes", "Mês inválido.");
        assertThat(pagar(usuario, 999_999_999L, mes, conta, hoje().toString(), "10")).hasStatus(404).bodyJson()
                .extractingPath("detail").isEqualTo("Cartão não encontrado.");
        assertThat(api.acao(usuario, "/api/cartoes/{id}/arquivar", cartao)).hasStatus(200);
        assertThat(pagar(usuario, cartao, mes, conta, hoje().toString(), "10")).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Cartão Horizonte está arquivado. Reative o cartão para pagar a fatura.");
        assertThat(contar("lancamentos", usuario)).isEqualTo(1);
    }

    @Test
    void editarEExcluirOPagamentoQueSoSeAlcancaPorPagamentos() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long carteira = novaConta(usuario, "Carteira", "CARTEIRA");
        long cartao = cartaoQueFecha5(usuario, "Cartão Horizonte", conta);
        novaCompra(usuario, cartao, diaUmAtras(2), "Livraria", "300", 1);
        String mes = mesAtras(2);
        long pagamento = pagarHoje(usuario, cartao, mes, conta, "300");

        MvcTestResult editado = api.put(usuario, "/api/pagamentos/{id}", "{\"contaId\":" + carteira + ",\"data\":\"" + hoje().minusDays(1)
                + "\",\"valor\":120.50,\"observacao\":\"  parte em dinheiro \"}", pagamento);
        assertThat(editado).hasStatus(200);
        assertThat(texto(editado)).contains("\"valor\":120.50").contains("\"contaNome\":\"Carteira\"").contains("\"observacao\":\"parte em dinheiro\"");
        assertThat(texto(fatura(usuario, cartao, mes))).contains("\"pendente\":179.50").contains("\"situacao\":\"FECHADA\"");
        assertThat(api.get(usuario, "/api/pagamentos/{id}", pagamento)).hasStatus(200).bodyJson().extractingPath("data")
                .isEqualTo(hoje().minusDays(1).toString());

        // O pagamento não é um lançamento comum: /api/lancamentos/{id} dá 404, e o tipo não se lança por lá.
        assertThat(api.get(usuario, "/api/lancamentos/{id}", pagamento)).hasStatus(404);
        assertThat(api.put(usuario, "/api/lancamentos/{id}", lancamento("DESPESA", "1", hoje().toString(), "X", conta, null), pagamento))
                .hasStatus(404);
        assertThat(api.delete(usuario, "/api/lancamentos/{id}", pagamento)).hasStatus(404);
        assertCampo(api.post(usuario, "/api/lancamentos", lancamento("PAGAMENTO_FATURA", "1", hoje().toString(), "X", conta, null)), "tipo",
                "Escolha receita ou despesa.");

        assertThat(api.delete(usuario, "/api/pagamentos/{id}", pagamento)).hasStatus(204);
        assertThat(api.get(usuario, "/api/pagamentos/{id}", pagamento)).hasStatus(404).bodyJson().extractingPath("detail")
                .isEqualTo("Pagamento não encontrado.");
        assertThat(texto(fatura(usuario, cartao, mes))).contains("\"pendente\":300.00").contains("\"quantidadePagamentos\":0");
        assertThat(api.acao(usuario, "/api/pagamentos/{id}/desfazer", pagamento)).hasStatus(404);
    }

    @Test
    void pagaForaDoFluxSoNaFechadaZeraOPendenteEDesmarca() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long cartao = cartaoQueFecha5(usuario, "Cartão Lua", conta);
        novaCompra(usuario, cartao, diaUmAtras(2), "Mercado", "312", 1);
        String mes = mesAtras(2);
        pagarHoje(usuario, cartao, mes, conta, "200");

        MvcTestResult marcada = api.put(usuario, "/api/cartoes/{id}/faturas/{mes}/paga-fora", "", cartao, mes);
        assertThat(marcada).hasStatus(200).bodyJson().extractingPath("situacao").isEqualTo("PAGA");
        assertThat(texto(marcada)).contains("\"pagaFora\":true").contains("\"pendente\":0.00").contains("\"pago\":200.00")
                .contains("\"disponivel\":1000.00");
        assertThat(api.put(usuario, "/api/cartoes/{id}/faturas/{mes}/paga-fora", "", cartao, mes)).hasStatus(200);

        MvcTestResult desmarcada = api.delete(usuario, "/api/cartoes/{id}/faturas/{mes}/paga-fora", cartao, mes);
        assertThat(desmarcada).hasStatus(200).bodyJson().extractingPath("situacao").isEqualTo("FECHADA");
        assertThat(texto(desmarcada)).contains("\"pagaFora\":false").contains("\"pendente\":112.00");

        String aberta = api.json(api.get(usuario, "/api/cartoes")).get("ativos").get(0).get("faturaAberta").get("mes").asString();
        MvcTestResult naAberta = api.put(usuario, "/api/cartoes/{id}/faturas/{mes}/paga-fora", "", cartao, aberta);
        assertThat(naAberta).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Só uma fatura que já fechou pode ser marcada como paga fora do Flux.");
        assertThat(api.acao(usuario, "/api/cartoes/{id}/arquivar", cartao)).hasStatus(200);
        assertThat(api.put(usuario, "/api/cartoes/{id}/faturas/{mes}/paga-fora", "", cartao, mes)).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Cartão Lua está arquivado. Reative o cartão para marcar a fatura como paga.");
    }

    @Test
    void oDisponivelContaAsFaturasFuturasEAPreviaDizODepoisDaCompra() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long cartao = cartaoQueFecha5(usuario, "Cartão Horizonte", conta);
        novaCompra(usuario, cartao, hoje().toString(), "Notebook", "900", 3);

        MvcTestResult cartoes = api.get(usuario, "/api/cartoes");
        assertThat(texto(cartoes)).contains("\"pendente\":900.00").contains("\"disponivel\":100.00").contains("\"usoDoLimite\":90");
        MvcTestResult previa = api.post(usuario, "/api/compras/previa", compra(cartao, hoje().toString(), "x", "300", 2, 1, null));
        assertThat(texto(previa)).contains("\"disponivelDepois\":-200.00");

        // Acima do limite: o disponível fica negativo, e nada é recusado.
        novaCompra(usuario, cartao, hoje().toString(), "Geladeira", "350", 1);
        assertThat(texto(api.get(usuario, "/api/cartoes"))).contains("\"pendente\":1250.00").contains("\"disponivel\":-250.00")
                .contains("\"usoDoLimite\":100");
    }

    @Test
    void naListaDoMesOPagamentoFicaForaDosTotaisEDosFiltrosDeCategoriaEEntraNoSaldoDoDia() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long cartao = cartaoQueFecha5(usuario, "Cartão Horizonte", conta);
        novaCompra(usuario, cartao, diaUmAtras(2), "Livraria", "265.20", 1);
        long pagamento = pagarHoje(usuario, cartao, mesAtras(2), conta, "265.20");
        api.idCriado(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "10", hoje().toString(), "Padaria", conta, null)));
        String mes = hoje().toString().substring(0, 7);

        MvcTestResult lista = api.get(usuario, "/api/lancamentos?mes={mes}", mes);
        assertThat(texto(lista)).contains("\"saidas\":-10.00").contains("\"quantidadeSaidas\":1").contains("\"quantidadeLancamentos\":2");
        assertThat(lista).bodyJson().extractingPath("dias[0].itens[?(@.id == " + pagamento + ")].tipo").asArray()
                .containsExactly("PAGAMENTO_FATURA");
        assertThat(lista).bodyJson().extractingPath("dias[0].itens[?(@.id == " + pagamento + ")].faturaPagaMes").asArray()
                .containsExactly(mesAtras(2));
        assertThat(lista).bodyJson().extractingPath("dias[0].itens[?(@.id == " + pagamento + ")].cartaoNome").asArray()
                .containsExactly("Cartão Horizonte");
        assertThat(api.get(usuario, "/api/lancamentos?mes={mes}&tipo=pagamento", mes)).bodyJson().extractingPath("dias[*].itens[*].id")
                .asArray().containsExactly((int) pagamento);
        assertThat(api.get(usuario, "/api/lancamentos?mes={mes}&categoria=sem", mes)).bodyJson().extractingPath("dias[*].itens[*].descricao")
                .asArray().containsExactly("Padaria");
        assertThat(texto(api.get(usuario, "/api/lancamentos?mes={mes}&conta={conta}", mes, conta))).contains("\"saldoNoFimDoDia\":-275.20");
        assertThat(api.get(usuario, "/api/lancamentos?mes={mes}&cartao={cartao}", mes, cartao)).bodyJson()
                .extractingPath("dias[*].itens[*].id").asArray().doesNotContain((int) pagamento);
        assertThat(api.get(usuario, "/api/lancamentos/ultimos")).bodyJson().extractingPath("[*].tipo").asArray().contains("PAGAMENTO_FATURA");
        // As regras não olham o pagamento: uma regra de despesa que casa com a descrição dele não o pega.
        MvcTestResult previaDaRegra = api.post(usuario, "/api/regras/previa", "{\"condicao\":\"CONTEM\",\"texto\":\"pagamento da fatura\","
                + "\"categoriaId\":" + idDaCategoria(usuario, "Compras") + ",\"contaId\":null}");
        assertThat(previaDaRegra).hasStatus(200).bodyJson().extractingPath("casam").isEqualTo(0);
    }

    @Test
    void comAContaOuOCartaoArquivadoOPagamentoFicaSoParaLeitura() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long outra = novaConta(usuario, "Carteira", "CARTEIRA");
        long cartao = cartaoQueFecha5(usuario, "Cartão Horizonte", conta);
        novaCompra(usuario, cartao, diaUmAtras(2), "Livraria", "100", 1);
        long pagamento = api.idCriado(pagar(usuario, cartao, mesAtras(2), outra, hoje().toString(), "100"));

        assertThat(api.acao(usuario, "/api/contas/{id}/arquivar", outra)).hasStatus(200);
        assertThat(api.get(usuario, "/api/pagamentos/{id}", pagamento)).bodyJson().extractingPath("somenteLeitura").isEqualTo(true);
        assertThat(api.put(usuario, "/api/pagamentos/{id}", pagamento(outra, hoje().toString(), "90"), pagamento)).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Carteira está arquivada. Reative a conta para editar ou excluir este pagamento.");
        assertThat(api.delete(usuario, "/api/pagamentos/{id}", pagamento)).hasStatus(422);
        assertThat(api.acao(usuario, "/api/contas/{id}/reativar", outra)).hasStatus(200);

        assertThat(api.acao(usuario, "/api/cartoes/{id}/arquivar", cartao)).hasStatus(200);
        assertThat(api.get(usuario, "/api/pagamentos/{id}", pagamento)).bodyJson().extractingPath("cartaoArquivado").isEqualTo(true);
        assertThat(api.delete(usuario, "/api/pagamentos/{id}", pagamento)).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Cartão Horizonte está arquivado. Reative o cartão para editar ou excluir este pagamento.");
        assertThat(api.get(usuario, "/api/lancamentos/ultimos")).bodyJson().extractingPath("[0].somenteLeitura").isEqualTo(true);
    }
}
